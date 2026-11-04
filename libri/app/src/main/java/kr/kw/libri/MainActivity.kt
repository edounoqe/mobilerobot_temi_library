package kr.kw.libri

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import kr.kw.libri.ui.theme.LibriTheme

/**
 * Libri 참참참 — 앱을 켜면 바로 참참참 대기실(ChamScreen)을 띄운다.
 * temi 에서는 로봇 연결 시 상단바에 앱을 등록한다 (폰·AVD 에서는 연결 콜백이 오지 않을 뿐 게임은 동작).
 */
class MainActivity : ComponentActivity(), OnRobotReadyListener {

    private val robot: Robot by lazy { Robot.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibriTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChamScreen(onExit = { finish() }, modifier = Modifier.padding(innerPadding))
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
