package kr.kw.library

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotemi.sdk.Robot
import java.util.Locale

/**
 * 화면. 위에서부터 센서 기반 사람 감지 → 회전·헤드 틸트 → 비전 기반 얼굴 검출 → 음성 인식 순서로 놓여 있다.
 * 로봇 앞에서 움직이고, 버튼을 누르고, 말을 해 보며 값이 어떻게 변하는지 본다.
 */
@Composable
fun DetectionScreen(
    modifier: Modifier = Modifier,
    viewModel: DetectionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val distance by viewModel.distance.collectAsStateWithLifecycle()
    val angle by viewModel.angle.collectAsStateWithLifecycle()
    val modeOn by viewModel.detectionModeOn.collectAsStateWithLifecycle()
    val heard by viewModel.heard.collectAsStateWithLifecycle()
    val tilt by viewModel.tilt.collectAsStateWithLifecycle()

    val robot = remember { Robot.getInstance() }

    // 화면이 보일 때 리스너를 등록하고, 사라질 때 해제한다 (감지 두 개 + 말하기 + 음성 인식 + 권한 결과)
    LifecycleStartEffect(viewModel) {
        robot.addOnDetectionStateChangedListener(viewModel)
        robot.addOnDetectionDataChangedListener(viewModel)
        robot.addTtsListener(viewModel)
        robot.addAsrListener(viewModel)
        robot.addOnRequestPermissionResultListener(viewModel)
        onStopOrDispose {
            robot.removeOnDetectionStateChangedListener(viewModel)
            robot.removeOnDetectionDataChangedListener(viewModel)
            robot.removeTtsListener(viewModel)
            robot.removeAsrListener(viewModel)
            robot.removeOnRequestPermissionResultListener(viewModel)
        }
    }

    // ── 비전 기반 얼굴 검출: 켜면 전체화면 프리뷰, 우측 상단 닫기로 돌아온다 ──
    var showCamera by remember { mutableStateOf(false) }
    if (showCamera) {
        Box(modifier = modifier.fillMaxSize()) {
            FaceDetectionView(modifier = Modifier.fillMaxSize())
            Button(
                onClick = { showCamera = false },
                colors = DarkButton,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).height(48.dp),
            ) { Text("닫기") }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (modeOn) "감지 모드: 켜짐" else "감지 모드: 꺼짐",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!modeOn) {
            Button(onClick = { viewModel.enableDetectionMode() }, colors = DarkButton, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("감지 모드 켜기 — setDetectionModeOn() (Settings 권한 요청)")
            }
        }
        // ── 센서 기반 사람 감지 ──
        SectionTitle("센서 기반 사람 감지")
        ValueLine("감지 상태", state)
        ValueLine("거리", String.format(Locale.KOREA, "%.2f m", distance))
        ValueLine("각도", String.format(Locale.KOREA, "%.2f", angle))   // 정면 0, 왼쪽 +, 오른쪽 −

        // ── 회전 · 헤드 틸트 ──
        SectionTitle("회전 · 헤드 틸트")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { viewModel.turn(30) }, colors = DarkButton, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("왼쪽으로 30° — turnBy(30)") }
            Button(onClick = { viewModel.turn(-30) }, colors = DarkButton, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("오른쪽으로 30° — turnBy(-30)") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { viewModel.tiltBy(10) }, colors = DarkButton, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("고개 올리기 +10° — tiltAngle()") }
            Button(onClick = { viewModel.tiltBy(-10) }, colors = DarkButton, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("고개 내리기 −10° — tiltAngle()") }
        }
        ValueLine("머리 각도", "$tilt°  (아래 −30 ~ 위 +50)")

        // ── 비전 기반 얼굴 검출 (CameraX + ML Kit) ──
        SectionTitle("비전 기반 얼굴 검출")
        Button(onClick = { showCamera = true }, colors = DarkButton, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("카메라 켜기 — ML Kit 얼굴 검출")
        }

        // ── 음성 인식 (STT) ──
        SectionTitle("음성 인식 (STT)")
        Button(onClick = { viewModel.greet() }, colors = DarkButton, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("말 걸기 — speak() → wakeup()")
        }
        if (heard.isNotEmpty()) ValueLine("들은 말", heard)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}

@Composable
private fun ValueLine(label: String, value: String) {
    Text(
        "$label   $value",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 버튼 공통 — 검정 바탕에 흰 글자 */
private val DarkButton: ButtonColors
    @Composable get() = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
