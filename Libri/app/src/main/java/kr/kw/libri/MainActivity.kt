package kr.kw.libri

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import kr.kw.libri.chamchamcham.ChamScreen
import kr.kw.libri.eye_work.EyeWorkScreen
import kr.kw.libri.photobooth.PhotoBoothScreen
import kr.kw.libri.stretch.StretchScreen
import kr.kw.libri.backend.FeedbackScreen
import kr.kw.libri.backend.LibriBackend
import kr.kw.libri.backend.RobotIdentity
import kr.kw.libri.backend.RobotPresence
import kr.kw.libri.backend.firebaseRobotUid
import kr.kw.libri.backend.approvedRobotId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kr.kw.libri.ui.theme.LibriTheme

/** 앱 화면 — 홈 메뉴에서 각 기능으로 이동한다 */
enum class Screen { HOME, PHOTO, CHAM, EYE, STRETCH, FEEDBACK, ROBOT_SETTINGS }

/**
 * Libri — 앱을 켜면 홈 화면(기능 선택)을 띄운다.
 * temi 에서는 로봇 연결 시 상단바에 앱을 등록한다 (폰·AVD 에서는 연결 콜백이 오지 않을 뿐 동작).
 */
class MainActivity : ComponentActivity(), OnRobotReadyListener {

    private val robot: Robot by lazy { Robot.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibriTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val context = LocalContext.current.applicationContext
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    var identity by remember { mutableStateOf(RobotIdentity.load(context)) }
                    var screen by rememberSaveable { mutableStateOf(if (identity == null) Screen.ROBOT_SETTINGS else Screen.HOME) }
                    var feedbackService by rememberSaveable { mutableStateOf("") }
                    var pairingAvailable by remember { mutableStateOf(false) }
                    var presence by remember { mutableStateOf<RobotPresence?>(null) }
                    var uid by remember { mutableStateOf<String?>(null) }
                    var approvedId by remember { mutableStateOf<String?>(null) }
                    var authMessage by remember { mutableStateOf("Firebase 인증을 확인하는 중…") }
                    var authAttempt by remember { mutableStateOf(0) }
                    LaunchedEffect(authAttempt) {
                        approvedId = null
                        authMessage = "Firebase 인증을 확인하는 중…"
                        try {
                            val finished = withTimeoutOrNull(12_000) {
                                val signedInUid = firebaseRobotUid()
                                uid = signedInUid
                                approvedId = approvedRobotId(context, signedInUid)
                                authMessage = if (approvedId != null) "승인된 로봇: ${approvedId}" else "관리자의 Firebase 승인이 필요합니다."
                                true
                            }
                            if (finished == null) authMessage = "Firebase 인증 응답이 지연됐습니다."
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Log.w("LibriAuth", "Firebase robot authorization failed", error)
                            authMessage = "Firebase 인증에 실패했습니다. 연결과 인증 설정을 확인해주세요."
                        }
                    }
                    val authorized = identity?.id?.let { it == approvedId } == true
                    val service = when (screen) {
                        Screen.PHOTO -> "photo"
                        Screen.CHAM -> if (pairingAvailable) "cham_lobby" else "chamchamcham"
                        Screen.EYE -> "eye_work"
                        Screen.STRETCH -> "stretch"
                        Screen.FEEDBACK -> "feedback"
                        Screen.ROBOT_SETTINGS -> "settings"
                        Screen.HOME -> "idle"
                    }
                    val currentService by rememberUpdatedState(service)
                    DisposableEffect(identity, authorized, lifecycle) {
                        val selected = identity.takeIf { authorized }
                        fun startPresence() {
                            if (selected == null || presence != null) return
                            runCatching {
                                LibriBackend(context).observePresence(selected.id, selected.floor) { Log.e("LibriPresence", it) }
                            }.onSuccess { presence = it; it.update(currentService) }
                                .onFailure { Log.e("LibriPresence", "Unable to start presence", it) }
                        }
                        fun stopPresence() { presence?.close(); presence = null }
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_START -> startPresence()
                                Lifecycle.Event.ON_STOP -> stopPresence()
                                else -> Unit
                            }
                        }
                        lifecycle.addObserver(observer)
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startPresence()
                        onDispose { lifecycle.removeObserver(observer); stopPresence() }
                    }
                    LaunchedEffect(service, presence) { presence?.update(service) }
                    val toHome = { screen = Screen.HOME }
                    val requestFeedback = { service: String ->
                        feedbackService = service
                        screen = if (authorized) Screen.FEEDBACK else Screen.HOME
                    }
                    val pad = Modifier.padding(innerPadding)
                    when (screen) {
                        Screen.HOME -> HomeScreen(onSelect = { screen = it }, modifier = pad, onRobotSettings = { screen = Screen.ROBOT_SETTINGS })
                        Screen.PHOTO -> PhotoBoothScreen(onExit = toHome, modifier = pad)
                        Screen.CHAM -> identity?.let {
                            ChamScreen(
                                onExit = { requestFeedback("chamchamcham") },
                                identity = it,
                                authorized = authorized,
                                onPairingAvailability = { available -> pairingAvailable = available },
                                modifier = pad,
                            )
                        }
                        Screen.EYE -> EyeWorkScreen(onExit = { requestFeedback("eye_work") }, modifier = pad)
                        Screen.STRETCH -> StretchScreen(onExit = toHome, modifier = pad)
                        Screen.FEEDBACK -> identity?.let { FeedbackScreen(service = feedbackService, identity = it, authorized = authorized, onExit = toHome, modifier = pad) }
                        Screen.ROBOT_SETTINGS -> Column(pad.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("로봇 운영 위치 설정")
                            Text("관리자가 기기의 설치 층을 선택해주세요. 두 기기는 서로 다른 층을 선택해야 합니다.")
                            identity?.let { Text("현재: ${it.floor}층 · ${it.id}") }
                            Text(authMessage)
                            uid?.let { Text("기기 UID: ${it}") }
                            OutlinedButton(onClick = { authAttempt++ }) { Text("Firebase 승인 다시 확인") }
                            Button(onClick = { identity = RobotIdentity.save(context, "L"); screen = Screen.HOME }, modifier = Modifier.fillMaxWidth()) { Text("L층 로봇 설정") }
                            Button(onClick = { identity = RobotIdentity.save(context, "1"); screen = Screen.HOME }, modifier = Modifier.fillMaxWidth()) { Text("1층 로봇 설정") }
                            if (identity != null) OutlinedButton(onClick = toHome, modifier = Modifier.fillMaxWidth()) { Text("돌아가기") }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
    }

    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
    }

    override fun onRobotReady(isReady: Boolean) {
        if (!isReady) return
        val info = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
        robot.onStart(info)   // 내 앱을 TEMI 상단바에 등록
    }
}
