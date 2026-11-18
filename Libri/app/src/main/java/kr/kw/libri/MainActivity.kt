package kr.kw.libri

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import kotlinx.coroutines.delay
import kr.kw.libri.chamchamcham.ChamScreen
import kr.kw.libri.common.paper
import kr.kw.libri.omok.OmokScreen
import kr.kw.libri.photobooth.PhotoBoothScreen
import kr.kw.libri.stretch.StretchScreen
import kr.kw.libri.ui.theme.LibriTheme

/** 앱 화면 — 얼굴 화면을 누르면 홈 메뉴, 홈 메뉴에서 각 기능으로 이동한다 */
enum class Screen { FACE, HOME, PHOTO, CHAM, OMOK, STRETCH }

/** 홈 메뉴에서 이만큼 아무도 화면을 만지지 않으면 다시 얼굴 화면으로 돌아간다 */
private const val IDLE_TO_FACE_MS = 60_000L

/**
 * Libri — 앱을 켜면 얼굴 화면(두 눈)을 띄우고, 화면을 누르면 홈 화면(기능 선택)으로 넘어간다.
 * temi 에서는 로봇 연결 시 상단바에 앱을 등록하고, 사람이 가까이 오면 얼굴이 반긴다
 * (폰·AVD 에서는 로봇 콜백이 오지 않을 뿐 그대로 동작한다).
 */
class MainActivity : ComponentActivity(), OnRobotReadyListener, OnDetectionStateChangedListener {

    private val robot: Robot by lazy { Robot.getInstance() }

    /** temi 가 앞에 사람이 있다고 알려 온 상태 */
    private var personNear by mutableStateOf(false)

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
                    val toHome = { screen = Screen.HOME }
                    val pad = Modifier.padding(innerPadding)

                    // 화면을 만질 때마다 세는 수 — 홈에서 한동안 바뀌지 않으면 얼굴 화면으로 돌아간다
                    var touches by remember { mutableIntStateOf(0) }
                    LaunchedEffect(screen, touches) {
                        if (screen == Screen.HOME) {
                            delay(IDLE_TO_FACE_MS)
                            screen = Screen.FACE
                        }
                    }
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
                            Screen.HOME -> HomeScreen(onSelect = { screen = it }, modifier = pad)
                            Screen.PHOTO -> PhotoBoothScreen(onExit = toHome, modifier = pad)
                            Screen.CHAM -> ChamScreen(onExit = toHome, modifier = pad)
                            Screen.OMOK -> OmokScreen(onExit = toHome, modifier = pad)
                            Screen.STRETCH -> StretchScreen(onExit = toHome, modifier = pad)
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
    }

    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
        robot.removeOnDetectionStateChangedListener(this)
        personNear = false
    }

    override fun onRobotReady(isReady: Boolean) {
        if (!isReady) return
        val info = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
        robot.onStart(info)   // 내 앱을 TEMI 상단바에 등록
    }

    /** temi 의 사람 감지 — 설정에서 감지 모드가 켜져 있을 때만 불린다 */
    override fun onDetectionStateChanged(state: Int) {
        personNear = state == OnDetectionStateChangedListener.DETECTED
    }
}
