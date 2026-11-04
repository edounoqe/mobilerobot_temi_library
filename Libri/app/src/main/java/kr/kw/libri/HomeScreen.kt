package kr.kw.libri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 홈 — 기능 선택 메뉴. 각 버튼이 해당 Screen 으로 이동한다 */
@Composable
fun HomeScreen(onSelect: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("📚 Libri", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("잠깐 쉬어 갈까요? 하고 싶은 걸 골라요.", style = MaterialTheme.typography.bodyLarge)

        MenuButton("📸 네컷 사진", "temi 앞에서 사진을 찍고 QR 로 받아요") { onSelect(Screen.PHOTO) }
        MenuButton("✌️ 참참참", "고개 방향으로 겨루는 게임") { onSelect(Screen.CHAM) }
        MenuButton("👀 눈 운동", "점을 따라가며 눈을 쉬어요") { onSelect(Screen.EYE) }
        MenuButton("🤸 스트레칭", "목·어깨를 가볍게 풀어요") { onSelect(Screen.STRETCH) }
    }
}

@Composable
private fun MenuButton(title: String, desc: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(desc, fontSize = 14.sp)
        }
    }
}
