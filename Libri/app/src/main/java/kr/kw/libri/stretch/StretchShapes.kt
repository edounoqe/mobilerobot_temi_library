package kr.kw.libri.stretch

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 크리에이티브 코스 — 벽에 '자세' 대신 '도형' 구멍이 뚫려 있다.
 * 정해진 자세는 없다. 어떤 자세든 몸이 구멍 가장자리에 닿지 않으면 통과하고, 구멍을 넓게 채울수록 점수가 높다.
 *
 *  · 좌표는 벽과 같다: x 는 가운데가 0 (오른쪽 +), y 는 바닥이 0 (위쪽 +), 단위는 몸통 길이.
 *  · 마네킹은 늘 가운데에 서 있으므로(각도로만 자세를 나타낸다) 모든 도형은 가운데에 다리가 지나갈 기둥이 있고,
 *    손이 내려오는 높이에서는 기둥이 좁아진다 — 차렷 자세로는 지나갈 수 없다.
 *  · 팔을 몸 앞으로 모아 좁게 서면 대부분 지나갈 수는 있지만, 구멍을 채우지 못해 '좋아요' 까지만 받는다.
 */

/** 벽에 뚫린 도형 구멍 — 꼭짓점을 차례로 이은 다각형 (x0, y0, x1, y1, …) */
class HoleShape(val pts: FloatArray) {
    val count: Int = pts.size / 2

    /** 구멍 가장자리까지의 여유 — 구멍 안쪽이면 +거리, 바깥이면 -거리 */
    fun depth(px: Float, py: Float): Float {
        var inside = false
        var best = Float.MAX_VALUE
        var j = count - 1
        for (i in 0 until count) {
            val xi = pts[2 * i]
            val yi = pts[2 * i + 1]
            val xj = pts[2 * j]
            val yj = pts[2 * j + 1]
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside
            val dx = xj - xi
            val dy = yj - yi
            val len2 = dx * dx + dy * dy
            val t = if (len2 > 0f) (((px - xi) * dx + (py - yi) * dy) / len2).coerceIn(0f, 1f) else 0f
            val ex = xi + t * dx - px
            val ey = yi + t * dy - py
            val d2 = ex * ex + ey * ey
            if (d2 < best) best = d2
            j = i
        }
        val d = sqrt(best)
        return if (inside) d else -d
    }

    // 구멍을 얼마나 채웠는지 재는 눈금 — 다리 기둥 위쪽의 구멍 안 점들
    private val cells: FloatArray by lazy {
        val out = ArrayList<Float>()
        var y = FILL_FROM_Y
        while (y < WALL_H) {
            var x = -WALL_W / 2f
            while (x < WALL_W / 2f) {
                if (depth(x, y) > 0f) {
                    out += x
                    out += y
                }
                x += FILL_STEP
            }
            y += FILL_STEP
        }
        out.toFloatArray()
    }

    // 계산용 임시 값 — 게임 루프(한 스레드)에서만 쓴다
    private val fig = Figure()
    private val bodyX = FloatArray(BODY_POINTS)
    private val bodyY = FloatArray(BODY_POINTS)
    private var bodyN = 0

    private fun point(x: Float, y: Float, r: Float): Float {
        if (bodyN < BODY_POINTS) {
            bodyX[bodyN] = x
            bodyY[bodyN] = y
            bodyN++
        }
        // 가장자리에 닿지 않으면 1, TOUCH_TOL 넘게 파고들면 0
        return 1f - smoothstep(0f, TOUCH_TOL, r - depth(x, y))
    }

    /** 관절 a → b 를 따라 몇 군데를 찍어, 가장 많이 걸린 곳의 점수를 돌려준다 */
    private fun limb(ax: Float, ay: Float, bx: Float, by: Float, r: Float, steps: Int): Float {
        var worst = 1f
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val s = point(ax + (bx - ax) * t, ay + (by - ay) * t, r)
            if (s < worst) worst = s
        }
        return worst
    }

    private fun bone(b: Bone): Float {
        val f = fig
        fun seg(from: Int, to: Int, r: Float, steps: Int) = limb(f.x[from], f.y[from], f.x[to], f.y[to], r, steps)
        return when (b) {
            Bone.SPINE -> limb(
                (f.x[Joint.HIP_L] + f.x[Joint.HIP_R]) / 2f, (f.y[Joint.HIP_L] + f.y[Joint.HIP_R]) / 2f,
                f.x[Joint.NECK], f.y[Joint.NECK], TORSO_R, 3,
            )
            Bone.NECK -> point(f.x[Joint.HEAD], f.y[Joint.HEAD], HEAD_TOUCH_R)
            Bone.L_UPPER_ARM -> seg(Joint.SHO_L, Joint.ELB_L, LIMB_R, 4)
            Bone.L_FOREARM -> seg(Joint.ELB_L, Joint.WRI_L, LIMB_R, 4)
            Bone.R_UPPER_ARM -> seg(Joint.SHO_R, Joint.ELB_R, LIMB_R, 4)
            Bone.R_FOREARM -> seg(Joint.ELB_R, Joint.WRI_R, LIMB_R, 4)
            Bone.L_THIGH -> seg(Joint.HIP_L, Joint.KNE_L, LIMB_R, 2)
            Bone.L_SHIN -> seg(Joint.KNE_L, Joint.ANK_L, LIMB_R, 2)
            Bone.R_THIGH -> seg(Joint.HIP_R, Joint.KNE_R, LIMB_R, 2)
            Bone.R_SHIN -> seg(Joint.KNE_R, Joint.ANK_R, LIMB_R, 2)
        }
    }

    /** 몸이 구멍 가장자리에 닿지 않는 정도 0~1 (뼈대별 점수는 boneOut 에) — 상체 점들을 bodyX·bodyY 에 남긴다 */
    private fun clear(player: BodyPose, legsTracked: Boolean, boneOut: FloatArray?): Float {
        player.layout(fig)
        bodyN = 0
        var sum = 0f
        var n = 0
        var worst = 1f
        for (i in 0 until BONE_COUNT) {
            val b = BONES[i]
            if (b.isLeg) continue
            val s = bone(b)
            boneOut?.set(i, s)
            sum += s
            n++
            if (s < worst) worst = s
        }
        val upper = bodyN
        for (i in 0 until BONE_COUNT) {
            val b = BONES[i]
            if (!b.isLeg) continue
            if (!legsTracked) {
                boneOut?.set(i, -1f)
                continue
            }
            // 다리는 늘 기둥 안에 있어 평균을 부풀리므로 평균에는 넣지 않고, 걸렸는지만 본다
            val s = bone(b)
            boneOut?.set(i, s)
            if (s < worst) worst = s
        }
        bodyN = upper
        // 구멍은 온몸이 다 들어가야 지나간다 — 한 군데라도 크게 걸리면 통과하지 못한다
        return 0.5f * (sum / n) + 0.5f * worst
    }

    /** 상체가 구멍을 덮은 비율 0~1 — clear 를 부른 직후에 쓴다 */
    private fun coverage(): Float {
        val c = cells
        val total = c.size / 2
        if (total == 0) return 0f
        var hit = 0
        for (k in 0 until total) {
            val x = c[2 * k]
            val y = c[2 * k + 1]
            for (i in 0 until bodyN) {
                val dx = bodyX[i] - x
                val dy = bodyY[i] - y
                if (dx * dx + dy * dy <= FILL_REACH * FILL_REACH) {
                    hit++
                    break
                }
            }
        }
        return hit / total.toFloat()
    }

    private var fullCoverage = -1f

    /**
     * 구멍에 얼마나 잘 들어갔는지 0~1.
     *  · 가장자리에 닿지 않으면 통과('좋아요') — 구멍을 예시 자세만큼 넓게 채우면 1 에 가까워진다('완벽').
     * @param example 이 구멍을 넓게 채우는 예시 자세 (채움의 기준)
     */
    fun fit(player: BodyPose, example: BodyPose, legsTracked: Boolean, boneOut: FloatArray?): Float {
        if (fullCoverage < 0f) {
            clear(example, false, null)
            fullCoverage = coverage().coerceAtLeast(0.01f)
        }
        val c = clear(player, legsTracked, boneOut)
        val fill = (coverage() / fullCoverage).coerceIn(0f, 1f)
        return c * (FILL_BASE + (1f - FILL_BASE) * smoothstep(FILL_LOW, FILL_HIGH, fill))
    }

    private companion object {
        const val LIMB_R = 0.08f            // 팔다리 굵기의 절반 (그림보다 조금 가늘게 — 살짝 스치는 것은 봐준다)
        const val TORSO_R = 0.17f
        const val HEAD_TOUCH_R = 0.18f
        const val TOUCH_TOL = 0.22f
        const val BODY_POINTS = 40
        const val FILL_FROM_Y = 1.25f       // 이 높이부터 채움을 잰다 (그 아래는 다리 기둥)
        const val FILL_STEP = 0.2f
        const val FILL_REACH = 0.34f        // 몸의 한 점이 덮는 반지름
        const val FILL_BASE = 0.72f         // 닿지만 않았을 때의 점수 — '좋아요' 구간
        const val FILL_LOW = 0.62f
        const val FILL_HIGH = 0.95f
    }
}

// ───────── 도형 ─────────

private const val BELOW = -0.6f     // 바닥 아래까지 뚫어 둔다 (발이 바닥 선에 걸리지 않게)
private const val LEG_HALF = 0.8f   // 다리 기둥의 반 너비
private const val SLIM_HALF = 0.42f // 손이 내려오는 높이의 반 너비 — 차렷한 팔이 걸린다
private const val TOP = 4.05f
private const val SIDE = 2.05f

/** 오른쪽 절반의 꼭짓점(아래 → 위)을 받아 좌우 대칭 도형을 만든다 */
private fun symmetric(vararg right: Float): HoleShape {
    val n = right.size / 2
    val pts = FloatArray(right.size * 2)
    for (i in 0 until n) {
        pts[2 * i] = right[2 * i]
        pts[2 * i + 1] = right[2 * i + 1]
        val m = 2 * n - 1 - i                    // 왼쪽은 위 → 아래로 되짚는다
        pts[2 * m] = -right[2 * i]
        pts[2 * m + 1] = right[2 * i + 1]
    }
    return HoleShape(pts)
}

private fun mirrored(s: HoleShape): HoleShape {
    val pts = FloatArray(s.pts.size)
    for (i in 0 until s.count) {
        val m = s.count - 1 - i
        pts[2 * m] = -s.pts[2 * i]
        pts[2 * m + 1] = s.pts[2 * i + 1]
    }
    return HoleShape(pts)
}

private val V_SHAPE = symmetric(
    LEG_HALF, BELOW, LEG_HALF, 0.8f, SLIM_HALF, 1.15f, SLIM_HALF, 1.75f, SIDE, TOP,
)

private val T_SHAPE = symmetric(
    LEG_HALF, BELOW, LEG_HALF, 0.8f, SLIM_HALF, 1.15f, SLIM_HALF, 2.15f, SIDE, 2.15f, SIDE, 3.3f, 0.55f, 3.3f, 0.55f, 3.65f,
)

/** 동그란 창 — 가슴 높이의 원에 다리 기둥이 달렸다 */
private val ROUND_SHAPE: HoleShape = run {
    val cy = 2.85f
    val r = 1.2f
    val right = ArrayList<Float>()
    right += listOf(LEG_HALF, BELOW, LEG_HALF, 0.8f, SLIM_HALF, 1.15f)
    var a = -69.5
    while (a <= 90.0) {
        val rad = Math.toRadians(a)
        right += (cos(rad) * r).toFloat().coerceAtLeast(0.001f)
        right += cy + (sin(rad) * r).toFloat()
        a += 11.4
    }
    symmetric(*right.toFloatArray())
}

/** 오른쪽으로 기울어진 틈 — 몸을 오른쪽으로 기울여야 왼쪽 어깨가 걸리지 않는다 */
private val LEAN_RIGHT_SHAPE = HoleShape(
    floatArrayOf(
        -LEG_HALF, BELOW, -LEG_HALF, 0.8f, -SLIM_HALF, 1.15f, -SLIM_HALF, 2.05f, 0.2f, TOP,
        SIDE, TOP, SIDE, 2.6f, SLIM_HALF, 1.5f, SLIM_HALF, 1.15f, LEG_HALF, 0.8f, LEG_HALF, BELOW,
    ),
)

/** 기역 구멍 — 왼쪽은 위로 솟은 굴뚝, 오른쪽은 어깨 높이의 가로 틈 */
private val CORNER_SHAPE = HoleShape(
    floatArrayOf(
        -LEG_HALF, BELOW, -LEG_HALF, 0.8f, -SLIM_HALF, 1.15f, -SLIM_HALF, 2.15f, -0.9f, 2.15f, -0.9f, TOP,
        0.55f, TOP, 0.55f, 3.3f, SIDE, 3.3f, SIDE, 2.15f, SLIM_HALF, 2.15f, SLIM_HALF, 1.15f, LEG_HALF, 0.8f, LEG_HALF, BELOW,
    ),
)

// ───────── 어려운 도형 — 몸에 꼭 맞게 좁다. 크리에이티브 서바이벌 뒤쪽에 나온다 ─────────

/** 좁은 굴뚝 — 허리 높이는 몸통만 지나갈 만큼, 위로는 두 팔을 딱 붙여야 지나간다 */
private val CHIMNEY_SHAPE = symmetric(
    LEG_HALF, BELOW, LEG_HALF, 0.8f, 0.3f, 1.15f, 0.3f, 2.2f, 0.54f, 2.35f, 0.54f, TOP,
)

/** 대각선 — 왼팔은 왼쪽 위로, 오른팔은 오른쪽 아래로 뻗은 '디스코' 자세 모양 */
private val DISCO_SHAPE = HoleShape(
    floatArrayOf(
        -LEG_HALF, BELOW, -LEG_HALF, 0.8f, -SLIM_HALF, 1.15f, -SLIM_HALF, 2.24f,
        -1.55f, 3.37f, -1.13f, 3.79f, -0.40f, 3.06f, -0.40f, 3.48f, 0.40f, 3.48f, 0.40f, 2.95f,
        0.60f, 2.80f, 1.48f, 1.75f, 1.02f, 1.37f, SLIM_HALF, 2.085f,
        SLIM_HALF, 1.15f, LEG_HALF, 0.8f, LEG_HALF, BELOW,
    ),
)

/** 크게 기운 틈 — 몸을 오른쪽으로 깊게 기울이고 두 팔도 그 방향으로 뻗어야 한다 */
private val STEEP_RIGHT_SHAPE = HoleShape(
    floatArrayOf(
        -LEG_HALF, BELOW, -LEG_HALF, 0.8f, -SLIM_HALF, 1.15f, -SLIM_HALF, 1.6f, -0.546f, 1.86f,
        0.48f, 4.1f, 1.58f, 3.6f, 0.546f, 1.36f,
        SLIM_HALF, 1.15f, LEG_HALF, 0.8f, LEG_HALF, BELOW,
    ),
)

private const val FREE = "정해진 자세는 없어요"

object StretchShapes {
    val VEE = make(
        "shape_v", "브이 구멍", "아래는 좁아요 — 팔을 위로!",
        left = Arm(150f, 150f), right = Arm(150f, 150f), shape = V_SHAPE,
    )
    val BAR = make(
        "shape_t", "가로 틈", "어깨 높이로 넓게 — 팔은 마음대로",
        left = Arm(90f, 90f), right = Arm(90f, 90f), shape = T_SHAPE,
    )
    val ROUND = make(
        "shape_round", "동그란 창", "팔을 접어 동그라미 안에 쏙",
        left = Arm(90f, 185f), right = Arm(90f, 185f), shape = ROUND_SHAPE,
    )
    val LEAN_RIGHT = make(
        "shape_lean_right", "기울어진 틈", "몸을 오른쪽으로 기울여 비스듬히",
        left = Arm(190f, 205f), right = Arm(100f, 120f), lean = 15f, shape = LEAN_RIGHT_SHAPE,
    )
    val LEAN_LEFT = make(
        "shape_lean_left", "기울어진 틈", "이번엔 왼쪽으로 비스듬히",
        left = Arm(100f, 120f), right = Arm(190f, 205f), lean = -15f, shape = mirrored(LEAN_RIGHT_SHAPE),
    )
    val CORNER_LEFT = make(
        "shape_corner_left", "기역 구멍", "한 팔은 위로, 한 팔은 옆으로",
        left = Arm(180f, 180f), right = Arm(90f, 90f), shape = CORNER_SHAPE,
    )
    val CORNER_RIGHT = make(
        "shape_corner_right", "기역 구멍", "이번엔 반대로!",
        left = Arm(90f, 90f), right = Arm(180f, 180f), shape = mirrored(CORNER_SHAPE),
    )

    val CHIMNEY = make(
        "shape_chimney", "좁은 굴뚝", "두 팔을 머리 위로 딱 붙여요",
        left = Arm(180f, 205f), right = Arm(180f, 205f), shape = CHIMNEY_SHAPE,
    )
    val DISCO_LEFT = make(
        "shape_disco_left", "대각선 틈", "왼팔은 위로, 오른팔은 아래로 비스듬히",
        left = Arm(135f, 135f), right = Arm(40f, 40f), shape = DISCO_SHAPE,
    )
    val DISCO_RIGHT = make(
        "shape_disco_right", "대각선 틈", "이번엔 반대로 비스듬히!",
        left = Arm(40f, 40f), right = Arm(135f, 135f), shape = mirrored(DISCO_SHAPE),
    )
    val STEEP_RIGHT = make(
        "shape_steep_right", "크게 기운 틈", "몸을 오른쪽으로 크게 기울여요",
        left = Arm(180f, 180f), right = Arm(180f, 180f), lean = 25f, shape = STEEP_RIGHT_SHAPE,
        focus = mapOf(Bone.SPINE to 3f),
    )
    val STEEP_LEFT = make(
        "shape_steep_left", "크게 기운 틈", "이번엔 왼쪽으로 크게!",
        left = Arm(180f, 180f), right = Arm(180f, 180f), lean = -25f, shape = mirrored(STEEP_RIGHT_SHAPE),
        focus = mapOf(Bone.SPINE to 3f),
    )

    /** 처음 나오는 쉬운 도형 */
    val EASY = listOf(VEE, BAR, ROUND, LEAN_RIGHT, LEAN_LEFT, CORNER_LEFT, CORNER_RIGHT)
    /** 몸에 꼭 맞는 어려운 도형 */
    val HARD = listOf(CHIMNEY, DISCO_LEFT, DISCO_RIGHT, STEEP_RIGHT, STEEP_LEFT)
    val ALL = EASY + HARD

    /** 화면에 덧붙이는 한마디 */
    const val HINT = "$FREE. 구멍에 닿지 않게, 넓게 채울수록 '완벽'!"
}
