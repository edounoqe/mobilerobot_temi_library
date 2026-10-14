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
import kr.kw.library.ui.theme.LibraryDemoTheme

/** 앱의 시작점. 화면(RobotScreen)을 띄우고, 로봇과 연결되는 시점을 기다린다. */
class MainActivity : ComponentActivity(), OnRobotReadyListener {

    private val robot: Robot by lazy { Robot.getInstance() }
    private val viewModel: RobotViewModel by viewModels()   // RobotScreen 과 같은 ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibraryDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RobotScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    /** 화면이 보일 때 연결 콜백 등록 */
    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
    }

    /** 화면이 사라질 때 해제 */
    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
    }

    /** 로봇과 연결되면 호출된다 — 이 뒤부터 로봇 API 를 쓸 수 있다 */
    override fun onRobotReady(isReady: Boolean) {
        if (!isReady) return
        val info = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
        robot.onStart(info)   // 내 앱을 TEMI 상단바에 등록
        viewModel.loadLocations()   // 연결 전에 읽으면 빈 목록이 온다 — 반드시 여기서
    }
}
