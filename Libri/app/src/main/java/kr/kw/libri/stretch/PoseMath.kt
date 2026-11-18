package kr.kw.libri.stretch

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * 스트레칭 게임의 자세 표현 — 관절 좌표가 아니라 '뼈대 방향(각도)'으로 자세를 나타낸다.
 *  · 카메라와의 거리, 서 있는 위치, 체형에 상관없이 비교할 수 있다.
 *  · 아바타와 벽의 구멍을 같은 각도·같은 마네킹으로 그리므로, 각도가 맞으면 화면에서도 구멍에 딱 맞는다.
 *
 * 각도 규약: 0° = 위, 90° = 화면 오른쪽, 180° = 아래, -90° = 화면 왼쪽 (시계 방향이 +)
 * L / R 은 '화면 기준' 왼쪽 / 오른쪽이다. 아바타는 거울처럼 움직이므로 사용자의 왼팔이 화면 왼쪽 팔이다.
 */

/**
 * 뼈대.
 *  weight : 판정 가중치 기본값 (0 이면 기본으로는 판정하지 않고, 동작이 따로 켠다)
 *  tolIn  : 이 각도(°) 이내로 맞으면 만점
 *  tolOut : 이 각도(°) 이상 벗어나면 0점
 */
enum class Bone(val weight: Float, val tolIn: Float, val tolOut: Float) {
    SPINE(1.2f, 6f, 20f),
    NECK(0f, 8f, 24f),
    L_UPPER_ARM(1f, 14f, 42f),
    L_FOREARM(1f, 16f, 46f),
    R_UPPER_ARM(1f, 14f, 42f),
    R_FOREARM(1f, 16f, 46f),
    L_THIGH(0f, 12f, 36f),
    L_SHIN(0f, 14f, 40f),
    R_THIGH(0f, 12f, 36f),
    R_SHIN(0f, 14f, 40f);

    val isLeg: Boolean get() = ordinal >= L_THIGH.ordinal
}

internal val BONES: Array<Bone> = Bone.entries.toTypedArray()
internal val BONE_COUNT: Int = BONES.size

/** 각도를 (-180, 180] 범위로 */
fun normDeg(a: Float): Float {
    var x = a % 360f
    if (x > 180f) x -= 360f
    if (x <= -180f) x += 360f
    return x
}

/** a - b 를 최단 호로 (-180, 180] */
fun angleDiff(a: Float, b: Float): Float = normDeg(a - b)

/** 방향 벡터(오른쪽 dx, 위쪽 dyUp) → 각도 규약의 각도(°) */
fun dirDeg(dx: Float, dyUp: Float): Float = Math.toDegrees(atan2(dx.toDouble(), dyUp.toDouble())).toFloat()

fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 자세 = 뼈대마다의 각도(°). 값은 바뀔 수 있다 (프레임마다 새로 만들지 않기 위해) */
class BodyPose(val a: FloatArray = FloatArray(BONE_COUNT)) {

    operator fun get(b: Bone): Float = a[b.ordinal]
    operator fun set(b: Bone, deg: Float) { a[b.ordinal] = normDeg(deg) }

    fun set(o: BodyPose) { o.a.copyInto(a) }
    fun copy(): BodyPose = BodyPose(a.copyOf())

    /** 모든 뼈를 target 쪽으로 최단 호를 따라 k(0~1) 만큼 옮긴다 */
    fun chase(target: BodyPose, k: Float) {
        for (i in a.indices) a[i] = normDeg(a[i] + angleDiff(target.a[i], a[i]) * k)
    }

    companion object {
        /** 차렷 — 팔을 살짝 벌리고 내린 자세 */
        fun rest(): BodyPose = BodyPose().apply {
            this[Bone.L_UPPER_ARM] = -172f; this[Bone.L_FOREARM] = -176f
            this[Bone.R_UPPER_ARM] = 172f; this[Bone.R_FOREARM] = 176f
            this[Bone.L_THIGH] = 180f; this[Bone.L_SHIN] = 180f
            this[Bone.R_THIGH] = 180f; this[Bone.R_SHIN] = 180f
        }
    }
}

/** 마네킹 치수 — 단위는 '몸통 길이(골반~어깨) = 1' */
object Body {
    const val TORSO = 1.0f
    const val SHOULDER_HALF = 0.37f
    const val HIP_HALF = 0.19f
    const val UPPER_ARM = 0.56f
    const val FOREARM = 0.56f      // 손목까지 + 손바닥 일부
    const val HAND_R = 0.105f
    const val NECK = 0.42f         // 어깨 중심 ~ 머리 중심
    const val HEAD_R = 0.21f
    const val THIGH = 0.78f
    const val SHIN = 0.75f
    const val ANKLE_H = 0.08f      // 발목 높이
    const val FOOT = 0.17f
}

/** 벽 크기와 구멍 여유 (같은 단위) */
const val WALL_W = 4.6f
const val WALL_H = 4.3f
const val HOLE_MARGIN = 0.11f

object Joint {
    const val HEAD = 0
    const val NECK = 1      // 어깨 중심
    const val SHO_L = 2
    const val SHO_R = 3
    const val ELB_L = 4
    const val ELB_R = 5
    const val WRI_L = 6
    const val WRI_R = 7
    const val HIP_L = 8
    const val HIP_R = 9
    const val KNE_L = 10
    const val KNE_R = 11
    const val ANK_L = 12
    const val ANK_R = 13
    const val TOE_L = 14
    const val TOE_R = 15
    const val COUNT = 16
}

/** 마네킹 관절 좌표 — x: 가운데가 0 (오른쪽 +), y: 바닥이 0 (위쪽 +) */
class Figure {
    val x = FloatArray(Joint.COUNT)
    val y = FloatArray(Joint.COUNT)
}

/** 각도로 표현된 자세를 마네킹 관절 좌표로 펼친다. 낮은 쪽 발이 바닥에 닿게 세운다 */
fun BodyPose.layout(f: Figure) {
    fun put(j: Int, px: Float, py: Float) { f.x[j] = px; f.y[j] = py }
    fun chain(from: Int, to: Int, len: Float, deg: Float) {
        val r = Math.toRadians(deg.toDouble())
        put(to, f.x[from] + len * sin(r).toFloat(), f.y[from] + len * cos(r).toFloat())
    }

    // 골반을 원점에 두고 쌓는다
    put(Joint.HIP_L, -Body.HIP_HALF, 0f)
    put(Joint.HIP_R, Body.HIP_HALF, 0f)
    chain(Joint.HIP_L, Joint.KNE_L, Body.THIGH, this[Bone.L_THIGH])
    chain(Joint.KNE_L, Joint.ANK_L, Body.SHIN, this[Bone.L_SHIN])
    chain(Joint.HIP_R, Joint.KNE_R, Body.THIGH, this[Bone.R_THIGH])
    chain(Joint.KNE_R, Joint.ANK_R, Body.SHIN, this[Bone.R_SHIN])
    put(Joint.TOE_L, f.x[Joint.ANK_L] - Body.FOOT, f.y[Joint.ANK_L] - 0.03f)
    put(Joint.TOE_R, f.x[Joint.ANK_R] + Body.FOOT, f.y[Joint.ANK_R] - 0.03f)

    val spine = Math.toRadians(this[Bone.SPINE].toDouble())
    val ux = sin(spine).toFloat()
    val uy = cos(spine).toFloat()                 // 몸통이 향하는 방향
    put(Joint.NECK, Body.TORSO * ux, Body.TORSO * uy)
    // 어깨선은 몸통에 수직 (몸통이 위를 향하면 오른쪽 = +x)
    put(Joint.SHO_L, f.x[Joint.NECK] - Body.SHOULDER_HALF * uy, f.y[Joint.NECK] + Body.SHOULDER_HALF * ux)
    put(Joint.SHO_R, f.x[Joint.NECK] + Body.SHOULDER_HALF * uy, f.y[Joint.NECK] - Body.SHOULDER_HALF * ux)
    chain(Joint.NECK, Joint.HEAD, Body.NECK, this[Bone.NECK])
    chain(Joint.SHO_L, Joint.ELB_L, Body.UPPER_ARM, this[Bone.L_UPPER_ARM])
    chain(Joint.ELB_L, Joint.WRI_L, Body.FOREARM, this[Bone.L_FOREARM])
    chain(Joint.SHO_R, Joint.ELB_R, Body.UPPER_ARM, this[Bone.R_UPPER_ARM])
    chain(Joint.ELB_R, Joint.WRI_R, Body.FOREARM, this[Bone.R_FOREARM])

    // 낮은 쪽 발목이 발목 높이에 오도록 전체를 올린다
    val lift = Body.ANKLE_H - min(f.y[Joint.ANK_L], f.y[Joint.ANK_R])
    for (j in 0 until Joint.COUNT) f.y[j] += lift
}
