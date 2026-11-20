package kr.kw.libri.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * 손그림 모음 — 홈 화면의 기능 카드와 각 게임 화면이 함께 쓰는 연필 그림.
 * 움직이지 않는 부분은 크기에 맞춰 한 번 비트맵으로 그려 두고, 움직이는 부분(눈동자, 팔다리)만 프레임마다 그린다.
 */

/** 0 → 1 을 되풀이하는 시계 (periodMs 마다 한 바퀴) */
@Composable
private fun loop(periodMs: Int): State<Float> =
    rememberInfiniteTransition(label = "doodle").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "t",
    )

private fun ease(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 색연필로 동글동글 칠한 바탕 — 그림마다 자기 색을 갖게 한다 */
private fun DrawScope.scribble(cx: Float, cy: Float, r: Float, color: Color, u: Float, seed: Int) {
    val n = 9
    val pts = FloatArray(2 * n)
    for (i in 0 until n) {
        val a = i * (6.2832f / n)
        val k = 1f + 0.09f * sin(i * 2.4f + seed)
        pts[2 * i] = cx + cos(a) * r * k * 1.06f
        pts[2 * i + 1] = cy + sin(a) * r * k * 0.94f
    }
    penciled(0.62f, 1.7f * u) {
        Sketch(this, seed, u).colorIn(blob(*pts), color, angle = -34f, base = 0.3f, gap = 4.2f, width = 3.6f)
    }
}

// ───────── 참참참 손 ─────────

private const val HAND_WIDEN = 1.22f     // 손을 가로로 조금 넉넉하게

private val SKIN = Color(0xFFFBDDBB)
private val SKIN_MID = Color(0xFFF5C89B)
private val SKIN_DARK = Color(0xFFEBAE7A)
private val NAIL = Color(0xFFFFF3EA)

/**
 * 참참참 손 그림 — 엄지를 위로 세운 손날. 엄지가 맨 위에 보인다.
 * 팔(움직이지 않음)과 손(손목을 축으로 돈다)을 따로 그려 둔다. 길이 단위 k = '손 좌표 1' 의 픽셀 수.
 */
class HandArt(val k: Float, val arm: ImageBitmap, val hand: ImageBitmap) {
    /** 그림 속 손목(회전축) 위치 */
    val handPivot = Offset(HAND_HALF_W * k, HAND_TOP * k)
    val armPivot = Offset(ARM_HALF_W * k, ARM_TOP * k)

    internal companion object {
        const val HAND_HALF_W = 2.6f
        const val HAND_TOP = 7.7f       // 손목에서 그림 위 끝까지
        const val HAND_BOTTOM = 1.2f
        const val ARM_HALF_W = 2.3f
        const val ARM_TOP = 0.7f
        const val ARM_BOTTOM = 5.4f
    }
}

/** @param sleeve 소매 색 */
internal fun bakeHandArt(k: Float, u: Float, sleeve: Color = LibriColors.Coral): HandArt {
    // 손 좌표(손목 중심, 위가 음수) → 그림 좌표로 옮겨 Path 를 만든다
    class Pen(val ox: Float, val oy: Float) {
        fun x(v: Float) = ox + v * HAND_WIDEN * k
        fun y(v: Float) = oy + v * k
        fun shape(build: Pen.(Path) -> Unit): Path = Path().also { build(it) }
        fun Path.m(px: Float, py: Float) = moveTo(x(px), y(py))
        fun Path.l(px: Float, py: Float) = lineTo(x(px), y(py))
        fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, px: Float, py: Float) = cubicTo(x(x1), y(y1), x(x2), y(y2), x(px), y(py))
        fun finger(x0: Float, x1: Float, top: Float, bottom: Float): Path = shape { p ->
            val r = (x1 - x0) / 2f
            p.m(x0, bottom); p.l(x0, top + r); p.c(x0, top - r * 0.4f, x1, top - r * 0.4f, x1, top + r); p.l(x1, bottom); p.close()
        }
    }

    val arm = bake(ceil(2f * HandArt.ARM_HALF_W * k).toInt(), ceil((HandArt.ARM_TOP + HandArt.ARM_BOTTOM) * k).toInt()) {
        val pen = Pen(HandArt.ARM_HALF_W * k, HandArt.ARM_TOP * k)
        with(pen) {
            val forearm = shape { p -> p.m(-0.74f, -0.2f); p.l(0.74f, -0.2f); p.l(0.92f, 1.7f); p.l(-0.92f, 1.7f); p.close() }
            val body = shape { p -> p.m(-1.12f, 2.05f); p.l(1.12f, 2.05f); p.l(1.5f, 5.4f); p.l(-1.5f, 5.4f); p.close() }
            val cuff = shape { p ->
                p.m(-1.2f, 1.35f); p.c(-0.5f, 1.2f, 0.5f, 1.2f, 1.2f, 1.35f); p.l(1.25f, 2.2f)
                p.c(0.5f, 2.32f, -0.5f, 2.32f, -1.25f, 2.2f); p.close()
            }
            penciled(0.5f, 1.6f * u) {
                val sk = Sketch(this, 21, u)
                drawPath(forearm, SKIN)
                sk.hatch(forearm, SKIN_DARK.copy(alpha = 0.7f), angle = 80f, gap = 5f, width = 2.2f)
                drawPath(body, sleeve.copy(alpha = 0.45f))
                sk.hatch(body, sleeve, angle = -60f, gap = 3.6f, width = 3.2f)
                drawPath(cuff, sleeve)
            }
            penciled(0.32f, 1.3f * u) {
                val sk = Sketch(this, 22, u)
                sk.line(x(-0.76f), y(0.1f), x(-0.92f), y(1.45f), width = 2.8f)
                sk.line(x(0.76f), y(0.1f), x(0.92f), y(1.45f), width = 2.8f)
                sk.line(x(-1.12f), y(2.15f), x(-1.5f), y(5.4f), width = 2.8f)
                sk.line(x(1.12f), y(2.15f), x(1.5f), y(5.4f), width = 2.8f)
                sk.trace(cuff, width = 2.8f)
                for (i in -3..3) {                      // 소매 끝단의 골
                    sk.line(x(i * 0.31f), y(1.45f), x(i * 0.33f), y(2.12f), color = LibriColors.Ink.copy(alpha = 0.55f), width = 1.4f, passes = 1)
                }
            }
        }
    }

    val hand = bake(ceil(2f * HandArt.HAND_HALF_W * k).toInt(), ceil((HandArt.HAND_TOP + HandArt.HAND_BOTTOM) * k).toInt()) {
        val pen = Pen(HandArt.HAND_HALF_W * k, HandArt.HAND_TOP * k)
        with(pen) {
            // 엄지를 위로 세운 손날 — 엄지·검지 쪽에서 본 모습.
            // 맨 위에 엄지, 그 아래 검지, 뒤로 중지·약지·새끼손가락이 계단처럼 보인다
            val pinky = finger(-1.32f, -0.46f, -5.5f, -2.9f)
            val ring = finger(-1.04f, -0.12f, -6.45f, -2.9f)
            val middle = finger(-0.74f, 0.2f, -6.98f, -2.9f)
            val index = finger(-0.42f, 0.54f, -6.5f, -2.7f)
            // 손바닥 — 아래는 둥글게 (손목을 돌려도 팔뚝과 매끈하게 이어진다)
            val palm = shape { p ->
                p.m(-0.78f, 0f)
                p.c(-1.12f, -0.8f, -1.38f, -2.2f, -1.32f, -3.2f)
                p.l(0.54f, -3.3f)
                p.c(0.54f, -2.95f, 0.78f, -2.8f, 0.78f, -2.4f)
                p.l(0.8f, 0f)
                p.c(0.8f, 1.05f, -0.78f, 1.05f, -0.78f, 0f)
                p.close()
            }
            val shade = shape { p ->                    // 손등 쪽으로 넘어가는 그늘
                p.m(-0.38f, 0.1f); p.c(-0.54f, -1.0f, -0.52f, -2.4f, -0.44f, -3.24f); p.l(-1.32f, -3.2f)
                p.c(-1.38f, -2.2f, -1.12f, -0.8f, -0.78f, 0f); p.close()
            }
            // 엄지 — 검지 옆에 붙여 세워 손 맨 위에 놓인다
            val thumb = shape { p ->
                p.m(0f, -0.5f)
                p.c(-0.16f, -1.6f, -0.1f, -3.0f, 0.02f, -3.7f)
                p.c(0.06f, -4.32f, 0.84f, -4.32f, 0.86f, -3.7f)
                p.c(0.92f, -2.6f, 0.98f, -1.4f, 0.84f, -0.3f)
                p.close()
            }
            val nail = shape { p ->
                p.m(0.2f, -3.56f); p.c(0.2f, -4.06f, 0.7f, -4.06f, 0.7f, -3.56f); p.l(0.68f, -3.18f)
                p.c(0.52f, -3.08f, 0.38f, -3.08f, 0.22f, -3.18f); p.close()
            }
            penciled(0.5f, 1.6f * u) {
                val sk = Sketch(this, 31, u)
                drawPath(pinky, SKIN_MID); sk.hatch(pinky, SKIN_DARK, angle = 82f, gap = 3.6f, width = 2.4f)
                drawPath(ring, SKIN_MID); sk.hatch(ring, SKIN_DARK.copy(alpha = 0.8f), angle = 82f, gap = 4.4f, width = 2.4f)
                drawPath(middle, SKIN_MID); sk.hatch(middle, SKIN_DARK.copy(alpha = 0.5f), angle = 82f, gap = 5.4f, width = 2.2f)
                drawPath(palm, SKIN)
                sk.hatch(shade, SKIN_DARK.copy(alpha = 0.75f), angle = 110f, gap = 4.4f, width = 2.4f)
                drawPath(index, SKIN)
            }
            penciled(0.32f, 1.3f * u) {
                val sk = Sketch(this, 32, u)
                sk.trace(pinky, closed = false, width = 2.8f)
                sk.trace(ring, closed = false, width = 2.8f)
                sk.trace(middle, closed = false, width = 2.8f)
                // 손목 쪽은 팔뚝으로 이어지므로 열어 둔다
                val left = shape { p -> p.m(-0.78f, 0.05f); p.c(-1.12f, -0.8f, -1.38f, -2.2f, -1.32f, -3.2f) }
                val front = shape { p ->                // 검지에서 손바닥 앞쪽 가장자리까지
                    p.m(-0.42f, -2.75f); p.l(-0.42f, -6.03f)
                    p.c(-0.42f, -6.69f, 0.54f, -6.69f, 0.54f, -6.03f); p.l(0.54f, -3.3f)
                    p.c(0.54f, -2.95f, 0.78f, -2.8f, 0.78f, -2.4f); p.l(0.8f, 0.05f)
                }
                sk.trace(left, closed = false, width = 3f)
                sk.trace(front, closed = false, width = 3f)
                // 손가락이 손바닥에 붙는 자리, 검지 마디 주름
                sk.line(x(-1.32f), y(-3.2f), x(-0.42f), y(-3.26f), width = 2f)
                sk.line(x(-0.4f), y(-5.3f), x(-0.08f), y(-5.3f), width = 1.8f)
                sk.line(x(-0.4f), y(-4.45f), x(-0.06f), y(-4.45f), width = 1.8f)
            }
            // 엄지는 맨 위에 — 앞서 그린 선을 덮는다
            penciled(0.5f, 1.6f * u) {
                val sk = Sketch(this, 33, u)
                drawPath(thumb, SKIN)
                sk.hatch(
                    shape { p -> p.m(0f, -0.5f); p.c(-0.16f, -1.6f, -0.1f, -3.0f, 0.02f, -3.7f); p.l(0.3f, -3.6f); p.c(0.2f, -2.6f, 0.2f, -1.5f, 0.34f, -0.45f); p.close() },
                    SKIN_DARK.copy(alpha = 0.6f), angle = 100f, gap = 4.6f, width = 2.2f,
                )
                drawPath(nail, NAIL)
            }
            penciled(0.32f, 1.3f * u) {
                val sk = Sketch(this, 34, u)
                sk.trace(thumb, closed = false, width = 3f)
                sk.trace(nail, closed = true, width = 1.8f)
                sk.curve(floatArrayOf(x(0.14f), y(-2.5f), x(0.44f), y(-2.42f), x(0.74f), y(-2.52f)), width = 1.8f)     // 엄지 마디
            }
        }
    }
    return HandArt(k, arm, hand)
}

/**
 * 손 그림을 찍는다.
 * @param wrist    손목(회전축)이 놓일 자리
 * @param rotation 손이 가리키는 쪽 — 0 = 정면, 음수 = 화면 왼쪽, 양수 = 화면 오른쪽
 * @param fromTop  false = 내 손 (팔이 화면 아래에서 올라온다), true = 상대 손 (팔이 화면 위, 상대편에서 내려온다)
 */
fun DrawScope.drawHand(art: HandArt, wrist: Offset, rotation: Float, alpha: Float = 1f, fromTop: Boolean = false) {
    val base = if (fromTop) 180f else 0f
    withTransform({
        rotate(base, wrist)
        translate(wrist.x - art.armPivot.x, wrist.y - art.armPivot.y)
    }) {
        drawImage(art.arm, alpha = alpha)
    }
    withTransform({
        // 위에서 내려온 손은 뒤집혀 있으므로, 같은 화면 방향을 가리키려면 반대로 돌려야 한다
        rotate(base + (if (fromTop) -rotation else rotation), wrist)
        translate(wrist.x - art.handPivot.x, wrist.y - art.handPivot.y)
    }) {
        drawImage(art.hand, alpha = alpha)
    }
}

/** 홈 카드용 — 손이 "참, 참, 참!" 박자로 좌우를 가리킨다 */
@Composable
fun HandDoodle(modifier: Modifier = Modifier) {
    val t by loop(3600)
    Spacer(
        modifier.drawWithCache {
            val s = min(size.width, size.height)
            val u = density
            val art = bakeHandArt(s / 11.5f, u)
            val back = bake(size.width.toInt(), size.height.toInt()) {
                scribble(size.width / 2f, size.height * 0.5f, s * 0.43f, LibriColors.Coral, u, seed = 3)
            }
            onDrawBehind {
                drawImage(back)
                // 한 바퀴: 까딱 · 까딱 · 왼쪽! · 까딱 · 까딱 · 오른쪽!
                val half = (t * 2f) % 1f
                val side = if (t < 0.5f) -1f else 1f
                val nod = if (half < 0.5f) sin(half * 4f * PI.toFloat()).let { it * it } else 0f
                val swing = ease((half - 0.5f) / 0.1f) * (1f - ease((half - 0.9f) / 0.1f))
                // 팔은 색칠한 바탕의 아래쪽 가장자리에서 올라온다
                clipRect(bottom = size.height * 0.5f + s * 0.4f) {
                    drawHand(
                        art,
                        Offset(size.width / 2f, size.height * 0.5f + s * 0.17f + nod * s * 0.03f),
                        rotation = side * 52f * swing,
                    )
                }
            }
        }
    )
}

// ───────── 오목 ─────────

private val DOODLE_WOOD = Color(0xFFF5DCA6)

// 돌이 놓이는 순서 (줄 번호 x, y) — 흑·백이 번갈아 두다가 흑이 대각선으로 다섯 알을 잇는다
private val OMOK_MOVES = intArrayOf(2, 2, 2, 3, 1, 3, 1, 4, 3, 1, 3, 3, 0, 4, 2, 1, 4, 0)

/** 홈 카드용 — 오목판 한 귀퉁이에 돌이 하나씩 놓이다가 다섯 알이 이어진다 */
@Composable
fun OmokDoodle(modifier: Modifier = Modifier) {
    val t by loop(6000)
    Spacer(
        modifier.drawWithCache {
            val s = min(size.width, size.height)
            val u = density
            val cx = size.width / 2f
            val cy = size.height / 2f
            val side = s * 0.7f                    // 판 한 변
            val left = cx - side / 2f
            val top = cy - side / 2f
            val pad = side * 0.1f
            val step = (side - 2f * pad) / 5f
            val art = bake(size.width.toInt(), size.height.toInt()) {
                scribble(cx, cy, s * 0.43f, LibriColors.Mint, u, seed = 5)
                drawRect(DOODLE_WOOD, Offset(left, top), androidx.compose.ui.geometry.Size(side, side))
                penciled(0.3f, 1.3f * u) {
                    val sk = Sketch(this, 23, u)
                    for (i in 0..5) {
                        val p = pad + i * step
                        sk.line(left + pad, top + p, left + side - pad, top + p, LibriColors.Ink.copy(alpha = 0.7f), 1.3f, rough = 0.6f, passes = 1)
                        sk.line(left + p, top + pad, left + p, top + side - pad, LibriColors.Ink.copy(alpha = 0.7f), 1.3f, rough = 0.6f, passes = 1)
                    }
                    sk.poly(floatArrayOf(left, top, left + side, top, left + side, top + side, left, top + side), width = 2.8f)
                }
            }
            onDrawBehind {
                drawImage(art)
                fun at(i: Int) = Offset(left + pad + OMOK_MOVES[2 * i] * step, top + pad + OMOK_MOVES[2 * i + 1] * step)
                val count = OMOK_MOVES.size / 2
                val r = step * 0.42f
                for (i in 0 until count) {
                    val age = (t - i * 0.075f) / 0.04f          // 0 → 1 사이에 '딱' 하고 내려앉는다
                    if (age <= 0f) break
                    val k = if (age < 1f) 1.35f - 0.35f * age else 1f
                    val p = at(i)
                    drawCircle(if (i % 2 == 0) LibriColors.Ink else LibriColors.Card, r * k, p)
                    drawCircle(LibriColors.Ink, r * k, p, style = androidx.compose.ui.graphics.drawscope.Stroke(1.7f * u))
                }
                // 다섯 알이 이어지면 색연필로 한 줄 긋는다
                val line = ease((t - 0.74f) / 0.1f)
                if (line > 0f) {
                    val a0 = at(6)      // 대각선의 왼쪽 아래 끝 (0, 4)
                    val b0 = at(8)      // 오른쪽 위 끝 (4, 0)
                    val ext = step * 0.45f / 1.414f
                    val a1 = Offset(a0.x - ext, a0.y + ext)
                    val b1 = Offset(b0.x + ext, b0.y - ext)
                    drawLine(
                        LibriColors.Coral, a1, Offset(a1.x + (b1.x - a1.x) * line, a1.y + (b1.y - a1.y) * line),
                        strokeWidth = 4.2f * u, cap = StrokeCap.Round,
                    )
                }
            }
        }
    )
}

// ───────── 스트레칭 ─────────

private val POSES = arrayOf(
    // 왼팔 위·아래, 오른팔 위·아래 (0° = 위, 90° = 옆, 180° = 아래 — 왼팔은 화면 왼쪽으로 벌어진다), 몸 기울기
    floatArrayOf(12f, 4f, 12f, 4f, 0f),          // 기지개
    floatArrayOf(90f, 90f, 90f, 90f, 0f),        // 양팔 벌리기
    floatArrayOf(90f, 0f, 90f, 0f, 0f),          // 선인장
    floatArrayOf(25f, 55f, 150f, 200f, 14f),     // 옆구리 늘이기
)

/** 홈 카드용 — 종이 인형이 스트레칭 동작을 차례로 한다 */
@Composable
fun StretchDoodle(modifier: Modifier = Modifier) {
    val t by loop(6400)
    Spacer(
        modifier.drawWithCache {
            val s = min(size.width, size.height)
            val u = density
            val back = bake(size.width.toInt(), size.height.toInt()) {
                scribble(size.width / 2f, size.height / 2f, s * 0.43f, LibriColors.Yellow, u, seed = 7)
            }
            val pose = FloatArray(5)
            onDrawBehind {
                drawImage(back)
                val n = POSES.size
                val at = t * n
                val i = at.toInt().coerceIn(0, n - 1)
                val k = ease((at - i - 0.62f) / 0.38f)          // 잠깐 멈췄다가 다음 동작으로
                for (j in pose.indices) pose[j] = POSES[i][j] + (POSES[(i + 1) % n][j] - POSES[i][j]) * k
                paperDoll(size.width / 2f, size.height * 0.5f + s * 0.36f, s * 0.2f, pose)
            }
        }
    )
}

/** 굵은 연필 테두리의 종이 인형 — 선과 원만으로 그린다 (프레임마다 그려도 가볍다). unit = 몸통 길이 */
private fun DrawScope.paperDoll(cx: Float, feetY: Float, unit: Float, pose: FloatArray) {
    val lean = (pose[4] * PI / 180.0).toFloat()
    val hip = Offset(cx, feetY - 1.55f * unit)
    val neck = Offset(hip.x + sin(lean) * unit, hip.y - cos(lean) * unit)
    val head = Offset(neck.x + sin(lean) * 0.42f * unit, neck.y - cos(lean) * 0.42f * unit)
    fun arm(from: Offset, side: Float, a: Float, len: Float): Offset {
        val r = ((a * side) * PI / 180.0).toFloat() + lean
        return Offset(from.x + sin(r) * len * unit, from.y - cos(r) * len * unit)
    }
    val shL = Offset(neck.x - cos(lean) * 0.3f * unit, neck.y - sin(lean) * 0.3f * unit + 0.08f * unit)
    val shR = Offset(neck.x + cos(lean) * 0.3f * unit, neck.y + sin(lean) * 0.3f * unit + 0.08f * unit)
    val elL = arm(shL, -1f, pose[0], 0.56f); val haL = arm(elL, -1f, pose[1], 0.52f)
    val elR = arm(shR, 1f, pose[2], 0.56f); val haR = arm(elR, 1f, pose[3], 0.52f)
    val footL = Offset(cx - 0.3f * unit, feetY)
    val footR = Offset(cx + 0.3f * unit, feetY)
    val hipL = Offset(hip.x - 0.16f * unit, hip.y)
    val hipR = Offset(hip.x + 0.16f * unit, hip.y)

    fun pass(grow: Float, color: Color) {
        fun bone(a: Offset, b: Offset, w: Float) = drawLine(color, a, b, strokeWidth = (w + grow) * unit, cap = StrokeCap.Round)
        bone(hipL, footL, 0.24f); bone(hipR, footR, 0.24f)
        bone(hip, neck, 0.56f)
        bone(shL, elL, 0.2f); bone(elL, haL, 0.19f)
        bone(shR, elR, 0.2f); bone(elR, haR, 0.19f)
        drawCircle(color, (0.3f + grow / 2f) * unit, head)
    }
    pass(0.1f, LibriColors.Ink)
    pass(0f, LibriColors.Card)
}

// ───────── 네컷 사진 ─────────

/** 홈 카드용 — 네 칸짜리 사진 띠 */
@Composable
fun PhotoDoodle(modifier: Modifier = Modifier) {
    val t by loop(2600)
    Spacer(
        modifier.drawWithCache {
            val s = min(size.width, size.height)
            val u = density
            val cx = size.width / 2f
            val cy = size.height / 2f
            val art = bake(size.width.toInt(), size.height.toInt()) {
                scribble(cx, cy, s * 0.43f, LibriColors.Sky, u, seed = 9)
                rotate(-9f, Offset(cx, cy)) {
                    val w = s * 0.3f
                    val h = s * 0.8f
                    val l = cx - w / 2f
                    val top = cy - h / 2f
                    drawRect(LibriColors.Card, Offset(l, top), androidx.compose.ui.geometry.Size(w, h))
                    val cell = (h - s * 0.06f) / 4f
                    penciled(0.5f, 1.6f * u) {
                        val sk = Sketch(this, 51, u)
                        for (i in 0 until 4) {
                            val y = top + s * 0.04f + i * cell
                            val frame = polygon(l + w * 0.14f, y, l + w * 0.86f, y, l + w * 0.86f, y + cell * 0.82f, l + w * 0.14f, y + cell * 0.82f)
                            sk.colorIn(frame, LibriColors.Sky.copy(alpha = 0.55f), angle = -30f + i * 14f, base = 0.35f, gap = 3.6f, width = 2.6f)
                        }
                    }
                    penciled(0.3f, 1.3f * u) {
                        val sk = Sketch(this, 52, u)
                        sk.poly(floatArrayOf(l, top, l + w, top, l + w, top + h, l, top + h), width = 2.8f)
                        for (i in 0 until 4) {
                            val y = top + s * 0.04f + i * cell
                            sk.poly(
                                floatArrayOf(l + w * 0.14f, y, l + w * 0.86f, y, l + w * 0.86f, y + cell * 0.82f, l + w * 0.14f, y + cell * 0.82f),
                                width = 1.8f, rough = 0.7f,
                            )
                            // 칸마다 웃는 얼굴 하나
                            val fx = l + w / 2f
                            val fy = y + cell * 0.41f
                            val fr = cell * 0.24f
                            sk.circle(fx, fy, fr, width = 1.8f, rough = 0.6f, passes = 1)
                            sk.curve(floatArrayOf(fx - fr * 0.45f, fy + fr * 0.15f, fx, fy + fr * 0.5f, fx + fr * 0.45f, fy + fr * 0.15f), width = 1.5f, rough = 0.4f, passes = 1)
                            drawCircle(LibriColors.Ink, 1.2f * u, Offset(fx - fr * 0.35f, fy - fr * 0.2f))
                            drawCircle(LibriColors.Ink, 1.2f * u, Offset(fx + fr * 0.35f, fy - fr * 0.2f))
                        }
                    }
                }
            }
            onDrawBehind {
                drawImage(art)
                // 반짝 — 플래시가 터지는 것처럼 커졌다 작아지는 별
                val p = sin(t * PI.toFloat())
                val c = Offset(cx + s * 0.3f, cy - s * 0.3f)
                val len = s * (0.05f + 0.07f * p)
                for (i in 0 until 4) {
                    val a = (i * 45f * PI / 180.0).toFloat()
                    val k = if (i % 2 == 0) 1f else 0.55f
                    drawLine(
                        LibriColors.Ink, Offset(c.x - cos(a) * len * k, c.y - sin(a) * len * k), Offset(c.x + cos(a) * len * k, c.y + sin(a) * len * k),
                        strokeWidth = 2.6f * u, cap = StrokeCap.Round,
                    )
                }
            }
        }
    )
}

// ───────── 책 (Libri 표지 그림) ─────────

/** 펼친 책과 연필 — 홈 화면 제목 옆에 놓는다 */
@Composable
fun BookDoodle(modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val u = density
            val art = bake(w.toInt(), h.toInt()) {
                val cx = w / 2f
                val top = h * 0.2f
                val bottom = h * 0.84f
                val half = w * 0.44f
                fun page(side: Float): Path = Path().apply {
                    moveTo(cx, top + h * 0.07f)
                    cubicTo(cx + side * half * 0.3f, top - h * 0.06f, cx + side * half * 0.7f, top - h * 0.03f, cx + side * half, top + h * 0.04f)
                    lineTo(cx + side * half, bottom - h * 0.04f)
                    cubicTo(cx + side * half * 0.7f, bottom - h * 0.11f, cx + side * half * 0.3f, bottom - h * 0.13f, cx, bottom)
                    close()
                }
                val left = page(-1f)
                val right = page(1f)
                drawPath(left, LibriColors.Card)
                drawPath(right, LibriColors.Card)
                // 한 줄에만 형광펜
                drawLine(
                    LibriColors.Yellow, Offset(cx + half * 0.16f, top + h * 0.3f), Offset(cx + half * 0.8f, top + h * 0.27f),
                    strokeWidth = h * 0.085f,
                )
                penciled(0.32f, 1.3f * u) {
                    val sk = Sketch(this, 61, u)
                    sk.hatch(
                        Path().apply {          // 책등 쪽 그늘
                            moveTo(cx, top + h * 0.07f); lineTo(cx + half * 0.13f, top + h * 0.03f)
                            lineTo(cx + half * 0.13f, bottom - h * 0.07f); lineTo(cx, bottom); close()
                        },
                        LibriColors.Faint, angle = 60f, gap = 3.2f, width = 1.2f,
                    )
                    sk.trace(left, width = 2.8f)
                    sk.trace(right, width = 2.8f)
                    // 글줄
                    for (i in 0 until 4) {
                        val y = top + h * (0.16f + 0.115f * i)
                        sk.line(cx - half * 0.82f, y + h * 0.02f, cx - half * 0.18f, y + h * 0.05f, color = LibriColors.Faint, width = 1.6f, passes = 1)
                        sk.line(cx + half * 0.18f, y + h * 0.05f, cx + half * (if (i == 3) 0.5f else 0.82f), y + h * 0.02f, color = LibriColors.Faint, width = 1.6f, passes = 1)
                    }
                }
            }
            onDrawBehind { drawImage(art) }
        }
    )
}

/** 연필로 그은 구불구불한 밑줄 */
@Composable
fun Squiggle(modifier: Modifier = Modifier, color: Color = LibriColors.Ink) {
    Spacer(
        modifier.drawWithCache {
            val u = density
            val art = bake(size.width.toInt(), size.height.toInt()) {
                val n = (size.width / (9f * u)).toInt().coerceAtLeast(4)
                val pts = FloatArray(2 * (n + 1))
                for (i in 0..n) {
                    pts[2 * i] = 3f * u + (size.width - 6f * u) * i / n
                    pts[2 * i + 1] = size.height / 2f + (if (i % 2 == 0) -1f else 1f) * size.height * 0.26f
                }
                penciled(0.35f, 1.3f * u) { Sketch(this, 71, u).curve(pts, color = color, width = 2.6f, rough = 0.6f) }
            }
            onDrawBehind { drawImage(art) }
        }
    )
}
