package kr.kw.libri.common

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * 손그림 도구 — Libri 의 화면은 '도서관 책 여백에 연필로 그린 낙서' 처럼 보인다.
 *
 * 그리는 방식: 떨리는 선·빗금·연필 결은 Path 와 레이어가 필요한데, temi V2(Android 6)는 프레임마다 바뀌는 Path 를
 *   CPU 로 그려서 느리다. 그래서 손그림은 모두 비트맵에 '한 번만' 그려 두고(bake), 화면에는 그 비트맵을 찍는다.
 *   프레임마다 움직이는 것(아바타, 눈동자 등)은 GPU 가 바로 처리하는 선·원·사각형·비트맵만 쓴다.
 */

/** 비트맵에 그린다 (dp 가 아니라 px 로 — Density 1) */
internal fun paintInto(bmp: ImageBitmap, block: DrawScope.() -> Unit) {
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(bmp.width.toFloat(), bmp.height.toFloat()), block)
}

/** 빈 비트맵을 만들어 그 위에 그린다 */
internal fun bake(w: Int, h: Int, block: DrawScope.() -> Unit): ImageBitmap =
    ImageBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1)).also { paintInto(it, block) }

// ───────── 종이 결 · 연필 결 ─────────

private const val TILE = 128

/** 연필심이 종이 결에 걸리지 않은 자리 — 선과 색칠을 이 무늬만큼 깎아 내면 연필·색연필 느낌이 난다 */
private val grainBitmap: Bitmap by lazy {
    val r = Random(77)
    val px = IntArray(TILE * TILE) {
        val v = r.nextFloat()
        val a = if (v < 0.1f) 0.5f + 0.45f * r.nextFloat() else v * v * 0.42f
        (a * 255f).toInt() shl 24
    }
    Bitmap.createBitmap(px, TILE, TILE, Bitmap.Config.ARGB_8888)
}

/** 종이 바탕 무늬 — 책 종이 색에 아주 옅은 얼룩 */
private val paperBitmap: Bitmap by lazy {
    val r = Random(1234)
    val px = IntArray(TILE * TILE) {
        val d = ((r.nextFloat() + r.nextFloat() - 1f) * 5.5f).toInt()       // 가운데로 몰린 흩어짐
        val warm = if (r.nextFloat() < 0.012f) -9 else 0                    // 드문드문 박힌 티
        (0xFF shl 24) or ((0xF4 + d + warm).coerceIn(0, 255) shl 16) or ((0xEF + d + warm).coerceIn(0, 255) shl 8) or
            (0xE2 + d + warm * 2).coerceIn(0, 255)
    }
    Bitmap.createBitmap(px, TILE, TILE, Bitmap.Config.ARGB_8888)
}

private fun tiled(bmp: Bitmap, scale: Float): ShaderBrush {
    val shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    shader.setLocalMatrix(Matrix().apply { setScale(scale, scale) })
    return ShaderBrush(shader)
}

/** 종이 바탕 붓 — 화면 전체를 이 붓으로 칠한다. scale = 무늬 한 점의 크기(px) */
internal fun paperBrush(scale: Float): Brush = tiled(paperBitmap, scale.coerceAtLeast(1f))

private val layerPaint = Paint()
private val HUGE = Rect(-1e5f, -1e5f, 1e5f, 1e5f)

/**
 * block 에서 그린 것에 연필 결을 입힌다 (비트맵에 그릴 때만 쓴다).
 * @param strength 깎아 내는 정도 0~1 (선은 0.5 안팎, 색칠은 0.8 안팎)
 * @param grain    결 한 점의 크기(px) — 보통 1dp 남짓
 */
internal fun DrawScope.penciled(strength: Float, grain: Float, block: DrawScope.() -> Unit) {
    val canvas = drawContext.canvas
    canvas.saveLayer(HUGE, layerPaint)
    block()
    if (strength > 0f) {
        drawRect(tiled(grainBitmap, grain.coerceAtLeast(1f)), HUGE.topLeft, HUGE.size, alpha = strength, blendMode = BlendMode.DstOut)
    }
    canvas.restore()
}

// ───────── 떨리는 선 ─────────

/**
 * 손으로 그은 듯한 선을 그리는 연필. 같은 seed 면 언제나 같은 그림이 나온다.
 * @param u 굵기·떨림의 기준 길이(px) — 보통 1dp
 */
internal class Sketch(private val ds: DrawScope, seed: Int, val u: Float) {
    private val rnd = Random(seed)
    private val path = Path()
    private val measure = PathMeasure()

    private fun j(amount: Float): Float = (rnd.nextFloat() * 2f - 1f) * amount

    /** 직선 — 살짝 휘고, 끝이 조금 어긋나게 두 번 겹쳐 긋는다 */
    fun line(
        x0: Float, y0: Float, x1: Float, y1: Float,
        color: Color = LibriColors.Ink, width: Float = 2.4f, rough: Float = 1f, passes: Int = 2,
    ) {
        val len = hypot(x1 - x0, y1 - y0)
        if (len < 0.01f) return
        val o = rough * u * min(1.5f, 0.35f + len / (110f * u))
        val nx = -(y1 - y0) / len
        val ny = (x1 - x0) / len
        repeat(passes) { pass ->
            val bow = j(o * 1.5f) + j(len * 0.005f * rough)
            val t = 0.5f + j(0.16f)
            val ax = x0 + j(o); val ay = y0 + j(o)
            val bx = x1 + j(o); val by = y1 + j(o)
            path.reset()
            path.moveTo(ax, ay)
            path.quadraticTo(ax + (bx - ax) * t + nx * bow, ay + (by - ay) * t + ny * bow, bx, by)
            stroke(color, width, pass)
        }
    }

    /** 꺾은선 — 모서리마다 따로 그어 끝이 살짝 삐져나온다 */
    fun poly(pts: FloatArray, closed: Boolean = true, color: Color = LibriColors.Ink, width: Float = 2.4f, rough: Float = 1f, passes: Int = 2) {
        val n = pts.size / 2
        val last = if (closed) n else n - 1
        for (i in 0 until last) {
            val k = (i + 1) % n
            line(pts[2 * i], pts[2 * i + 1], pts[2 * k], pts[2 * k + 1], color, width, rough, passes)
        }
    }

    /** 점들을 부드럽게 잇는 곡선 */
    fun curve(pts: FloatArray, closed: Boolean = false, color: Color = LibriColors.Ink, width: Float = 2.4f, rough: Float = 1f, passes: Int = 2) {
        val n = pts.size / 2
        if (n < 2) return
        val q = FloatArray(pts.size)
        repeat(passes) { pass ->
            val o = rough * u * (if (pass == 0) 0.7f else 1.2f)
            for (i in pts.indices) q[i] = pts[i] + j(o)
            spline(path, q, n, closed)
            stroke(color, width, pass)
        }
    }

    /** 원 · 타원 — 한 바퀴를 조금 넘겨 돌려 끝이 겹친다 */
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color = LibriColors.Ink, width: Float = 2.4f, rough: Float = 1f, passes: Int = 2) {
        val n = 11
        val pts = FloatArray(2 * (n + 2))
        repeat(passes) { pass ->
            val a0 = rnd.nextFloat() * 6.2832f
            val grow = 1f + j(0.02f * rough)
            for (i in 0 until n + 2) {
                val a = a0 + i * (6.2832f / n)
                val k = grow + j(0.012f * rough)
                pts[2 * i] = cx + cos(a) * rx * k + j(rough * u * 0.5f)
                pts[2 * i + 1] = cy + sin(a) * ry * k + j(rough * u * 0.5f)
            }
            spline(path, pts, n + 2, closed = false)
            stroke(color, width, pass)
        }
    }

    fun circle(cx: Float, cy: Float, r: Float, color: Color = LibriColors.Ink, width: Float = 2.4f, rough: Float = 1f, passes: Int = 2) =
        oval(cx, cy, r, r, color, width, rough, passes)

    /** 모서리가 둥근 네모 */
    fun roundRect(
        l: Float, t: Float, r: Float, b: Float, radius: Float,
        color: Color = LibriColors.Ink, width: Float = 2.4f, rough: Float = 1f, passes: Int = 2,
    ) = curve(roundRectPoints(l, t, r, b, radius, 64f * u), closed = true, color, width, rough, passes)

    /** 어떤 모양이든 그 윤곽을 손그림 선으로 따라 그린다 */
    fun trace(shape: Path, closed: Boolean = true, color: Color = LibriColors.Ink, width: Float = 2.4f, rough: Float = 1f, passes: Int = 2, step: Float = 12f) {
        measure.setPath(shape, false)
        val len = measure.length
        if (len < 1f) return
        val n = max(3, ceil(len / (step * u)).toInt())
        val count = if (closed) n else n + 1
        val pts = FloatArray(2 * count)
        for (i in 0 until count) {
            val p = measure.getPosition(len * i / n)
            pts[2 * i] = p.x
            pts[2 * i + 1] = p.y
        }
        curve(pts, closed, color, width, rough, passes)
    }

    /**
     * 빗금 — shape 안쪽을 나란한 선으로 채운다 (연필 그늘, 색연필 칠).
     * @param angle 선의 기울기(°), gap 선 사이 간격 (u 단위)
     */
    fun hatch(shape: Path, color: Color, angle: Float = -42f, gap: Float = 5f, width: Float = 1.3f, rough: Float = 1f) {
        val b = shape.getBounds()
        if (b.isEmpty) return
        val rad = (angle * PI / 180.0).toFloat()
        val dx = cos(rad); val dy = sin(rad)
        val reach = hypot(b.width, b.height) / 2f + 2f * u
        val cx = b.center.x; val cy = b.center.y
        val step = gap * u
        ds.clipPath(shape) {
            var d = -reach
            while (d <= reach) {
                val ox = cx - dy * d + j(step * 0.25f)
                val oy = cy + dx * d + j(step * 0.25f)
                line(ox - dx * reach, oy - dy * reach, ox + dx * reach, oy + dy * reach, color, width, rough, passes = 1)
                d += step * (0.8f + rnd.nextFloat() * 0.4f)
            }
        }
    }

    /** 색연필로 칠하기 — 옅은 바탕색 위에 촘촘한 빗금 */
    fun colorIn(shape: Path, color: Color, angle: Float = -38f, base: Float = 0.5f, gap: Float = 3.4f, width: Float = 3f) {
        ds.drawPath(shape, color.copy(alpha = color.alpha * base))
        hatch(shape, color.copy(alpha = color.alpha * 0.75f), angle, gap, width, rough = 1.2f)
    }

    private fun stroke(color: Color, width: Float, pass: Int) {
        ds.drawPath(
            path, if (pass == 0) color else color.copy(alpha = color.alpha * 0.5f),
            style = Stroke(width * u * (if (pass == 0) 1f else 0.62f), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 점들을 지나는 부드러운 곡선(Catmull-Rom)을 out 에 만든다 */
internal fun spline(out: Path, q: FloatArray, n: Int, closed: Boolean) {
    fun idx(i: Int) = if (closed) i.mod(n) else i.coerceIn(0, n - 1)
    fun x(i: Int) = q[2 * idx(i)]
    fun y(i: Int) = q[2 * idx(i) + 1]
    out.reset()
    out.moveTo(x(0), y(0))
    val segs = if (closed) n else n - 1
    for (i in 0 until segs) {
        out.cubicTo(
            x(i) + (x(i + 1) - x(i - 1)) / 6f, y(i) + (y(i + 1) - y(i - 1)) / 6f,
            x(i + 1) - (x(i + 2) - x(i)) / 6f, y(i + 1) - (y(i + 2) - y(i)) / 6f,
            x(i + 1), y(i + 1),
        )
    }
    if (closed) out.close()
}

/** 점들을 부드럽게 이은 닫힌 모양 */
internal fun blob(vararg pts: Float): Path = Path().also { spline(it, pts, pts.size / 2, closed = true) }

/** 꼭짓점을 직선으로 이은 닫힌 모양 */
internal fun polygon(vararg pts: Float): Path = Path().apply {
    moveTo(pts[0], pts[1])
    for (i in 2 until pts.size step 2) lineTo(pts[i], pts[i + 1])
    close()
}

/** 둥근 네모의 둘레 점 — 긴 변에는 중간 점을 넣어 선 전체가 고르게 떨리게 한다 */
internal fun roundRectPoints(l: Float, t: Float, r: Float, b: Float, radius: Float, edgeStep: Float): FloatArray {
    val rad = min(radius, min(abs(r - l), abs(b - t)) / 2f)
    val out = ArrayList<Float>(96)
    val arc = 4
    // 모서리 중심과 시작 각도 (오른위 → 오른아래 → 왼아래 → 왼위, 시계 방향)
    val cx = floatArrayOf(r - rad, r - rad, l + rad, l + rad)
    val cy = floatArrayOf(t + rad, b - rad, b - rad, t + rad)
    val a0 = floatArrayOf(-90f, 0f, 90f, 180f)
    for (c in 0 until 4) {
        for (i in 0..arc) {
            val a = ((a0[c] + 90f * i / arc) * PI / 180.0).toFloat()
            val px = cx[c] + cos(a) * rad
            val py = cy[c] + sin(a) * rad
            // 알약 모양처럼 변의 길이가 0 이면 앞 모서리의 끝점과 겹친다 — 겹친 점은 한 번만
            val k = out.size
            if (k >= 2 && hypot(out[k - 2] - px, out[k - 1] - py) < 0.5f) continue
            out += px
            out += py
        }
        // 다음 모서리까지의 변
        val nx = cx[(c + 1) % 4]; val ny = cy[(c + 1) % 4]
        val aEnd = ((a0[c] + 90f) * PI / 180.0).toFloat()
        val sx = cx[c] + cos(aEnd) * rad; val sy = cy[c] + sin(aEnd) * rad
        val aNext = (a0[(c + 1) % 4] * PI / 180.0).toFloat()
        val ex = nx + cos(aNext) * rad; val ey = ny + sin(aNext) * rad
        val len = hypot(ex - sx, ey - sy)
        val pieces = (len / edgeStep).toInt()
        for (i in 1..pieces) {
            val k = i / (pieces + 1f)
            out += sx + (ex - sx) * k
            out += sy + (ey - sy) * k
        }
    }
    val k = out.size
    if (k >= 4 && hypot(out[k - 2] - out[0], out[k - 1] - out[1]) < 0.5f) {       // 끝점이 시작점과 겹치면 뺀다
        out.removeAt(k - 1)
        out.removeAt(k - 2)
    }
    return out.toFloatArray()
}
