package kr.kw.libri.stretch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.paintInto
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * 스트레칭 무대 그리기 — 종이 위의 도서관 통로(LibraryAisle) 위에 책장 벽·아바타·효과를 얹는다.
 *
 * 벽의 구멍과 아바타를 같은 마네킹·같은 원근 식으로 그리므로, 벽이 아바타 자리(d = 0)에 오면 구멍이 아바타에 정확히 겹친다.
 * 프레임마다 그리는 것은 비트맵·사각형·선·원만 쓴다 (이유는 LibraryAisle 의 설명 참고).
 */
private const val D_GONE = -3.4f         // 통과한 벽이 사라지는 거리
private const val WALL_T = 0.55f         // 벽 두께
private const val RIM = 0.06f            // 구멍 테두리(굵은 연필 선) 두께

private val WOOD_FRAMES = arrayOf(Color(0xFFEBC98F), Color(0xFFE2B878), Color(0xFFF0D6A6))
private val BACK_TINT = ColorFilter.tint(Color(0xFF8E877B).copy(alpha = 0.6f), BlendMode.SrcAtop)
private val CONFETTI = arrayOf(
    LibriColors.Coral, LibriColors.Yellow, LibriColors.Mint, LibriColors.Sky, LibriColors.Amber, LibriColors.Ink,
)

internal val FIT_OFF = LibriColors.Coral
internal val FIT_MID = LibriColors.Amber
internal val FIT_ON = LibriColors.Mint
/** 다음 벽의 구멍 모양을 미리 보여 주는 안내선 색 */
internal val GUIDE = LibriColors.Sky
private val BODY = LibriColors.Card
private val BODY_DIM = Color(0xFFD3CCBE)
private val OUTLINE = LibriColors.Ink

/** 뼈대 점수(0~1) → 색. 판정하지 않는 뼈(음수)는 기본 몸 색 */
internal fun fitColor(s: Float): Color = when {
    s < 0f -> BODY
    s < 0.5f -> lerp(FIT_OFF, FIT_MID, s * 2f)
    else -> lerp(FIT_MID, FIT_ON, (s - 0.5f) * 2f)
}

/** 마네킹 그리기 — 아바타, 벽의 구멍, 안내 윤곽, 동작 아이콘이 모두 이것을 쓴다 */
internal class FigurePainter(
    private val ds: DrawScope,
    private val ox: Float,      // 가운데 x (px)
    private val oy: Float,      // 바닥 y (px)
    private val s: Float,       // 몸통 길이 1 의 픽셀 수
) {
    private fun at(f: Figure, j: Int) = Offset(ox + f.x[j] * s, oy - f.y[j] * s)

    private fun limb(f: Figure, a: Int, b: Int, width: Float, grow: Float, color: Color, blend: BlendMode) {
        ds.drawLine(color, at(f, a), at(f, b), strokeWidth = (width + 2f * grow) * s, cap = StrokeCap.Round, blendMode = blend)
    }

    /** 몸통(어깨선 ~ 골반선 사다리꼴) — Path 대신 굵은 선을 촘촘히 겹쳐 채운다 */
    private fun torso(f: Figure, grow: Float, color: Color, blend: BlendMode) {
        val sl = at(f, Joint.SHO_L)
        val sr = at(f, Joint.SHO_R)
        val hl = at(f, Joint.HIP_L)
        val hr = at(f, Joint.HIP_R)
        val width = (TORSO_PAD + grow) * 2f * s
        for (i in 0..TORSO_STRIPS) {
            val t = i / TORSO_STRIPS.toFloat()
            ds.drawLine(
                color, Offset(sl.x + (sr.x - sl.x) * t, sl.y + (sr.y - sl.y) * t),
                Offset(hl.x + (hr.x - hl.x) * t, hl.y + (hr.y - hl.y) * t),
                strokeWidth = width, cap = StrokeCap.Round, blendMode = blend,
            )
        }
        ds.drawLine(color, sl, sr, strokeWidth = width, cap = StrokeCap.Round, blendMode = blend)
        ds.drawLine(color, hl, hr, strokeWidth = width, cap = StrokeCap.Round, blendMode = blend)
    }

    /** 다리 · 몸통 · 목 · 머리 */
    private inline fun lower(f: Figure, grow: Float, blend: BlendMode, colorOf: (Bone) -> Color) {
        limb(f, Joint.HIP_L, Joint.KNE_L, THIGH_W, grow, colorOf(Bone.L_THIGH), blend)
        limb(f, Joint.KNE_L, Joint.ANK_L, SHIN_W, grow, colorOf(Bone.L_SHIN), blend)
        limb(f, Joint.ANK_L, Joint.TOE_L, FOOT_W, grow, colorOf(Bone.L_SHIN), blend)
        limb(f, Joint.HIP_R, Joint.KNE_R, THIGH_W, grow, colorOf(Bone.R_THIGH), blend)
        limb(f, Joint.KNE_R, Joint.ANK_R, SHIN_W, grow, colorOf(Bone.R_SHIN), blend)
        limb(f, Joint.ANK_R, Joint.TOE_R, FOOT_W, grow, colorOf(Bone.R_SHIN), blend)
        torso(f, grow, colorOf(Bone.SPINE), blend)
        limb(f, Joint.NECK, Joint.HEAD, NECK_W, grow, colorOf(Bone.NECK), blend)
        ds.drawCircle(colorOf(Bone.NECK), (Body.HEAD_R + grow) * s, at(f, Joint.HEAD), blendMode = blend)
    }

    private inline fun arms(f: Figure, grow: Float, blend: BlendMode, colorOf: (Bone) -> Color) {
        limb(f, Joint.SHO_L, Joint.ELB_L, UPPER_ARM_W, grow, colorOf(Bone.L_UPPER_ARM), blend)
        limb(f, Joint.ELB_L, Joint.WRI_L, FOREARM_W, grow, colorOf(Bone.L_FOREARM), blend)
        ds.drawCircle(colorOf(Bone.L_FOREARM), (Body.HAND_R + grow) * s, at(f, Joint.WRI_L), blendMode = blend)
        limb(f, Joint.SHO_R, Joint.ELB_R, UPPER_ARM_W, grow, colorOf(Bone.R_UPPER_ARM), blend)
        limb(f, Joint.ELB_R, Joint.WRI_R, FOREARM_W, grow, colorOf(Bone.R_FOREARM), blend)
        ds.drawCircle(colorOf(Bone.R_FOREARM), (Body.HAND_R + grow) * s, at(f, Joint.WRI_R), blendMode = blend)
    }

    /** 한 가지 색의 실루엣. grow 만큼 부풀린다 (구멍 여유, 테두리) */
    fun silhouette(f: Figure, grow: Float, color: Color, blend: BlendMode = BlendMode.SrcOver) {
        lower(f, grow, blend) { color }
        arms(f, grow, blend) { color }
    }

    /** 굵은 연필 테두리에 속을 칠한 종이 인형 (동작 아이콘용) */
    fun doll(f: Figure, fill: Color = BODY, outline: Color = OUTLINE) {
        silhouette(f, OUTLINE_W, outline)
        silhouette(f, 0f, fill)
    }

    /**
     * 아바타 — 굵은 연필 테두리의 종이 인형. 뼈대별 점수에 따라 색이 바뀐다 (어긋남: 빨강 → 맞음: 초록).
     * @param dim 0 = 선명, 1 = 흐림 (카메라가 몸을 놓쳤을 때)
     */
    fun avatar(f: Figure, fit: FloatArray, dim: Float) {
        fun shade(b: Bone): Color {
            val c = fitColor(fit[b.ordinal])
            return if (dim <= 0f) c else lerp(c, BODY_DIM, dim)
        }
        val outline = if (dim <= 0f) OUTLINE else lerp(OUTLINE, LibriColors.Faint, dim)
        // 잘 맞은 팔 뒤로 번지는 색
        if (dim <= 0f) {
            val halo = FIT_ON.copy(alpha = 0.28f)
            if (fit[Bone.L_UPPER_ARM.ordinal] >= GLOW_AT) limb(f, Joint.SHO_L, Joint.ELB_L, UPPER_ARM_W, 0.11f, halo, BlendMode.SrcOver)
            if (fit[Bone.L_FOREARM.ordinal] >= GLOW_AT) limb(f, Joint.ELB_L, Joint.WRI_L, FOREARM_W, 0.11f, halo, BlendMode.SrcOver)
            if (fit[Bone.R_UPPER_ARM.ordinal] >= GLOW_AT) limb(f, Joint.SHO_R, Joint.ELB_R, UPPER_ARM_W, 0.11f, halo, BlendMode.SrcOver)
            if (fit[Bone.R_FOREARM.ordinal] >= GLOW_AT) limb(f, Joint.ELB_R, Joint.WRI_R, FOREARM_W, 0.11f, halo, BlendMode.SrcOver)
        }
        // 팔이 몸통 앞을 지날 때도 구분되도록, 몸을 먼저 그리고 팔을 외곽선과 함께 그 위에 얹는다
        lower(f, OUTLINE_W, BlendMode.SrcOver) { outline }
        lower(f, 0f, BlendMode.SrcOver) { shade(it) }
        arms(f, OUTLINE_W, BlendMode.SrcOver) { outline }
        arms(f, 0f, BlendMode.SrcOver) { shade(it) }
        // 얼굴 — 점 두 개와 웃는 입
        val head = at(f, Joint.HEAD)
        val r = Body.HEAD_R * s
        ds.drawCircle(outline, r * 0.11f, Offset(head.x - r * 0.36f, head.y - r * 0.08f))
        ds.drawCircle(outline, r * 0.11f, Offset(head.x + r * 0.36f, head.y - r * 0.08f))
        val mouth = r * 0.09f
        ds.drawLine(outline, Offset(head.x - r * 0.26f, head.y + r * 0.3f), Offset(head.x, head.y + r * 0.42f), strokeWidth = mouth, cap = StrokeCap.Round)
        ds.drawLine(outline, Offset(head.x, head.y + r * 0.42f), Offset(head.x + r * 0.26f, head.y + r * 0.3f), strokeWidth = mouth, cap = StrokeCap.Round)
    }

    private companion object {
        const val UPPER_ARM_W = 0.18f
        const val FOREARM_W = 0.16f
        const val THIGH_W = 0.24f
        const val SHIN_W = 0.2f
        const val FOOT_W = 0.16f
        const val NECK_W = 0.16f
        const val TORSO_PAD = 0.1f
        const val TORSO_STRIPS = 6
        const val OUTLINE_W = 0.05f
        const val GLOW_AT = 0.85f
    }
}

/** 벽 하나의 미리 그려 둔 그림 — 프레임마다 책을 다시 그리지 않고 이 그림을 크기만 바꿔 찍는다 */
private class WallArt(
    val face: ImageBitmap,      // 책장 벽 + 구멍
    val guide: ImageBitmap,     // 구멍 윤곽 (아바타 자리에 띄우는 안내선)
    val facePx: Float,          // face 에서 몸통 길이 1 의 픽셀 수
    val guidePx: Float,
)

internal class StageRenderer {
    private val aisle = LibraryAisle()
    private val fig = Figure()
    private val arts = HashMap<Int, WallArt>()
    private val haloTint = ColorFilter.tint(LibriColors.Yellow)

    /** @param frame 프레임 번호 — 읽기만 해도 Compose 가 프레임마다 다시 그리게 된다 */
    fun DrawScope.drawStage(s: StretchSession, @Suppress("UNUSED_PARAMETER") frame: Long) {
        if (size.minDimension < 16f) return
        val g = AisleGeom(size.width, size.height, density)
        val e = s.engine
        val fx = s.fx
        prune(e, fx)

        val shake = fx.shake * 9f * density
        translate(sin(e.timeMs * 0.085f) * shake, cos(e.timeMs * 0.067f) * shake * 0.7f) {
            with(aisle) { drawAisle(g, e.distance) }
            walls(g, e, behind = true)
            guide(g, e)
            platform(g, e.timeMs)
            s.shown.layout(fig)
            FigurePainter(this, g.cx, g.feetY, g.u).avatar(fig, e.boneFit, if (s.tracked) 0f else 0.6f)
            walls(g, e, behind = false)
            shards(g, fx)
            ring(g, fx)
            confetti(g, fx)
        }
        if (fx.flash > 0f) {
            drawRect(if (fx.flashHit) LibriColors.Coral.copy(alpha = 0.3f * fx.flash) else LibriColors.Yellow.copy(alpha = 0.22f * fx.flash))
        }
    }

    // ───────── 벽 ─────────

    /** 필요 없어진 벽 그림을 버린다 (지나간 벽, 다 부서진 벽) */
    private fun prune(e: StretchEngine, fx: StageFx) {
        if (arts.isEmpty()) return
        val it = arts.keys.iterator()
        while (it.hasNext()) {
            val i = it.next()
            val w = e.walls.getOrNull(i)
            val done = when {
                w == null -> true
                w.grade == Grade.MISS -> fx.shards.none { sh -> sh.wall == i } && e.timeMs - w.judgedAtMs > 200f
                else -> w.at - e.distance < D_GONE
            }
            if (done) it.remove()
        }
    }

    private fun artFor(w: Wall, g: AisleGeom): WallArt {
        val want = min(g.u, 176f)
        arts[w.index]?.let { if (abs(it.facePx - want) < 2f) return it }
        return buildArt(w.index, w.pose, want).also { arts[w.index] = it }
    }

    private fun buildArt(index: Int, pose: StretchPose, px: Float): WallArt {
        val f = Figure().also { pose.pose.layout(it) }
        val (face, facePx) = wallBitmap(px)
        paintInto(face) {
            paintBookcase(Random(index * 7919 + 17), size.width, size.height, facePx, WOOD_FRAMES[index.mod(WOOD_FRAMES.size)], rows = 6)
            val p = FigurePainter(this, size.width / 2f, size.height, facePx)
            p.silhouette(f, HOLE_MARGIN + RIM, LibriColors.Ink)                // 구멍 둘레의 굵은 연필 선
            p.silhouette(f, HOLE_MARGIN, Color.Black, BlendMode.Clear)       // 구멍을 뚫는다
        }
        val (guide, guidePx) = wallBitmap(px * 0.5f)
        paintInto(guide) {
            val p = FigurePainter(this, size.width / 2f, size.height, guidePx)
            p.silhouette(f, HOLE_MARGIN + RIM, GUIDE)
            p.silhouette(f, HOLE_MARGIN, GUIDE.copy(alpha = 0.16f), BlendMode.Src)   // 안쪽은 옅게
        }
        return WallArt(face, guide, facePx, guidePx)
    }

    /** 벽 크기의 빈 비트맵. 반환: 비트맵과 실제 '몸통 길이 1' 픽셀 수 */
    private fun wallBitmap(px: Float): Pair<ImageBitmap, Float> {
        val w = (WALL_W * px).roundToInt().coerceAtLeast(8)
        val s = w / WALL_W
        return ImageBitmap(w, (WALL_H * s).roundToInt().coerceAtLeast(8)) to s
    }

    private fun DrawScope.face(g: AisleGeom, art: WallArt, d: Float, alpha: Float, tint: ColorFilter?) {
        val sc = g.scale(d)
        val k = sc / art.facePx
        withTransform({
            translate(g.cx - WALL_W / 2f * sc, g.floorY(sc) - WALL_H * sc)
            scale(k, k, Offset.Zero)
        }) {
            drawImage(art.face, alpha = alpha, colorFilter = tint)
        }
    }

    /**
     * 벽을 그린다. 벽에는 두께가 있어서 앞면(카메라 쪽)과 뒷면을 따로 찍는다.
     * @param behind true = 아바타보다 뒤에 있는 면만, false = 아바타보다 앞에 있는 면만
     */
    private fun DrawScope.walls(g: AisleGeom, e: StretchEngine, behind: Boolean) {
        for (i in e.walls.indices.reversed()) {       // 먼 벽부터
            val w = e.walls[i]
            if (w.grade == Grade.MISS) continue       // 부딪힌 벽은 파편으로 그린다
            val d = w.at - e.distance
            if (d > AISLE_FAR || d < D_GONE) continue
            val art = artFor(w, g)
            val fadeIn = ((AISLE_FAR - d) / 9f).coerceIn(0f, 1f)
            val fadeOut = if (d < 0f) (1f - d / D_GONE).coerceIn(0f, 1f) else 1f
            val alpha = fadeIn * fadeOut
            val dBack = d + WALL_T
            if ((dBack >= 0f) == behind) face(g, art, dBack, alpha, BACK_TINT)
            if ((d >= 0f) == behind) {
                if (behind) {                         // 바닥에 지는 그림자
                    val s0 = g.scale(d)
                    val top = g.floorY(s0)
                    drawRect(
                        LibriColors.Ink.copy(alpha = 0.16f * alpha),
                        Offset(g.cx - WALL_W / 2f * s0, top), Size(WALL_W * s0, g.floorY(g.scale(d - 0.9f)) - top),
                    )
                }
                val haze = 1f - LibraryAisle.fog(d)
                val tint = if (haze > 0.02f) ColorFilter.tint(AISLE_FOG.copy(alpha = haze * 0.75f), BlendMode.SrcAtop) else null
                face(g, art, d, alpha, tint)
            }
        }
    }

    /** 다음 벽의 구멍 윤곽을 아바타 자리에 미리 띄운다 — 벽이 멀리 있을 때부터 어떤 자세인지 알 수 있다 */
    private fun DrawScope.guide(g: AisleGeom, e: StretchEngine) {
        val w = e.target ?: return
        val art = artFor(w, g)
        val ahead = w.at - e.distance
        val appear = (w.activeMs / 350f).coerceIn(0f, 1f)
        val leave = (ahead / 2.4f).coerceIn(0f, 1f)        // 벽이 다가오면 진짜 구멍에 자리를 내준다
        val pulse = 0.72f + 0.22f * sin(e.timeMs / 240f)
        val k = g.u / art.guidePx
        withTransform({
            translate(g.cx - WALL_W / 2f * g.u, g.feetY - WALL_H * g.u)
            scale(k, k, Offset.Zero)
        }) {
            drawImage(art.guide, alpha = pulse * appear * leave)
        }
    }

    /** 아바타가 서 있는 발판 — 형광펜으로 칠하고 연필로 두른 동그라미 */
    private fun DrawScope.platform(g: AisleGeom, timeMs: Float) {
        val cx = g.cx
        val cy = g.feetY + 0.03f * g.u
        val rx = 1.05f * g.u
        val ry = rx * 0.2f
        with(aisle) { glowAt(cx, cy, rx * 1.6f, ry * 1.6f, 0.5f, haloTint) }
        drawOval(LibriColors.Yellow, Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry))
        drawOval(LibriColors.Ink, Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry), style = Stroke(0.04f * g.u))
        val p = (timeMs / 900f) % 1f                           // 바깥으로 퍼지는 물결
        val rr = rx * (0.4f + 0.85f * p)
        drawOval(
            LibriColors.Ink.copy(alpha = 0.45f * (1f - p)), Offset(cx - rr, cy - rr * 0.2f), Size(2f * rr, 0.4f * rr),
            style = Stroke(0.022f * g.u),
        )
    }

    // ───────── 효과 ─────────

    private fun DrawScope.shards(g: AisleGeom, fx: StageFx) {
        for (sh in fx.shards) {
            val art = arts[sh.wall] ?: continue
            val tw = art.face.width / StageFx.SHARD_COLS
            val th = art.face.height / StageFx.SHARD_ROWS
            val k = g.u / art.facePx * sh.zoom
            val alpha = (sh.lifeMs / 320f).coerceIn(0f, 1f)
            withTransform({
                translate(g.cx + sh.x * g.u, g.feetY - sh.y * g.u)
                rotate(sh.rot, Offset.Zero)
                scale(k, k, Offset.Zero)
            }) {
                drawImage(
                    art.face,
                    srcOffset = IntOffset(sh.col * tw, sh.row * th), srcSize = IntSize(tw, th),
                    dstOffset = IntOffset(-tw / 2, -th / 2), dstSize = IntSize(tw, th),
                    alpha = alpha,
                )
            }
        }
    }

    private fun DrawScope.ring(g: AisleGeom, fx: StageFx) {
        val p = fx.ring
        if (p < 0f) return
        val c = Offset(g.cx, g.feetY - 2.3f * g.u)
        val color = if (fx.ringPerfect) FIT_ON else LibriColors.Amber
        drawCircle(color.copy(alpha = 0.85f * (1f - p)), (0.8f + 2.8f * p) * g.u, c, style = Stroke(0.12f * g.u * (1f - p) + 2f))
    }

    private fun DrawScope.confetti(g: AisleGeom, fx: StageFx) {
        for (p in fx.confetti) {
            val sz = p.size * g.u
            val hh = sz * (0.3f + 0.7f * abs(cos(p.flip)))     // 팔랑거리며 뒤집힌다
            val alpha = (p.lifeMs / 350f).coerceIn(0f, 1f)
            withTransform({
                translate(g.cx + p.x * g.u, g.feetY - p.y * g.u)
                rotate(p.rot, Offset.Zero)
            }) {
                drawRect(CONFETTI[p.color % CONFETTI.size], Offset(-sz / 2f, -hh / 2f), Size(sz, hh), alpha = alpha)
            }
        }
    }
}
