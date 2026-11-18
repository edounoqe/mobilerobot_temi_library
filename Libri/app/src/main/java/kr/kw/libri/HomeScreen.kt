package kr.kw.libri

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.kw.libri.common.BookDoodle
import kr.kw.libri.common.HandDoodle
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.LibriScreen
import kr.kw.libri.common.LibriTag
import kr.kw.libri.common.NoteText
import kr.kw.libri.common.OmokDoodle
import kr.kw.libri.common.PhotoDoodle
import kr.kw.libri.common.SketchSurface
import kr.kw.libri.common.Squiggle
import kr.kw.libri.common.Stamp
import kr.kw.libri.common.StretchDoodle
import kr.kw.libri.common.TitleText
import kr.kw.libri.common.highlighter

/** 홈 메뉴의 기능 하나 */
private class Feature(
    val screen: Screen,
    val title: String,
    val desc: String,
    val accent: Color,
    val tag: String,
    val ready: Boolean = true,
    val art: @Composable (Modifier) -> Unit,
)

private val FEATURES = listOf(
    // 설명은 폰 화면에서도 한 줄에 들어오게 짧게. 아직 준비 중인 기능은 맨 뒤에
    Feature(Screen.CHAM, "참참참", "손끝을 피해 고개를 휙!", LibriColors.Coral, "둘이서 · 혼자서") { HandDoodle(it) },
    Feature(Screen.OMOK, "오목", "다섯 알을 먼저 이어 보세요", LibriColors.Mint, "둘이서 · 랭킹") { OmokDoodle(it) },
    Feature(Screen.STRETCH, "스트레칭", "책장 벽을 몸으로 통과해요", LibriColors.Yellow, "약 1분") { StretchDoodle(it) },
    Feature(Screen.PHOTO, "네컷 사진", "사진을 찍고 QR 로 받아 가요", LibriColors.Sky, "준비 중", ready = false) { PhotoDoodle(it) },
)

/** 홈 — 기능 선택 메뉴. 각 카드가 해당 Screen 으로 이동한다 */
@Composable
fun HomeScreen(onSelect: (Screen) -> Unit, modifier: Modifier = Modifier) {
    LibriScreen(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val big = maxWidth >= 720.dp            // temi 처럼 넓은 화면 — 두 줄로 크게 (멀리서도 누르기 쉽게)
            val viewport = maxHeight
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    // 내용이 화면보다 짧으면 세로 가운데에 놓는다
                    modifier = Modifier
                        .heightIn(min = viewport)
                        .widthIn(max = 1080.dp)
                        .fillMaxWidth()
                        .padding(horizontal = if (big) 36.dp else 20.dp, vertical = if (big) 20.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (big) 18.dp else 14.dp, if (big) Alignment.CenterVertically else Alignment.Top),
                ) {
                    Header(big)
                    for (row in FEATURES.chunked(if (big) 2 else 1)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(if (big) 22.dp else 14.dp), modifier = Modifier.fillMaxWidth()) {
                            for (f in row) FeatureCard(f, big, Modifier.weight(1f)) { onSelect(f.screen) }
                        }
                    }
                    // 책의 쪽 번호
                    Text(
                        "— 1 —", color = LibriColors.Faint, fontSize = 14.sp, fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** 표지 — 책 그림, 제목, 연필로 적은 한 줄 */
@Composable
private fun Header(big: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 18.dp else 12.dp)) {
        BookDoodle(Modifier.size(if (big) 132.dp else 92.dp, if (big) 104.dp else 72.dp))
        Column(Modifier.weight(1f)) {
            TitleText("Libri", size = if (big) 68.sp else 50.sp)
            Squiggle(Modifier.width(if (big) 164.dp else 120.dp).height(12.dp).offset(y = (-6).dp))
        }
        if (big) {
            // 여백의 메모 — 살짝 기울여 적었다
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.graphicsLayer { rotationZ = -2.5f }) {
                NoteText("책 보다 지칠 땐,", size = 28.sp)
                NoteText("잠깐 쉬어 가요!", size = 34.sp, color = LibriColors.Ink, modifier = Modifier.highlighter())
            }
        }
    }
    if (!big) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            NoteText("책 보다 지칠 땐,", size = 22.sp)
            NoteText("잠깐 쉬어 가요!", size = 25.sp, color = LibriColors.Ink, modifier = Modifier.highlighter())
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun FeatureCard(f: Feature, big: Boolean, modifier: Modifier, onClick: () -> Unit) {
    SketchSurface(
        modifier = modifier.heightIn(min = if (big) 216.dp else 128.dp),
        onClick = onClick,
        radius = if (big) 30.dp else 24.dp,
        shadow = if (big) 8.dp else 6.dp,
        seed = f.title.hashCode(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (big) 16.dp else 10.dp),
            modifier = Modifier.padding(start = if (big) 16.dp else 10.dp, end = if (big) 22.dp else 14.dp, top = 10.dp, bottom = 10.dp),
        ) {
            f.art(Modifier.size(if (big) 184.dp else 104.dp).alpha(if (f.ready) 1f else 0.75f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (big) 6.dp else 3.dp)) {
                TitleText(f.title, size = if (big) 44.sp else 30.sp)
                Text(f.desc, fontSize = if (big) 19.sp else 15.sp, color = LibriColors.Pencil, lineHeight = if (big) 26.sp else 20.sp)
                Spacer(Modifier.height(if (big) 4.dp else 1.dp))
                // 꼬리표와, 오른쪽 아래 구석의 '들어가기' 화살표
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (f.ready) {
                        LibriTag(f.tag, f.accent)
                    } else {
                        Stamp(f.tag, fontSize = if (big) 20.sp else 15.sp, tilt = -5f)
                    }
                    Spacer(Modifier.weight(1f))
                    if (f.ready) GoArrow(Modifier.size(if (big) 52.dp else 38.dp))
                }
            }
        }
    }
}

/** 검은 동그라미 속 화살표 — '누르면 들어간다' 는 표시 */
@Composable
private fun GoArrow(modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(LibriColors.Ink, r)
        val w = r * 0.16f
        val a = Offset(center.x - r * 0.42f, center.y)
        val b = Offset(center.x + r * 0.42f, center.y)
        drawLine(LibriColors.Card, a, b, strokeWidth = w, cap = StrokeCap.Round)
        drawLine(LibriColors.Card, b, Offset(b.x - r * 0.34f, b.y - r * 0.32f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(LibriColors.Card, b, Offset(b.x - r * 0.34f, b.y + r * 0.32f), strokeWidth = w, cap = StrokeCap.Round)
    }
}
