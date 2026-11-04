package kr.kw.libri.photobooth

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
 * 네컷 사진 — 준비 중 (자리만 잡아둔 화면)
 *  예정: 카메라로 4장 촬영 → 프레임 합성 → Supabase Storage 업로드 → 다운로드 QR 표시.
 *  사진 메타데이터(촬영 시각·만료 시각·다운로드 경로)는 Supabase DB(Postgres)로 관리한다.
 */
@Composable
fun PhotoBoothScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("📸 네컷 사진", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "준비 중입니다 🛠️\n4장 촬영·프레임 합성 후 Supabase 로 올려 QR 로 받는 기능이 들어올 자리예요.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("뒤로") }
    }
}
