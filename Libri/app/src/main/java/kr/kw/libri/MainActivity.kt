package kr.kw.libri

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Log
import kr.kw.libri.backend.FeedbackScreen
import kr.kw.libri.backend.LibriBackend
import kr.kw.libri.backend.RobotIdentity
import kr.kw.libri.backend.RobotPresence
import kr.kw.libri.backend.firebaseRobotUid
import kr.kw.libri.backend.approvedRobotId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission
import kotlinx.coroutines.delay
import kr.kw.libri.chamchamcham.ChamScreen
import kr.kw.libri.common.paper
import kr.kw.libri.omok.OmokScreen
import kr.kw.libri.photobooth.PhotoBoothScreen
import kr.kw.libri.stretch.StretchScreen
import kr.kw.libri.ui.theme.LibriTheme

/** 앱 화면 — 얼굴 화면을 누르면 홈 메뉴, 홈 메뉴에서 각 기능으로 이동한다 */
enum class Screen { FACE, HOME, PHOTO, CHAM, OMOK, STRETCH, FEEDBACK, ROBOT_SETTINGS }

/**
 * 이만큼 아무도 화면을 만지지 않으면 사람이 떠난 것으로 보고 얼굴 화면으로 돌아간다.
 * 게임은 화면을 안 만지고 하는 시간(수비·스트레칭·수 읽기)이 있어 더 넉넉히 기다린다.
 */
private fun idleToFaceMs(screen: Screen): Long? = when (screen) {
    Screen.FACE, Screen.ROBOT_SETTINGS -> null
    Screen.HOME, Screen.PHOTO, Screen.FEEDBACK -> 60_000L
    Screen.CHAM, Screen.OMOK, Screen.STRETCH -> 180_000L
}

/** temi 사람 감지 — 이 거리(m) 안에 사람이 들어오면 '다가왔다' (0.5~2.0) */
private const val DETECT_RANGE_M = 1.5f
private const val REQ_SETTINGS = 7001

/**
 * Libri — 앱을 켜면 얼굴 화면(두 눈)을 띄우고, 화면을 누르면 홈 화면(기능 선택)으로 넘어간다.
 * temi 에서는 로봇 연결 시 상단바에 앱을 등록하고 사람 감지 모드를 켠다 → 사람이 가까이 오면 얼굴이 반긴다
 * (폰·AVD 에서는 로봇 콜백이 오지 않을 뿐 그대로 동작한다).
 */
class MainActivity : ComponentActivity(),
    OnRobotReadyListener, OnDetectionStateChangedListener, OnRequestPermissionResultListener {

    private val robot: Robot by lazy { Robot.getInstance() }

    /** temi 가 앞에 사람이 있다고 알려 온 상태 */
    private var personNear by mutableStateOf(false)

    /** Settings 권한을 거절해도 돌아올 때마다 다시 묻지 않도록 한 번만 */
    private var askedSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 모든 화면이 밝은 책 종이 바탕이므로 상태바·내비게이션바도 투명 바탕 + 어두운 아이콘으로 맞춘다
        val paperBars = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = paperBars, navigationBarStyle = paperBars)
        setContent {
            LibriTheme {
                // 종이 바탕은 창 전체(상태바 아래까지)에 한 장으로 깐다 — 화면마다 따로 깔면 이음매가 보인다
                Scaffold(modifier = Modifier.fillMaxSize().paper(), containerColor = Color.Transparent) { innerPadding ->
                    var screen by rememberSaveable { mutableStateOf(Screen.FACE) }
                    val context = LocalContext.current.applicationContext
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    var identity by remember { mutableStateOf(RobotIdentity.load(context)) }
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
                        Screen.OMOK -> "omok"
                        Screen.STRETCH -> "stretch"
                        Screen.FEEDBACK -> "feedback"
                        Screen.ROBOT_SETTINGS -> "settings"
                        Screen.HOME, Screen.FACE -> "idle"
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

                    // 화면을 만질 때마다 세는 수 — 한동안 바뀌지 않으면 얼굴 화면으로 돌아간다.
                    // 게임 화면은 사라질 때 스스로 방을 나간다 (각 Screen 의 DisposableEffect)
                    var touches by remember { mutableIntStateOf(0) }
                    LaunchedEffect(screen, touches) {
                        val wait = idleToFaceMs(screen) ?: return@LaunchedEffect
                        delay(wait)
                        screen = Screen.FACE
                    }
                    // 홈에서 '뒤로' — 앱을 닫지 않고 얼굴 화면으로 (temi 에서 앱이 꺼지면 다시 켜기 어렵다)
                    BackHandler(enabled = screen == Screen.HOME) { screen = Screen.FACE }
                    BackHandler(enabled = screen == Screen.FACE) { /* 얼굴 화면이 맨 처음 — 뒤로 가도 앱을 닫지 않는다 */ }
                    BackHandler(enabled = screen == Screen.ROBOT_SETTINGS || screen == Screen.FEEDBACK, onBack = toHome)
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    // 아래 화면보다 먼저 보기만 하고(Initial), 누름을 가로채지는 않는다
                                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) touches++
                                }
                            }
                        },
                    ) {
                        when (screen) {
                            Screen.FACE -> FaceScreen(onWake = toHome, personNear = personNear, modifier = pad)
                            Screen.HOME -> HomeScreen(onSelect = { screen = it }, modifier = pad, onRobotSettings = { screen = Screen.ROBOT_SETTINGS })
                            Screen.PHOTO -> PhotoBoothScreen(onExit = toHome, modifier = pad)
                            Screen.CHAM -> ChamScreen(
                                onExit = { requestFeedback("chamchamcham") }, identity = identity,
                                authorized = authorized, onPairingAvailability = { pairingAvailable = it }, modifier = pad,
                            )
                            Screen.OMOK -> OmokScreen(onExit = toHome, modifier = pad)
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
    }

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
        robot.addOnDetectionStateChangedListener(this)
        robot.addOnRequestPermissionResultListener(this)
    }

    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
        robot.removeOnDetectionStateChangedListener(this)
        robot.removeOnRequestPermissionResultListener(this)
        personNear = false
    }

    override fun onRobotReady(isReady: Boolean) {
        if (!isReady) return
        val info = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
        robot.onStart(info)   // 내 앱을 TEMI 상단바에 등록
        ensureDetectionMode()
    }

    /** 사람 감지 모드 켜기 — temi Settings 권한이 필요하다 (매니페스트에 선언, 첫 실행 때 승인 창) */
    private fun ensureDetectionMode() {
        if (robot.checkSelfPermission(Permission.SETTINGS) != Permission.GRANTED) {
            if (!askedSettings) {
                askedSettings = true
                robot.requestPermissions(listOf(Permission.SETTINGS), REQ_SETTINGS)
            }
            return
        }
        if (!robot.detectionModeOn) robot.setDetectionModeOn(true, DETECT_RANGE_M)
    }

    override fun onRequestPermissionResult(permission: Permission, grantResult: Int, requestCode: Int) {
        if (requestCode == REQ_SETTINGS && grantResult == Permission.GRANTED) {
            robot.setDetectionModeOn(true, DETECT_RANGE_M)
        }
    }

    /** temi 의 사람 감지 — 감지 모드가 켜져 있을 때만 불린다 */
    override fun onDetectionStateChanged(state: Int) {
        personNear = state == OnDetectionStateChangedListener.DETECTED
    }
}
