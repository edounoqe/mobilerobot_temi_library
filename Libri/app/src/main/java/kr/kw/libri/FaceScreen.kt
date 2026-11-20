package kr.kw.libri

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.LibriScreen
import kr.kw.libri.common.NoteText
import kr.kw.libri.common.Sketch
import kr.kw.libri.common.TitleText
import kr.kw.libri.common.bake
import kr.kw.libri.common.highlighter
import kr.kw.libri.common.penciled
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

/**
 * 얼굴 화면 — 아무도 쓰지 않을 때 로봇이 보여 주는 두 눈. 화면을 누르면 홈(기능 선택)으로 넘어간다.
 *  · 눈은 이따금 깜빡이고 여기저기를 둘러본다.
 *  · 화면에 손이 닿거나 temi 가 가까이 온 사람을 알아채면(personNear) 눈웃음을 짓는다.
 * 눈 그림은 한 번만 그려 두고(비트맵), 프레임마다 눈동자(원)와 깜빡임(세로로 줄이기)만 바꾼다.
 */
@Composable
fun FaceScreen(onWake: () -> Unit, personNear: Boolean, modifier: Modifier = Modifier) {
    val wake by rememberUpdatedState(onWake)
    var pressed by remember { mutableStateOf(false) }
    val smile = animateFloatAsState(if (pressed || personNear) 1f else 0f, tween(140), label = "smile")

    // 눈동자가 보는 곳 (-1 ~ 1) — 잠깐씩 다른 곳을 봤다가 가운데로 돌아온다
    val gaze = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(1400, 3200))
            val away = Offset(Random.nextFloat() * 2f - 1f, (Random.nextFloat() * 2f - 1f) * 0.6f)
            gaze.animateTo(away, tween(420, easing = FastOutSlowInEasing))
            delay(Random.nextLong(700, 1700))
            gaze.animateTo(Offset.Zero, tween(420, easing = FastOutSlowInEasing))
        }
    }
    // 눈꺼풀 (1 = 뜬 눈, 0 = 감은 눈) — 가끔은 두 번 연달아 깜빡인다
    val lid = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 5200))
            repeat(if (Random.nextFloat() < 0.25f) 2 else 1) {
                lid.animateTo(0.06f, tween(70))
                lid.animateTo(1f, tween(120))
                delay(90)
            }
        }
    }
    val breath = rememberInfiniteTransition(label = "face").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath",
    )
    val hint = rememberInfiniteTransition(label = "hint").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "alpha",
    )

    LibriScreen(
        modifier
            .semantics { contentDescription = "화면을 누르면 시작합니다" }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) wake()
                    },
                )
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            Spacer(
                Modifier.fillMaxSize().drawWithCache {
                    val u = density
                    val w = size.width
                    val h = size.height
                    val s = min(w, h * 1.5f)                 // 얼굴 크기의 기준
                    val cx = w / 2f
                    val cy = h * (if (w > h) 0.4f else 0.38f)
                    val rx = s * 0.105f                      // 눈 하나의 가로·세로 반지름
                    val ry = s * 0.15f
                    val gap = s * 0.175f                     // 가운데에서 눈 중심까지
                    val m = ceil(8f * u)

                    // 움직이지 않는 부분 — 눈썹, 볼, 입
                    val still = bake(w.toInt(), h.toInt()) {
                        for (side in intArrayOf(-1, 1)) {
                            val bx = cx + side * (gap + rx * 1.55f)
                            val by = cy + ry * 0.95f
                            penciled(0.6f, 1.7f * u) {
                                Sketch(this, 11 + side, u).colorIn(
                                    Path().apply { addOval(Rect(bx - rx * 0.62f, by - rx * 0.36f, bx + rx * 0.62f, by + rx * 0.36f)) },
                                    LibriColors.Coral.copy(alpha = 0.75f), angle = -30f, base = 0.3f, gap = 4f, width = 3.4f,
                                )
                            }
                        }
                        penciled(0.3f, 1.4f * u) {
                            val sk = Sketch(this, 21, u)
                            for (side in intArrayOf(-1, 1)) {
                                val ex = cx + side * gap
                                // 눈썹 — 바깥쪽이 살짝 내려간 둥근 선
                                sk.curve(
                                    floatArrayOf(
                                        ex - side * rx * 0.75f, cy - ry * 1.42f,
                                        ex + side * rx * 0.1f, cy - ry * 1.62f,
                                        ex + side * rx * 0.85f, cy - ry * 1.38f,
                                    ),
                                    width = 5.5f, rough = 1.4f,
                                )
                            }
                            // 입 — 작은 미소
                            sk.curve(
                                floatArrayOf(
                                    cx - rx * 0.62f, cy + ry * 1.22f,
                                    cx, cy + ry * 1.5f,
                                    cx + rx * 0.62f, cy + ry * 1.22f,
                                ),
                                width = 5.5f, rough = 1.4f,
                            )
                        }
                    }
                    // 뜬 눈 — 흰자와 테두리 (눈동자는 프레임마다 그린다)
                    val bw = ceil(2f * (rx + m)).toInt()
                    val bh = ceil(2f * (ry + m)).toInt()
                    val open = bake(bw, bh) {
                        val c = Offset(bw / 2f, bh / 2f)
                        drawOval(LibriColors.Card, Offset(c.x - rx, c.y - ry), androidx.compose.ui.geometry.Size(2f * rx, 2f * ry))
                        penciled(0.28f, 1.4f * u) { Sketch(this, 31, u).oval(c.x, c.y, rx, ry, width = 6f, rough = 1.6f) }
                    }
                    // 눈웃음 — 위로 볼록한 굵은 선
                    val happy = bake(bw, bh) {
                        val c = Offset(bw / 2f, bh / 2f)
                        penciled(0.28f, 1.4f * u) {
                            Sketch(this, 33, u).curve(
                                floatArrayOf(
                                    c.x - rx * 0.92f, c.y + ry * 0.2f,
                                    c.x - rx * 0.5f, c.y - ry * 0.3f,
                                    c.x, c.y - ry * 0.46f,
                                    c.x + rx * 0.5f, c.y - ry * 0.3f,
                                    c.x + rx * 0.92f, c.y + ry * 0.2f,
                                ),
                                width = 7f, rough = 1.4f,
                            )
                        }
                    }
                    val half = Offset(bw / 2f, bh / 2f)
                    val pupil = rx * 0.6f

                    onDrawBehind {
                        val k = smile.value
                        translate(0f, breath.value * 2.5f * u) {
                            drawImage(still)
                            for (side in intArrayOf(-1, 1)) {
                                val c = Offset(cx + side * gap, cy)
                                if (k < 1f) {
                                    scale(1f, lid.value, c) {
                                        drawImage(open, c - half, alpha = 1f - k)
                                        val p = Offset(c.x + gaze.value.x * rx * 0.3f, c.y + gaze.value.y * ry * 0.3f)
                                        drawCircle(LibriColors.Ink.copy(alpha = 1f - k), pupil, p)
                                        drawCircle(LibriColors.Card.copy(alpha = 1f - k), pupil * 0.3f, Offset(p.x - pupil * 0.32f, p.y - pupil * 0.36f))
                                        drawCircle(LibriColors.Card.copy(alpha = (1f - k) * 0.8f), pupil * 0.13f, Offset(p.x + pupil * 0.3f, p.y + pupil * 0.34f))
                                    }
                                }
                                if (k > 0f) drawImage(happy, c - half, alpha = k)
                            }
                        }
                    }
                },
            )

            TitleText("Libri", Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 16.dp), size = if (wide) 34.sp else 28.sp)

            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = if (wide) 44.dp else 72.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (wide) 10.dp else 8.dp),
            ) {
                TitleText(if (personNear) "안녕하세요!" else "잠깐 쉬어 갈래요?", size = if (wide) 50.sp else 34.sp)
                NoteText(
                    "화면을 톡 눌러 주세요", size = if (wide) 30.sp else 24.sp, color = LibriColors.Ink,
                    modifier = Modifier.graphicsLayer { alpha = hint.value }.highlighter(),
                )
            }
        }
    }
}
