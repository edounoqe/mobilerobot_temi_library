package kr.kw.libri.photobooth

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.kw.libri.common.HoldIdleTimer
import kr.kw.libri.common.LibriButton
import kr.kw.libri.common.LibriButtonStyle
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.LibriScreen
import kr.kw.libri.common.NoteText
import kr.kw.libri.common.TitleText

/**
 * 홈의 '네컷 사진' — 들어오자마자 네컷 사진 화면들(PhotoEntryActivity → 프레임 선택 → 4컷 촬영 → 합성·QR)을 띄운다.
 * 그 화면들이 모두 닫히면 Libri 홈으로 돌아간다.
 * 네컷 사진 화면이 떠 있는 동안에는 Libri 화면을 아무도 만지지 않으므로 대기모드 타이머를 멈춘다.
 */
@Composable
fun PhotoBoothScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }     // 네컷 사진 화면이 떠 있는지
    var launched by rememberSaveable { mutableStateOf(false) } // 이 방문에서 한 번 띄웠는지 (다시 그려져도 또 띄우지 않게)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        open = false
        onExit()
    }
    HoldIdleTimer(open)
    BackHandler { onExit() }

    val start = {
        open = true
        launcher.launch(Intent(context, PhotoEntryActivity::class.java))
    }
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            start()
        }
    }

    // 네컷 사진 화면이 닫히는 잠깐 사이나, 띄우지 못했을 때 보이는 화면
    LibriScreen(modifier) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TitleText("네컷 사진", size = 48.sp)
            NoteText("광운대학교 중앙도서관 네컷 사진", size = 22.sp, color = LibriColors.Pencil)
            LibriButton("네컷 사진 시작하기", { start() }, Modifier.width(360.dp), style = LibriButtonStyle.COLOR, color = LibriColors.Sky, minHeight = 72.dp, fontSize = 24)
            LibriButton("Libri 홈으로", onExit, Modifier.width(360.dp), style = LibriButtonStyle.FLAT, minHeight = 56.dp, fontSize = 18)
        }
    }
}
