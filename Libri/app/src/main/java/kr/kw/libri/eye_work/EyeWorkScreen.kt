package kr.kw.libri.eye_work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 눈 운동 — 준비 중 (자리만 잡아둔 화면)
 *  예정: 화면의 점을 고개로 따라가기 + 먼 곳 보기 20초 등 눈 휴식 루틴. common/FaceDetectionView 재사용 예정.
 */
@Composable
fun EyeWorkScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("👀 눈 운동", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "준비 중입니다 🛠️\n점을 따라가며 먼 곳을 보는 눈 휴식 기능이 들어올 자리예요.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("뒤로") }
    }
}
