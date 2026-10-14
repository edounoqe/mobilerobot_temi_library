package kr.kw.library

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import kr.kw.library.ui.theme.DetectionDemoTheme

/**
 * 앱의 시작점. 창을 열어 화면(DetectionScreen)을 띄우고, 로봇과 연결되는 시점을 기다린다.
 * 로봇 명령은 여기서 직접 보내지 않는다 — 그 일은 DetectionViewModel 이 한다.
 */
class MainActivity : ComponentActivity(), OnRobotReadyListener {

    private val robot: Robot by lazy { Robot.getInstance() }
    private val viewModel: DetectionViewModel by viewModels()   // DetectionScreen 의 viewModel() 과 같은 인스턴스

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DetectionDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DetectionScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    /** 화면이 보일 때 '연결됐다' 콜백을 받을 대상으로 자신을 등록한다. */
    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
    }

    /** 화면이 사라질 때 해제한다 — 등록과 해제는 항상 짝으로. */
    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
    }

    /** 로봇과 연결이 맺어지면 호출된다. 이 뒤부터 로봇 API를 호출할 수 있다. */
    override fun onRobotReady(isReady: Boolean) {
        if (!isReady) return
        val info = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
        robot.onStart(info)   // 내 앱을 TEMI 상단바에 등록
        viewModel.ensureDetectionMode()   // 연결된 뒤에 — temi Settings 권한 확인(첫 실행 때 승인 창) → 감지 모드 켜기
    }
}
