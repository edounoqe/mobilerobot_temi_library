package kr.kw.libri.photobooth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.kw.libri.common.LibriButton
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.LibriScreen
import kr.kw.libri.common.NoteText
import kr.kw.libri.common.PhotoDoodle
import kr.kw.libri.common.SketchCard
import kr.kw.libri.common.Stamp
import kr.kw.libri.common.TitleText

/**
 * 네컷 사진 — 준비 중 (자리만 잡아둔 화면)
 *  예정: 카메라로 4장 촬영 → 프레임 합성 → Supabase Storage 업로드 → 다운로드 QR 표시.
 *  사진 메타데이터(촬영 시각·만료 시각·다운로드 경로)는 Supabase DB(Postgres)로 관리한다.
 */
@Composable
fun PhotoBoothScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onExit)
    LibriScreen(modifier) {
        SketchCard(
            modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 480.dp),
            radius = 30.dp, shadow = 8.dp,
            padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                PhotoDoodle(Modifier.size(190.dp))
                Stamp("준비 중", Modifier.align(Alignment.BottomEnd).offset(x = 34.dp, y = (-6).dp), fontSize = 24.sp, tilt = -10f)
            }
            TitleText("네컷 사진", size = 40.sp)
            NoteText("아직 그리는 중이에요…", size = 24.sp)
            Text(
                "사진 4장을 찍어 프레임에 담고, QR 코드로 휴대폰에 받아 가는 기능이 곧 들어옵니다.",
                fontSize = 16.sp, color = LibriColors.Pencil, textAlign = TextAlign.Center, lineHeight = 23.sp,
            )
            LibriButton("←  처음으로", onExit, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}
