package kr.kw.libri.stretch

import kotlin.math.abs

/**
 * 벽 하나에 뚫린 구멍 = 스트레칭 동작 하나.
 *  pose    : 목표 자세 (도형 구멍이면 그 구멍을 넓게 채우는 예시 자세 — 튜토리얼과 채움의 기준으로 쓴다)
 *  weights : 뼈대별 판정 가중치 (0 이면 판정하지 않는다)
 *  shape   : 도형 구멍 (크리에이티브 코스). 있으면 자세가 아니라 '구멍에 몸이 들어갔는지' 로 판정한다
 */
class StretchPose(
    val id: String,
    val name: String,
    val cue: String,
    val pose: BodyPose,
    val weights: FloatArray,
    val shape: HoleShape? = null,
) {
    /**
     * 사용자 자세가 목표와 얼마나 맞는지 0~1.
     * @param legsTracked 다리가 카메라에 보이는가 — 안 보이면 다리는 판정에서 뺀다
     * @param boneOut     뼈대별 점수를 받아 갈 배열 (판정하지 않는 뼈는 -1)
     */
    fun fit(player: BodyPose, legsTracked: Boolean, boneOut: FloatArray? = null): Float {
        if (shape != null) return shape.fit(player, pose, legsTracked, boneOut)
        var sum = 0f
        var wsum = 0f
        var worst = 1f
        for (i in 0 until BONE_COUNT) {
            val b = BONES[i]
            val w = if (b.isLeg && !legsTracked) 0f else weights[i]
            if (w <= 0f) { boneOut?.set(i, -1f); continue }
            val d = abs(angleDiff(player.a[i], pose.a[i]))
            val s = 1f - smoothstep(b.tolIn, b.tolOut, d)
            boneOut?.set(i, s)
            sum += w * s
            wsum += w
            if (!b.isLeg && s < worst) worst = s
        }
        if (wsum <= 0f) return 0f
        // 평균만 쓰면 팔 하나가 완전히 틀려도 통과한다 — 구멍은 온몸이 다 맞아야 지나가므로 가장 안 맞는 뼈를 함께 본다.
        // 다리는 여기서 뺀다: 앉아서 하거나 다리가 일부만 보여도 상체가 맞으면 통과할 수 있어야 한다 (맞추면 점수만 더 좋다)
        return MEAN_SHARE * (sum / wsum) + (1f - MEAN_SHARE) * worst
    }

    private companion object {
        const val MEAN_SHARE = 0.7f
    }
}

/** 코스 — 벽이 나오는 순서와 간격(처음 → 마지막으로 갈수록 조금씩 빨라진다) */
class StretchCourse(
    val id: String,
    val title: String,
    val desc: String,
    val poses: List<StretchPose>,
    val firstIntervalMs: Float,
    val lastIntervalMs: Float,
) {
    fun intervalMs(index: Int): Float {
        if (poses.size <= 1) return firstIntervalMs
        val t = index / (poses.size - 1f)
        return firstIntervalMs + (lastIntervalMs - firstIntervalMs) * t
    }
}

/*
 * 동작 정의 — 팔은 '몸통 기준 벌린 각'으로 적는다: 0 = 내림, 90 = 옆으로, 180 = 위로
 * (180 을 넘으면 머리 위를 지나 반대쪽으로, 음수면 몸 안쪽으로)
 * 몸을 기울이는 동작(lean)은 팔도 몸통과 함께 기운다.
 */
internal class Arm(val upper: Float, val fore: Float)

private val ON_HIP = Arm(38f, -40f)   // 손 허리

internal fun make(
    id: String,
    name: String,
    cue: String,
    left: Arm,
    right: Arm,
    lean: Float = 0f,          // 몸통 기울기 (+ = 화면 오른쪽)
    neckTilt: Float = 0f,      // 몸통 기준 고개 기울기
    legSpread: Float = 0f,     // 다리 벌림
    focus: Map<Bone, Float> = emptyMap(),   // 이 동작에서 가중치를 바꿀 뼈 (목·옆구리처럼 핵심인 부위)
    shape: HoleShape? = null,               // 도형 구멍 (크리에이티브 코스)
): StretchPose {
    val p = BodyPose()
    p[Bone.SPINE] = lean
    p[Bone.NECK] = lean + neckTilt
    p[Bone.L_UPPER_ARM] = left.upper - 180f + lean
    p[Bone.L_FOREARM] = left.fore - 180f + lean
    p[Bone.R_UPPER_ARM] = 180f - right.upper + lean
    p[Bone.R_FOREARM] = 180f - right.fore + lean
    p[Bone.L_THIGH] = legSpread - 180f
    p[Bone.L_SHIN] = legSpread - 180f
    p[Bone.R_THIGH] = 180f - legSpread
    p[Bone.R_SHIN] = 180f - legSpread
    val weights = FloatArray(BONE_COUNT) { focus[BONES[it]] ?: BONES[it].weight }
    return StretchPose(id, name, cue, p, weights, shape)
}

object StretchPoses {
    val REACH = make(
        "reach", "기지개 켜기", "두 손을 머리 위로 모아 쭉 뻗어요",
        left = Arm(180f, 205f), right = Arm(180f, 205f),
    )
    val WIDE = make(
        "wide", "양팔 벌리기", "양팔을 옆으로 길게 펴요",
        left = Arm(90f, 90f), right = Arm(90f, 90f),
    )
    val CACTUS = make(
        "cactus", "선인장 자세", "팔꿈치를 직각으로, 가슴을 활짝",
        left = Arm(90f, 180f), right = Arm(90f, 180f),
    )
    val SIDE_LEFT = make(
        "side_left", "왼쪽으로 기울이기", "오른팔을 머리 위로, 몸을 왼쪽으로",
        left = ON_HIP, right = Arm(190f, 215f), lean = -17f,
        focus = mapOf(Bone.SPINE to 3.6f),
    )
    val SIDE_RIGHT = make(
        "side_right", "오른쪽으로 기울이기", "왼팔을 머리 위로, 몸을 오른쪽으로",
        left = Arm(190f, 215f), right = ON_HIP, lean = 17f,
        focus = mapOf(Bone.SPINE to 3.6f),
    )
    val HEAD_HANDS = make(
        "head_hands", "머리 뒤 깍지", "양손을 머리 뒤로, 팔꿈치는 활짝",
        left = Arm(118f, 254f), right = Arm(118f, 254f),
        // 손이 머리 뒤에 가려 손목 위치가 부정확하게 읽힐 수 있다 — 아래팔 비중을 낮춘다
        focus = mapOf(Bone.L_FOREARM to 0.5f, Bone.R_FOREARM to 0.5f),
    )
    val NECK_LEFT = make(
        "neck_left", "목 늘리기 (왼쪽)", "손은 허리에, 고개를 왼쪽으로 지그시",
        left = ON_HIP, right = ON_HIP, neckTilt = -24f,
        focus = mapOf(Bone.NECK to 5f),
    )
    val NECK_RIGHT = make(
        "neck_right", "목 늘리기 (오른쪽)", "손은 허리에, 고개를 오른쪽으로 지그시",
        left = ON_HIP, right = ON_HIP, neckTilt = 24f,
        focus = mapOf(Bone.NECK to 5f),
    )
    val PLANE_LEFT = make(
        "plane_left", "비행기 (왼쪽)", "양팔을 편 채 왼쪽으로 기우뚱",
        left = Arm(72f, 72f), right = Arm(108f, 108f), lean = -12f,
        focus = mapOf(Bone.SPINE to 2.4f),
    )
    val PLANE_RIGHT = make(
        "plane_right", "비행기 (오른쪽)", "양팔을 편 채 오른쪽으로 기우뚱",
        left = Arm(108f, 108f), right = Arm(72f, 72f), lean = 12f,
        focus = mapOf(Bone.SPINE to 2.4f),
    )
    val STAR = make(
        "star", "큰 별 자세", "팔다리를 크게 벌려 별 모양!",
        left = Arm(140f, 140f), right = Arm(140f, 140f), legSpread = 20f,
        focus = mapOf(Bone.L_THIGH to 0.8f, Bone.R_THIGH to 0.8f, Bone.L_SHIN to 0.5f, Bone.R_SHIN to 0.5f),
    )

    val ALL = listOf(
        REACH, WIDE, CACTUS, SIDE_LEFT, SIDE_RIGHT, HEAD_HANDS,
        NECK_LEFT, NECK_RIGHT, PLANE_LEFT, PLANE_RIGHT, STAR,
    )
}

object StretchCourses {
    val LIGHT = StretchCourse(
        "light", "가볍게", "6동작 · 약 40초",
        listOf(
            StretchPoses.REACH, StretchPoses.WIDE, StretchPoses.CACTUS,
            StretchPoses.SIDE_LEFT, StretchPoses.SIDE_RIGHT, StretchPoses.STAR,
        ),
        firstIntervalMs = 6500f, lastIntervalMs = 5400f,
    )
    val FULL = StretchCourse(
        "full", "제대로", "10동작 · 약 1분",
        listOf(
            StretchPoses.REACH, StretchPoses.WIDE, StretchPoses.SIDE_LEFT, StretchPoses.SIDE_RIGHT,
            StretchPoses.HEAD_HANDS, StretchPoses.NECK_LEFT, StretchPoses.NECK_RIGHT,
            StretchPoses.PLANE_LEFT, StretchPoses.PLANE_RIGHT, StretchPoses.STAR,
        ),
        firstIntervalMs = 6000f, lastIntervalMs = 4600f,
    )
    /** 자세 대신 도형 구멍 — 어떤 자세든 구멍에 들어가기만 하면 된다. 생각할 시간을 조금 더 준다 */
    val CREATIVE = StretchCourse(
        "creative", "크리에이티브", "도형 7개 · 자유 자세",
        StretchShapes.ALL,
        firstIntervalMs = 7000f, lastIntervalMs = 5800f,
    )
    val ALL = listOf(LIGHT, FULL, CREATIVE)
}
