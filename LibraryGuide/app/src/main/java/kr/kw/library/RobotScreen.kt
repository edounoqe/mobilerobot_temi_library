package kr.kw.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotemi.sdk.Robot

/**
 * 화면. ViewModel 의 상태를 읽어서 그리고, 버튼이 눌리면 ViewModel 에 명령을 보낸다.
 * state 는 읽고 있지만 아직 버튼 제어에는 쓰지 않는다 — Button 의 enabled 에 걸어 보자.
 */
@Composable
fun RobotScreen(
    modifier: Modifier = Modifier,
    viewModel: RobotViewModel = viewModel(),
) {
    // ViewModel 의 값을 읽는다 — 값이 바뀌면 화면이 자동으로 다시 그려진다
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()

    val robot = remember { Robot.getInstance() }

    // 로봇 콜백 리스너 — 화면이 보일 때 등록하고, 사라질 때 해제한다 (짝으로)
    LifecycleStartEffect(viewModel) {
        robot.addOnGoToLocationStatusChangedListener(viewModel)
        robot.addTtsListener(viewModel)

        onStopOrDispose {
            robot.removeOnGoToLocationStatusChangedListener(viewModel)
            robot.removeTtsListener(viewModel)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "도서관 안내 로봇",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "$state  ·  $message",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        if (locations.isEmpty()) {
            Text("저장된 위치가 없습니다. 지도 편집기에서 위치를 저장한 뒤 다시 실행하세요.")
        }

        // 위치 이름 하나당 버튼 하나
        locations.forEach { name ->
            Button(
                onClick = { viewModel.goTo(name) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.weight(1f))

        // 비상 정지
        Button(
            onClick = { viewModel.stop() },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("비상 정지", style = MaterialTheme.typography.titleMedium)
        }
    }
}
