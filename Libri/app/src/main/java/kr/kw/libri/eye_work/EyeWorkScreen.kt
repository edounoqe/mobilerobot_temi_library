package kr.kw.libri.eye_work

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.kw.libri.common.FaceDetectionView

/**
 * 눈 운동 — 가운데 점을 보며 고개를 지정 방향으로 돌리는 운동.
 *  카메라(FaceDetectionView)로 고개 각도를 재고, 판정은 EyeWorkViewModel 이 한다.
 */
@Composable
fun EyeWorkScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EyeWorkViewModel = viewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF12161B))) {
        // 고개 각도 측정을 위한 카메라 (미리보기는 깔아두되 점에 집중하도록 어둡게)
        FaceDetectionView(Modifier.fillMaxSize(), showInfo = false, onFaces = vm::onFaces)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        // 가운데 점 + 유지 진행 링
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val ringR = 46f
            drawCircle(Color(0xFF00E676), radius = 18f, center = c)
            drawCircle(Color.White.copy(alpha = 0.25f), radius = ringR, center = c, style = Stroke(width = 8f))
            if (st.progress > 0f) {
                drawArc(
                    color = Color(0xFF00E676),
                    startAngle = -90f,
                    sweepAngle = 360f * st.progress,
                    useCenter = false,
                    topLeft = Offset(c.x - ringR, c.y - ringR),
                    size = androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2),
                    style = Stroke(width = 8f),
                )
            }
        }

        // 상단 바
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("👀 눈 운동", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!st.done) {
                Text("${st.step} / ${st.total}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) { Text("나가기") }
        }

        // 안내 문구
        val target = st.target
        val msg = when {
            st.done -> "눈 운동 완료! 👏"
            st.recenter -> "좋아요! 정면으로 돌아오세요"
            target != null -> "가운데 점을 보며\n고개를 ${target.label} ${target.arrow} 으로 돌리세요"
            else -> "준비…"
        }
        Text(
            msg,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )

        // 완료 후 버튼
        if (st.done) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { vm.restart() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("다시 하기") }
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Text("뒤로", color = Color.White)
                }
            }
        }
    }
}
