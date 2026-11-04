package kr.kw.libri.stretch

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
 * 스트레칭 가이드 — 준비 중 (자리만 잡아둔 화면)
 *  예정: 화면 동작을 따라 하면 카메라가 목 각도로 판정하는 자세교정 스트레칭. common/FaceDetectionView 재사용 예정.
 */
@Composable
fun StretchScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("🤸 스트레칭", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "준비 중입니다 🛠️\n화면 동작을 따라 하면 목 각도로 판정하는 스트레칭 가이드가 들어올 자리예요.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("뒤로") }
    }
}
