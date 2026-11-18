package kr.kw.libri

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import kr.kw.libri.chamchamcham.ChamScreen
import kr.kw.libri.common.paper
import kr.kw.libri.eye_work.EyeWorkScreen
import kr.kw.libri.photobooth.PhotoBoothScreen
import kr.kw.libri.stretch.StretchScreen
import kr.kw.libri.ui.theme.LibriTheme

/** 앱 화면 — 홈 메뉴에서 각 기능으로 이동한다 */
enum class Screen { HOME, PHOTO, CHAM, EYE, STRETCH }

/**
 * Libri — 앱을 켜면 홈 화면(기능 선택)을 띄운다.
 * temi 에서는 로봇 연결 시 상단바에 앱을 등록한다 (폰·AVD 에서는 연결 콜백이 오지 않을 뿐 동작).
 */
class MainActivity : ComponentActivity(), OnRobotReadyListener {

    private val robot: Robot by lazy { Robot.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 모든 화면이 밝은 책 종이 바탕이므로 상태바·내비게이션바도 투명 바탕 + 어두운 아이콘으로 맞춘다
        val paperBars = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = paperBars, navigationBarStyle = paperBars)
        setContent {
            LibriTheme {
                // 종이 바탕은 창 전체(상태바 아래까지)에 한 장으로 깐다 — 화면마다 따로 깔면 이음매가 보인다
                Scaffold(modifier = Modifier.fillMaxSize().paper(), containerColor = Color.Transparent) { innerPadding ->
                    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                    val toHome = { screen = Screen.HOME }
                    val pad = Modifier.padding(innerPadding)
                    when (screen) {
                        Screen.HOME -> HomeScreen(onSelect = { screen = it }, modifier = pad)
                        Screen.PHOTO -> PhotoBoothScreen(onExit = toHome, modifier = pad)
                        Screen.CHAM -> ChamScreen(onExit = toHome, modifier = pad)
                        Screen.EYE -> EyeWorkScreen(onExit = toHome, modifier = pad)
                        Screen.STRETCH -> StretchScreen(onExit = toHome, modifier = pad)
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
