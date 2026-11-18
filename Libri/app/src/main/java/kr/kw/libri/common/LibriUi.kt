package kr.kw.libri.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kr.kw.libri.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * Libri 공용 화면 스타일 — '도서관 책 여백의 연필 낙서'.
 *  · 바탕은 책 종이, 선은 연필, 버튼과 카드는 굵은 테두리에 딱 떨어지는 그림자.
 *  · 메뉴·안내 화면은 연필 색(흑백)에 형광펜 한 가지 색만 쓰고, 게임 화면에서는 색연필 색을 마음껏 쓴다.
 * 홈·대기실·게임 화면이 같은 종이, 같은 글꼴, 같은 모양의 카드와 버튼을 쓰도록 여기에 모아 둔다.
 */

object LibriColors {
    val Paper = Color(0xFFF4EFE2)       // 책 종이 (바탕)
    val PaperShade = Color(0xFFD9CFB8)  // 종이 가장자리의 바랜 색
    val Card = Color(0xFFFEFCF6)        // 카드 · 버튼 (새 종이)
    val Ink = Color(0xFF24221F)         // 진한 연필 — 제목 · 테두리
    val Pencil = Color(0xFF615C55)      // 연필 — 본문
    val Faint = Color(0xFFA29B8F)       // 옅은 연필 — 덜 중요한 글 · 보조선
    val Yellow = Color(0xFFFFD43B)      // 형광펜 — 강조 · 가장 중요한 버튼
    val Coral = Color(0xFFF2644E)       // 색연필 빨강 — 실패 · 어긋남 · 참참참
    val Amber = Color(0xFFF7A928)       // 색연필 주황 — 거의 맞음
    val Mint = Color(0xFF2BB98B)        // 색연필 초록 — 성공 · 맞음 · 눈 운동
    val Sky = Color(0xFF4A9CEB)         // 색연필 파랑 — 네컷 사진
    val Stamp = Color(0xFFD9412E)       // 도장 인주
}

/** 글꼴 — 제목(굵은 손글씨), 버튼·짧은 이름(둥근 글씨), 연필 메모(손글씨). 본문은 기기 기본 글꼴 */
object LibriFonts {
    // 굵기를 달리 요청해도 가짜로 굵게 만들지 않도록 같은 파일을 Normal·Bold 양쪽에 등록한다
    val Title = FontFamily(Font(R.font.bagel_fat_one, FontWeight.Normal), Font(R.font.bagel_fat_one, FontWeight.Bold))
    val Round = FontFamily(Font(R.font.jua, FontWeight.Normal), Font(R.font.jua, FontWeight.Bold))
    val Hand = FontFamily(Font(R.font.gaegu_bold, FontWeight.Normal), Font(R.font.gaegu_bold, FontWeight.Bold))
}

// ───────── 종이 바탕 ─────────

/** 종이 가장자리의 바랜 색과 얼룩 — 작게 그려 화면 크기로 늘려 찍는다 */
private fun paperShade(w: Float, h: Float): ImageBitmap {
    val bw = max(8, (w / 8f).roundToInt())
    val bh = max(8, (h / 8f).roundToInt())
    return bake(bw, bh) {
        val r = Random(9)
        repeat(7) {                                 // 오래된 책의 옅은 얼룩
            val c = Offset(r.nextFloat() * bw, r.nextFloat() * bh)
            val rad = min(bw, bh) * (0.18f + 0.3f * r.nextFloat())
            drawCircle(Brush.radialGradient(listOf(LibriColors.PaperShade.copy(alpha = 0.16f), Color.Transparent), c, rad), rad, c)
        }
        val c = Offset(bw / 2f, bh / 2f)
        val rad = max(bw, bh) * 0.72f
        drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to LibriColors.PaperShade.copy(alpha = 0.75f), center = c, radius = rad))
    }
}

/** 책 종이 바탕 — 화면(또는 무대) 전체에 깐다 */
fun Modifier.paper(): Modifier = drawWithCache {
    val brush = paperBrush(density * 1.15f)
    val shade = paperShade(size.width, size.height)
    val whole = IntSize(size.width.roundToInt(), size.height.roundToInt())
    onDrawBehind {
        drawRect(brush)
        drawImage(shade, dstSize = whole)
    }
}

/** 화면 틀 — 종이 바탕은 MainActivity 가 창 전체에 깔고, 여기서는 글자 색만 연필 색으로 맞춘다 */
@Composable
fun LibriScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalContentColor provides LibriColors.Ink) { content() }
    }
}

// ───────── 카드 · 버튼의 바탕 ─────────

private fun margin(u: Float): Float = ceil(5f * u)

private fun bakeFace(w: Float, h: Float, rad: Float, fill: Color, stroke: Color, strokeW: Float, seed: Int, u: Float): ImageBitmap {
    val m = margin(u)
    return bake(ceil(w + 2f * m).toInt(), ceil(h + 2f * m).toInt()) {
        translate(m, m) {
            if (fill.alpha > 0f) {
                drawRoundRect(fill, Offset(0.6f * u, 0.6f * u), Size(w - 1.2f * u, h - 1.2f * u), CornerRadius(rad))
            }
            if (stroke.alpha > 0f) {
                penciled(0.4f, 1.3f * u) { Sketch(this, seed, u).roundRect(0f, 0f, w, h, rad, stroke, strokeW, rough = 0.9f) }
            }
        }
    }
}

private fun bakeShadow(w: Float, h: Float, rad: Float, color: Color, u: Float): ImageBitmap {
    val m = margin(u)
    return bake(ceil(w + 2f * m).toInt(), ceil(h + 2f * m).toInt()) {
        translate(m, m) {
            // 연필로 꾹꾹 눌러 칠한 그림자
            penciled(0.38f, 1.5f * u) { drawRoundRect(color, size = Size(w, h), cornerRadius = CornerRadius(rad)) }
        }
    }
}

/**
 * 손그림 테두리의 종이 조각 — 카드와 버튼의 바탕.
 * onClick 이 있으면 누를 때 그림자 쪽으로 꾹 눌린다.
 * @param radius Dp.Infinity 면 알약 모양
 * @param shadow 그림자가 삐져나오는 길이 (0 이면 그림자 없음)
 * @param fillContent 바깥에서 크기를 정해 주었을 때 안쪽 내용도 그 크기를 채울지 (카드는 채우고, 버튼은 가운데에 둔다)
 */
@Composable
fun SketchSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    fill: Color = LibriColors.Card,
    stroke: Color = LibriColors.Ink,
    strokeWidth: Float = 2.6f,
    radius: Dp = 22.dp,
    shadow: Dp = 6.dp,
    shadowColor: Color = LibriColors.Ink,
    seed: Int = 1,
    contentAlignment: Alignment = Alignment.TopStart,
    fillContent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(60), label = "press")
    val click = if (onClick != null) {
        Modifier.clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    } else Modifier

    fun Density.radiusPx(w: Float, h: Float) = if (radius == Dp.Infinity) min(w, h) / 2f else min(radius.toPx(), min(w, h) / 2f)

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .then(click)
            .drawWithCache {
                val off = shadow.toPx()
                val w = size.width - off
                val h = size.height - off
                val bmp = if (off > 0f && w > 4f && h > 4f) bakeShadow(w, h, radiusPx(w, h), shadowColor, density) else null
                val m = margin(density)
                onDrawBehind { if (bmp != null) drawImage(bmp, Offset(off - m, off - m)) }
            },
        propagateMinConstraints = true,
    ) {
        Box(
            modifier = Modifier
                .padding(end = shadow, bottom = shadow)
                .graphicsLayer {
                    val d = shadow.toPx() * press
                    translationX = d
                    translationY = d
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val bmp = if (w > 4f && h > 4f) bakeFace(w, h, radiusPx(w, h), fill, stroke, strokeWidth, seed, density) else null
                    val m = margin(density)
                    onDrawBehind { if (bmp != null) drawImage(bmp, Offset(-m, -m)) }
                },
            contentAlignment = contentAlignment,
            propagateMinConstraints = fillContent,
            content = content,
        )
    }
}

/** 종이 카드 — 굵은 연필 테두리에 딱 떨어지는 그림자 */
@Composable
fun SketchCard(
    modifier: Modifier = Modifier,
    fill: Color = LibriColors.Card,
    radius: Dp = 22.dp,
    shadow: Dp = 6.dp,
    seed: Int = 1,
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    SketchSurface(modifier, fill = fill, radius = radius, shadow = shadow, seed = seed, fillContent = true) {
        Column(
            modifier = Modifier.padding(padding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * 버튼 모양.
 *  INK   = 검은 바탕(주 동작), COLOR = 색 바탕(가장 중요한 한 가지 · 게임 버튼),
 *  PAPER = 흰 종이(그 밖의 동작 · 고른 선택지), FLAT = 옅은 테두리만(뒤로 · 고르지 않은 선택지)
 */
enum class LibriButtonStyle { INK, COLOR, PAPER, FLAT }

@Composable
fun LibriButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LibriButtonStyle = LibriButtonStyle.PAPER,
    color: Color = LibriColors.Yellow,
    enabled: Boolean = true,
    minHeight: Dp = 60.dp,
    fontSize: Int = 20,
    sub: String? = null,
) {
    val flat = style == LibriButtonStyle.FLAT
    val ink = style == LibriButtonStyle.INK
    val content = if (ink) LibriColors.Card else if (flat) LibriColors.Pencil else LibriColors.Ink
    SketchSurface(
        modifier = modifier.heightIn(min = minHeight),
        onClick = onClick,
        enabled = enabled,
        fill = when (style) {
            LibriButtonStyle.INK -> LibriColors.Ink
            LibriButtonStyle.COLOR -> color
            LibriButtonStyle.PAPER -> LibriColors.Card
            LibriButtonStyle.FLAT -> LibriColors.Card.copy(alpha = 0.55f)
        },
        // 검은 버튼은 그림자와 붙어 보이지 않게 밝은 테두리를 두른다
        stroke = if (ink) LibriColors.Card else if (flat) LibriColors.Faint else LibriColors.Ink,
        strokeWidth = if (flat) 1.8f else if (ink) 1.6f else 2.6f,
        radius = Dp.Infinity,
        shadow = if (flat) 0.dp else 5.dp,
        seed = text.hashCode(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, color = content, fontSize = fontSize.sp, fontFamily = LibriFonts.Round, textAlign = TextAlign.Center)
            if (sub != null) {
                Text(sub, color = content.copy(alpha = 0.72f), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 16.sp)
            }
        }
    }
}

// ───────── 글자 ─────────

/** 제목 — 굵은 손글씨 */
@Composable
fun TitleText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 34.sp,
    color: Color = LibriColors.Ink,
    align: TextAlign? = null,
) {
    Text(text, modifier, color = color, fontSize = size, fontFamily = LibriFonts.Title, textAlign = align, lineHeight = size * 1.12f)
}

/** 연필로 적은 메모 */
@Composable
fun NoteText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    color: Color = LibriColors.Pencil,
    align: TextAlign? = null,
) {
    Text(
        text, modifier, color = color, fontSize = size, fontFamily = LibriFonts.Hand, textAlign = align, lineHeight = size * 1.15f,
        letterSpacing = (-0.05).em,      // 이 손글씨체는 글자 사이가 넓어서 조금 좁힌다
    )
}

/** 글자 뒤에 형광펜을 긋는다 */
fun Modifier.highlighter(color: Color = LibriColors.Yellow): Modifier = drawBehind {
    val h = size.height
    val pad = h * 0.12f
    val y = h * 0.64f
    // 살짝 어긋난 두 번의 붓질
    drawLine(color.copy(alpha = 0.8f), Offset(-pad, y + h * 0.02f), Offset(size.width + pad, y - h * 0.05f), strokeWidth = h * 0.4f)
    drawLine(color.copy(alpha = 0.55f), Offset(-pad * 0.4f, y + h * 0.1f), Offset(size.width + pad * 1.6f, y + h * 0.06f), strokeWidth = h * 0.22f)
}

/** 도장 — 기울어진 테두리 안의 굵은 글자. 인주가 고르게 묻지 않은 것처럼 얼룩덜룩하다 */
@Composable
fun Stamp(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LibriColors.Stamp,
    fontSize: TextUnit = 22.sp,
    tilt: Float = -7f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val art = remember(text, color, fontSize, density) {
        val u = density.density
        val px = with(density) { fontSize.toPx() }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.bagel_fat_one)
            textSize = px
            this.color = color.toArgb()
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val padX = px * 0.42f
        val padY = px * 0.3f
        val w = bounds.width() + 2f * padX
        val h = bounds.height() + 2f * padY
        val m = margin(u)
        bake(ceil(w + 2f * m).toInt(), ceil(h + 2f * m).toInt()) {
            penciled(0.75f, 1.7f * u) {
                translate(m, m) {
                    Sketch(this, text.hashCode(), u).roundRect(0f, 0f, w, h, px * 0.32f, color, max(2.6f, px / u * 0.1f), rough = 0.7f)
                    drawIntoCanvas { it.nativeCanvas.drawText(text, padX - bounds.left, padY - bounds.top, paint) }
                }
            }
        }
    }
    Canvas(
        modifier
            .size(with(density) { art.width.toDp() }, with(density) { art.height.toDp() })
            .graphicsLayer { rotationZ = tilt },
    ) { drawImage(art) }
}

/** 작은 꼬리표 (예: '약 1분', '공격') */
@Composable
fun LibriTag(text: String, color: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 14.sp) {
    Text(
        text, color = LibriColors.Ink, fontSize = fontSize, fontFamily = LibriFonts.Round,
        modifier = modifier
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(color.copy(alpha = 0.32f), cornerRadius = r)
                drawRoundRect(LibriColors.Ink, cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()))
            }
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

// ───────── 화면 틀 ─────────

/** 게임 화면 맨 위 띠 — 왼쪽 내용(제목·점수 등) + 오른쪽 '나가기' */
@Composable
fun LibriTopBar(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    exitLabel: String = "나가기",
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(LibriColors.Paper.copy(alpha = 0.9f))
            .drawBehind {       // 아래쪽에 연필로 그은 줄
                drawLine(LibriColors.Ink, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
            }
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
    ) {
        content()
        LibriButton(exitLabel, onExit, minHeight = 46.dp, fontSize = 17)
    }
}

/**
 * 대기실 틀 — 뒤로 가기, 제목, 한 줄 소개 아래에 '방법'(intro)과 '고르고 시작하기'(content)를 놓는다.
 * 넓은 화면(temi)에서는 책을 펼친 것처럼 왼쪽 쪽에 방법, 오른쪽 쪽에 시작하기가 온다.
 * @param art 이 게임의 그림 — 제목 옆에 놓는다. artOnRight 면 넓은 화면에서는 오른쪽 쪽 맨 위에 크게 놓는다.
 */
@Composable
fun LibriLobby(
    title: String,
    tagline: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    art: (@Composable (Modifier) -> Unit)? = null,
    artOnRight: Boolean = false,
    intro: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    LibriScreen(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            val viewport = maxHeight
            val heading: @Composable ColumnScope.() -> Unit = {
                LibriButton("←  처음으로", onBack, style = LibriButtonStyle.FLAT, minHeight = 44.dp, fontSize = 16)
                // 제목과 한 줄 소개, 그 옆에 이 게임의 그림
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TitleText(title, size = if (wide) 54.sp else 40.sp)
                        NoteText(tagline, size = if (wide) 24.sp else 21.sp)
                    }
                    if (art != null && !(wide && artOnRight)) art(Modifier.size(if (wide) 168.dp else 118.dp))
                }
            }
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 40.dp, vertical = 24.dp)
                        .heightIn(min = viewport - 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        heading()
                        intro()
                    }
                    Column(Modifier.weight(1f).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (art != null && artOnRight) art(Modifier.size(250.dp).align(Alignment.CenterHorizontally))
                        content()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    heading()
                    intro()
                    content()
                }
            }
        }
    }
}

/** 방법 설명 한 줄 — 연필로 동그라미 친 번호 + 글 */
@Composable
fun LibriStep(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).drawWithCache {
                val u = density
                val m = margin(u)
                val bmp = bake(ceil(size.width + 2f * m).toInt(), ceil(size.height + 2f * m).toInt()) {
                    translate(m, m) {
                        val c = Offset(this@drawWithCache.size.width / 2f, this@drawWithCache.size.height / 2f)
                        drawCircle(LibriColors.Yellow, c.x - 1.5f * u, c)
                        penciled(0.4f, 1.3f * u) { Sketch(this, n * 31, u).circle(c.x, c.y, c.x - 1f * u, width = 2.2f) }
                    }
                }
                onDrawBehind { drawImage(bmp, Offset(-m, -m)) }
            },
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = LibriColors.Ink, fontSize = 19.sp, fontFamily = LibriFonts.Round)
        }
        Text(text, fontSize = 17.sp, lineHeight = 23.sp, color = LibriColors.Ink)
    }
}

/** 작은 제목 (예: '코스', '누구와 할까요?') */
@Composable
fun LibriLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = LibriColors.Ink, fontSize = 20.sp, fontFamily = LibriFonts.Round)
}

/** 종이 화면용 입력창 색 */
@Composable
fun libriTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LibriColors.Ink,
    unfocusedTextColor = LibriColors.Ink,
    focusedContainerColor = LibriColors.Card,
    unfocusedContainerColor = LibriColors.Card,
    focusedBorderColor = LibriColors.Ink,
    unfocusedBorderColor = LibriColors.Pencil,
    focusedLabelColor = LibriColors.Ink,
    unfocusedLabelColor = LibriColors.Pencil,
    cursorColor = LibriColors.Ink,
)

// ───────── 카메라 권한 ─────────

/** 카메라 권한 — granted 를 보고, 없으면 request() 로 묻는다 (Android 6 부터는 실행 중에 물어봐야 한다) */
class CameraAccess(val granted: Boolean, val request: () -> Unit)

/**
 * @param onResult 권한 요청 창이 닫힌 뒤 불린다 (허용했으면 true)
 */
@Composable
fun rememberCameraAccess(onResult: (Boolean) -> Unit = {}): CameraAccess {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        currentOnResult(ok)
    }
    return CameraAccess(granted) { launcher.launch(Manifest.permission.CAMERA) }
}
