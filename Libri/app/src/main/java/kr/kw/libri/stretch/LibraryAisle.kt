package kr.kw.libri.stretch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.Sketch
import kr.kw.libri.common.paintInto
import kr.kw.libri.common.penciled
import kr.kw.libri.common.polygon
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * 도서관 통로 — 스트레칭 게임의 무대. 책 종이 위에 연필로 그리고 색연필로 칠한 그림책 속 통로다.
 * 아바타가 이 통로를 나아가고, 멀리 있는 것은 아직 그리다 만 것처럼 종이 색으로 옅어진다.
 *
 * 원근: 카메라는 무대 뒤 AISLE_ZP 만큼 떨어진 높이 AISLE_CAM_H 에서 통로를 똑바로 본다.
 *   무대보다 d 만큼 앞에 있는 것은 scale(d) = u * ZP / (ZP + d) 로 줄어들고, 소실점은 (cx, horizonY).
 * 단위는 모두 '몸통 길이 = 1' (아바타 기준) 이고 u 가 그 길이의 픽셀 수다.
 *
 * 그리기 방식: temi V2 는 Android 6 이고, 그 버전은 프레임마다 모양이 바뀌는 Path 를 CPU 로 그린다(느리다).
 *   그래서 프레임마다 그리는 것은 GPU 가 바로 처리하는 것(비트맵·사각형·선·원)만 쓰고,
 *   배경·책장처럼 복잡한 손그림은 한 번만 비트맵으로 그려 두었다가 크기·원근만 바꿔 찍는다.
 */
internal const val AISLE_CAM_H = 3.75f              // 카메라 높이
internal const val AISLE_ZP = 8f                    // 카메라 ~ 무대 거리
internal const val AISLE_FAR = 58f                  // 이보다 먼 것은 아직 그리지 않은 종이
internal const val AISLE_NEAR = -AISLE_ZP * 0.86f   // 카메라 바로 앞 (이보다 가까운 것은 그리지 않는다)
internal const val AISLE_STAGE_W = 4.6f             // 통로 가운데 무대의 폭 — 이 폭이 화면에 들어오게 배치한다

private const val SHELF_X = 3.55f        // 통로 양옆 책장까지의 거리
private const val SHELF_H = 5.4f
private const val SHELF_L = 2.4f         // 책장 한 칸 길이
private const val SHELF_PX = 120f        // 옆 책장 그림에서 몸통 길이 1 의 픽셀 수
private const val SHELF_EDGE = 2         // 옆 책장 그림 둘레의 투명 여백(px) — 기울어진 가장자리가 계단지지 않게
private const val SHELF_VARIANTS = 4
private const val CARPET = 1.95f         // 카펫 반폭

/** 멀리 있는 것이 옅어져 가는 색 — 종이 */
internal val AISLE_FOG = LibriColors.Paper
private val SHELF_WOOD = Color(0xFFE9CFA3)
private val BOOKS = arrayOf(
    LibriColors.Coral, LibriColors.Amber, LibriColors.Yellow, LibriColors.Mint, LibriColors.Sky,
    Color(0xFF9483C9), Color(0xFFF196B4), LibriColors.Card, Color(0xFF6E8FB5), Color(0xFFB9D46E),
)
private val SIDES = floatArrayOf(-1f, 1f)
private val PLANKS = floatArrayOf(-6.4f, -5f, -3.55f, -2.7f, 2.7f, 3.55f, 5f, 6.4f)

/** 화면 크기에 맞춘 통로 배치 */
class AisleGeom(val w: Float, val h: Float, val density: Float) {
    val wide = w > h
    /** 무대 바닥(아바타 발)이 닿는 화면 높이 */
    val feetY = h * (if (wide) 0.9f else 0.78f)
    /** 무대 평면에서 몸통 길이 1 의 픽셀 수 — 가로 화면은 양옆에 안내 패널 자리를 남긴다 */
    val u = min(
        (feetY - (if (wide) 64f else 176f) * density) / 4.0f,
        if (wide) (w - 2f * 232f * density) / AISLE_STAGE_W else w / 3.75f,
    ).coerceAtLeast(8f)
    val cx = w / 2f
    val horizonY = feetY - AISLE_CAM_H * u

    fun scale(d: Float): Float = u * AISLE_ZP / (AISLE_ZP + d)
    fun floorY(s: Float): Float = horizonY + AISLE_CAM_H * s
    fun yOf(height: Float, s: Float): Float = horizonY + (AISLE_CAM_H - height) * s
}

/**
 * 책이 빼곡한 책장 한 면을 (0,0)-(w,h) 에 그린다 — 연필 테두리에 색연필로 칠한 책들.
 * @param s 몸통 길이 1 의 픽셀 수
 */
internal fun DrawScope.paintBookcase(rnd: Random, w: Float, h: Float, s: Float, frameColor: Color, rows: Int) {
    val u = s / 58f                      // 연필 굵기의 기준
    val frame = 0.15f * s
    val board = 0.07f * s
    val rowH = (h - 2f * frame) / rows
    val ink = LibriColors.Ink
    drawRect(LibriColors.Card, size = Size(w, h))
    penciled(0.5f, 1.6f * u) {
        val sk = Sketch(this, rnd.nextInt(), u)
        // 책장 안쪽의 연필 그늘
        sk.hatch(polygon(0f, 0f, w, 0f, w, h, 0f, h), LibriColors.Faint.copy(alpha = 0.55f), angle = -50f, gap = 7f, width = 1.3f)
        for (r in 0 until rows) {
            val shelfTop = frame + (r + 1) * rowH - board
            var x = frame + 0.03f * s
            val end = w - frame - 0.03f * s
            while (x < end) {
                val bw = min((0.09f + rnd.nextFloat() * 0.09f) * s, end - x)
                val gap = rnd.nextFloat() < 0.06f                       // 빈자리
                val bh = (rowH - board) * (0.64f + rnd.nextFloat() * 0.3f)
                val book = BOOKS[rnd.nextInt(BOOKS.size)]
                val label = rnd.nextFloat() < 0.6f
                val lean = if (rnd.nextFloat() < 0.07f && bw > 0.1f * s) 5f else 0f      // 가끔 기대어 선 책
                if (!gap && bw > 0.03f * s) {
                    rotate(lean, Offset(x, shelfTop)) {
                        val top = shelfTop - bh
                        drawRect(book, Offset(x, top), Size(bw - 0.012f * s, bh))
                        if (label) {                                    // 책등 라벨
                            drawRect(LibriColors.Card, Offset(x + 0.012f * s, top + bh * 0.16f), Size(bw - 0.036f * s, bh * 0.1f))
                        }
                        drawRect(ink, Offset(x, top), Size(bw - 0.012f * s, bh), style = Stroke(1.5f * u))
                    }
                }
                x += bw
            }
            drawRect(LibriColors.Card, Offset(frame, shelfTop), Size(w - 2f * frame, board))
            sk.line(frame, shelfTop, w - frame, shelfTop, width = 1.8f, rough = 0.5f)
            sk.line(frame, shelfTop + board, w - frame, shelfTop + board, width = 1.8f, rough = 0.5f)
        }
        // 나무 틀
        drawRect(frameColor, Offset(frame / 2f, frame / 2f), Size(w - frame, h - frame), style = Stroke(frame))
        sk.poly(floatArrayOf(frame, frame, w - frame, frame, w - frame, h - frame, frame, h - frame), width = 2f, rough = 0.5f)
        drawRect(ink, Offset(1.4f * u, 1.4f * u), Size(w - 2.8f * u, h - 2.8f * u), style = Stroke(2.8f * u))
    }
}

/** 화면 크기에 맞춰 한 번 그려 둔 배경 (화면보다 margin 만큼 넓다 — 화면이 흔들려도 가장자리가 비지 않게) */
private class Backdrop(val w: Float, val h: Float, val image: ImageBitmap, val margin: Int)

/** 통로를 그리는 화가 — 미리 그려 둔 비트맵을 들고 있으므로 화면마다 하나씩 remember 해서 쓴다 */
class LibraryAisle {
    private var backdrop: Backdrop? = null
    private var shelfTex: Array<Array<android.graphics.Bitmap>>? = null     // [종류][해상도 단계]
    private var glow: ImageBitmap? = null

    private val shelfMatrix = android.graphics.Matrix()
    private val shelfPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val quadSrc = FloatArray(8)
    private val quadDst = FloatArray(8)
    private val lampTint = ColorFilter.tint(LibriColors.Yellow)

    /**
     * 통로 전체 — 바닥·카펫, 흘러가는 바닥 무늬, 천장 등, 양옆 책장. (바탕 종이는 화면 뒤에 이미 깔려 있다)
     * @param distance 통로를 나아간 거리 (커질수록 풍경이 뒤로 흘러간다)
     */
    fun DrawScope.drawAisle(g: AisleGeom, distance: Float) {
        val bd = backdropFor(g)
        drawImage(bd.image, topLeft = Offset(-bd.margin.toFloat(), -bd.margin.toFloat()))
        floorMarks(g, distance)
        lamps(g, distance)
        shelves(g, distance)
    }

    /** 부드러운 빛 번짐을 (cx, cy) 에 가로 rx · 세로 ry 크기로 찍는다 */
    fun DrawScope.glowAt(cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float, tint: ColorFilter) {
        val sprite = glowSprite()
        withTransform({
            translate(cx - rx, cy - ry)
            scale(2f * rx / sprite.width, 2f * ry / sprite.height, Offset.Zero)
        }) {
            drawImage(sprite, alpha = alpha, colorFilter = tint)
        }
    }

    private fun backdropFor(g: AisleGeom): Backdrop {
        backdrop?.let { if (it.w == g.w && it.h == g.h) return it }
        val m = (12f * g.density).roundToInt() + 2
        val image = ImageBitmap(g.w.roundToInt() + 2 * m, g.h.roundToInt() + 2 * m)
        paintInto(image) {
            translate(m.toFloat(), m.toFloat()) { paintBackdrop(g, m.toFloat()) }
        }
        return Backdrop(g.w, g.h, image, m).also { backdrop = it }
    }

    /** 움직이지 않는 배경 — 통로 끝의 빛, 바닥의 연필 선, 색연필로 칠한 카펫 */
    private fun DrawScope.paintBackdrop(g: AisleGeom, pad: Float) {
        val u = g.density
        val horizon = max(g.horizonY, 1f)
        // 바닥은 아래로 갈수록 살짝 짙은 종이
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, LibriColors.PaperShade.copy(alpha = 0.45f)), startY = horizon, endY = g.h),
            topLeft = Offset(-pad, horizon), size = Size(g.w + 2 * pad, g.h - horizon + pad),
        )
        // 통로 끝에서 번져 오는 빛 (형광펜 색)
        val vp = Offset(g.cx, g.horizonY)
        val r = g.u * 2.6f
        drawCircle(Brush.radialGradient(listOf(LibriColors.Yellow.copy(alpha = 0.5f), LibriColors.Yellow.copy(alpha = 0f)), center = vp, radius = r), r, vp)

        val sF = g.scale(AISLE_FAR)
        val sN = g.scale(AISLE_NEAR)
        val yF = g.floorY(sF)
        val yN = g.floorY(sN)
        fun quadOf(x0: Float, x1: Float): Path = polygon(g.cx + x0 * sF, yF, g.cx + x1 * sF, yF, g.cx + x1 * sN, yN, g.cx + x0 * sN, yN)

        // 카펫 — 색연필로 칠하고, 가장자리에 띠를 한 줄 두른다
        penciled(0.55f, 1.7f * u) {
            val sk = Sketch(this, 801, u)
            drawPath(quadOf(-CARPET, CARPET), LibriColors.Coral.copy(alpha = 0.4f))
            // 색연필 결은 통로 방향으로 — 소실점을 향해 모인다
            val stroke = LibriColors.Coral.copy(alpha = 0.62f)
            val rnd = Random(803)
            var x = -CARPET + 0.03f
            while (x < CARPET - 0.03f) {
                val x2 = x + (rnd.nextFloat() - 0.5f) * 0.03f
                sk.line(g.cx + x * sF, yF, g.cx + x2 * sN, yN, color = stroke, width = 0.05f * g.u / u, rough = 0.6f, passes = 1)
                x += 0.062f + rnd.nextFloat() * 0.03f
            }
            for (side in SIDES) {
                drawPath(quadOf(side * (CARPET - 0.2f), side * (CARPET - 0.07f)), LibriColors.Yellow)
            }
        }
        penciled(0.3f, 1.3f * u) {
            val sk = Sketch(this, 802, u)
            // 마루 결 (통로 방향)
            for (x in PLANKS) {
                sk.line(g.cx + x * sF, yF, g.cx + x * sN, yN, color = LibriColors.Faint, width = 1.4f, passes = 1)
            }
            for (side in SIDES) {
                sk.line(g.cx + side * CARPET * sF, yF, g.cx + side * CARPET * sN, yN, width = 2.6f)
            }
        }
    }

    /** 바닥의 가로 이음매와 카펫 무늬 — 뒤로 흘러가며 나아가는 속도감을 준다 */
    private fun DrawScope.floorMarks(g: AisleGeom, distance: Float) {
        val step = 2f
        val k0 = ceil((distance + AISLE_NEAR) / step).toInt()
        val k1 = floor((distance + AISLE_FAR) / step).toInt()
        for (k in k1 downTo k0) {
            val d = k * step - distance
            val sc = g.scale(d)
            val y = g.floorY(sc)
            val f = fog(d)
            val seam = LibriColors.Pencil.copy(alpha = 0.3f * f)
            val wLine = max(1f, 0.016f * sc)
            drawLine(seam, Offset(g.cx - 16f * sc, y), Offset(g.cx - CARPET * sc, y), strokeWidth = wLine)
            drawLine(seam, Offset(g.cx + CARPET * sc, y), Offset(g.cx + 16f * sc, y), strokeWidth = wLine)
            if (k.mod(2) == 0 && d - 0.7f > AISLE_NEAR) {
                // 마름모 무늬 — 45° 돌린 정사각형을 원근에 맞게 눌러 찍는다
                val halfH = (g.floorY(g.scale(d - 0.7f)) - g.floorY(g.scale(d + 0.7f))) / 2f
                withTransform({
                    translate(g.cx, y)
                    scale(0.55f * sc, halfH, Offset.Zero)
                    rotate(45f, Offset.Zero)
                }) {
                    drawRect(LibriColors.Yellow.copy(alpha = 0.9f * f), Offset(-0.7071f, -0.7071f), Size(1.4142f, 1.4142f))
                    drawRect(LibriColors.Ink.copy(alpha = 0.85f * f), Offset(-0.7071f, -0.7071f), Size(1.4142f, 1.4142f), style = Stroke(0.07f))
                }
            }
        }
    }

    private fun glowSprite(): ImageBitmap = glow ?: ImageBitmap(64, 64).also { bmp ->
        paintInto(bmp) {
            drawRect(
                Brush.radialGradient(
                    0f to Color.White, 0.3f to Color.White.copy(alpha = 0.5f), 1f to Color.Transparent,
                    center = center, radius = size.width / 2f,
                )
            )
        }
        glow = bmp
    }

    /** 천장에 줄지어 매달린 등 */
    private fun DrawScope.lamps(g: AisleGeom, distance: Float) {
        val step = SHELF_L * 4f
        val k0 = ceil((distance + 1f) / step).toInt()
        val k1 = floor((distance + AISLE_FAR) / step).toInt()
        for (k in k1 downTo k0) {
            val d = k * step - distance
            val sc = g.scale(d)
            val y = g.yOf(5.15f, sc)
            if (y < -sc) continue
            val f = fog(d)
            val ink = LibriColors.Ink.copy(alpha = f)
            drawLine(ink, Offset(g.cx, y - 3f * sc), Offset(g.cx, y), strokeWidth = max(1f, 0.025f * sc))
            glowAt(g.cx, y, 1.2f * sc, 1.2f * sc, 0.55f * f, lampTint)
            drawCircle(LibriColors.Yellow.copy(alpha = f), 0.15f * sc, Offset(g.cx, y))
            drawCircle(ink, 0.15f * sc, Offset(g.cx, y), style = Stroke(max(1f, 0.035f * sc)))
        }
    }

    private fun shelfTextures(): Array<Array<android.graphics.Bitmap>> = shelfTex ?: Array(SHELF_VARIANTS) { variant ->
        Array(3) { lod ->                                   // 0 = 가까운 것용(선명) … 2 = 먼 것용(작게 — 멀리서 지글거리지 않는다)
            val px = SHELF_PX / (1 shl lod)
            val w = (SHELF_L * px).roundToInt()
            val h = (SHELF_H * px).roundToInt()
            val bmp = ImageBitmap(w + 2 * SHELF_EDGE, h + 2 * SHELF_EDGE)
            paintInto(bmp) {
                translate(SHELF_EDGE.toFloat(), SHELF_EDGE.toFloat()) {
                    paintBookcase(Random(4000 + variant), w.toFloat(), h.toFloat(), px, SHELF_WOOD, rows = 7)
                    // 가운데 무대가 돋보이도록 옆 책장은 한 톤 옅게 (덜 칠한 그림처럼)
                    drawRect(LibriColors.Paper.copy(alpha = 0.34f), size = Size(w.toFloat(), h.toFloat()))
                }
            }
            bmp.asAndroidBitmap()
        }
    }.also { shelfTex = it }

    /** 통로 양옆으로 이어지는 높은 책장 — 책장 그림 한 칸씩을 원근 변환해 찍는다 */
    private fun DrawScope.shelves(g: AisleGeom, distance: Float) {
        val tex = shelfTextures()
        val canvas = drawContext.canvas.nativeCanvas
        val k0 = floor((distance - 3.2f) / SHELF_L).toInt()
        val k1 = floor((distance + AISLE_FAR) / SHELF_L).toInt()
        for (k in k1 downTo k0) {
            val dA = k * SHELF_L - distance             // 가까운 끝
            val dB = dA + SHELF_L                       // 먼 끝
            val f = fog((dA + dB) / 2f)
            if (f < 0.02f) continue
            val sA = g.scale(dA)
            val sB = g.scale(dB)
            val lod = if (dA < 9f) 0 else if (dA < 24f) 1 else 2
            shelfPaint.alpha = (f * 255f).roundToInt()
            for (side in SIDES) {
                val xA = g.cx + side * SHELF_X * sA
                val xB = g.cx + side * SHELF_X * sB
                if (side < 0f && xB < 0f || side > 0f && xB > g.w) continue      // 화면 밖
                val bmp = tex[(k * 2 + (if (side < 0f) 0 else 1)).mod(SHELF_VARIANTS)][lod]
                val e = SHELF_EDGE.toFloat()
                val r = bmp.width - e
                val b = bmp.height - e
                // 그림의 네 귀퉁이(왼위, 오른위, 오른아래, 왼아래)
                quadSrc[0] = e; quadSrc[1] = e; quadSrc[2] = r; quadSrc[3] = e
                quadSrc[4] = r; quadSrc[5] = b; quadSrc[6] = e; quadSrc[7] = b
                // 화면에서는 가까운 끝이 바깥쪽 — 왼쪽 책장은 그림 왼쪽이 가깝고, 오른쪽 책장은 그림 오른쪽이 가깝다
                val xl = if (side < 0f) xA else xB
                val sl = if (side < 0f) sA else sB
                val xr = if (side < 0f) xB else xA
                val sr = if (side < 0f) sB else sA
                quadDst[0] = xl; quadDst[1] = g.yOf(SHELF_H, sl); quadDst[2] = xr; quadDst[3] = g.yOf(SHELF_H, sr)
                quadDst[4] = xr; quadDst[5] = g.floorY(sr); quadDst[6] = xl; quadDst[7] = g.floorY(sl)
                if (!shelfMatrix.setPolyToPoly(quadSrc, 0, quadDst, 0, 4)) continue
                canvas.save()
                canvas.concat(shelfMatrix)
                canvas.drawBitmap(bmp, 0f, 0f, shelfPaint)
                canvas.restore()
            }
        }
    }

    companion object {
        /** 거리 d 에 있는 것이 또렷하게 보이는 정도 (1 = 또렷, 0 = 아직 그리지 않은 종이) */
        fun fog(d: Float): Float = (1f - (d - 24f) / (AISLE_FAR - 24f)).coerceIn(0f, 1f)

        /** 풍경 무늬가 되풀이되는 거리 — 이만큼 나아가면 처음과 똑같아 보인다 */
        const val LOOP = 48f
    }
}
