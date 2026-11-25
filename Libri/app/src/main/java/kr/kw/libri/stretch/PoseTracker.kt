package kr.kw.libri.stretch

import kotlin.math.hypot

/** ML Kit 포즈 랜드마크 번호 (PoseLandmark 의 상수와 같다). 왼쪽·오른쪽 짝은 항상 '왼쪽 + 1 = 오른쪽' */
object Lm {
    const val NOSE = 0
    const val L_EAR = 7
    const val L_SHOULDER = 11
    const val L_ELBOW = 13
    const val L_WRIST = 15
    const val L_HIP = 23
    const val L_KNEE = 25
    const val L_ANKLE = 27
    const val COUNT = 33
}

/** 카메라 한 프레임에서 읽은 자세 */
class BodyObservation {
    val pose: BodyPose = BodyPose.rest()
    var legsTracked = false
}

/** 랜드마크 좌표 → 뼈대 각도. 카메라·ML Kit 과 분리해 두어 단위 테스트로 검증한다 */
object PoseTracker {
    private const val SEEN = 0.5f   // 이 값 이상이면 화면 안에 보이는 관절로 본다

    /**
     * @param x, y     랜드마크 좌표 — 똑바로 세운 이미지 기준, y 는 아래로 갈수록 커진다
     * @param c        관절이 화면 안에 있을 확률 (inFrameLikelihood)
     * @param mirrored 전면 카메라처럼 좌우를 뒤집어 보여 주는가 (거울 모드)
     * @return 상체(양 어깨)가 잡혔으면 true. 잡힌 뼈만 out 에 덮어쓴다
     */
    fun observe(x: FloatArray, y: FloatArray, c: FloatArray, mirrored: Boolean, out: BodyObservation): Boolean {
        // 거울 모드에서는 사람의 왼쪽이 화면 왼쪽에 보이고, 아니면 사람의 오른쪽이 화면 왼쪽에 보인다
        val sgn = if (mirrored) -1f else 1f
        val leftOffset = if (mirrored) 0 else 1
        fun left(i: Int) = i + leftOffset            // 화면 왼쪽에 보이는 쪽 관절
        fun right(i: Int) = i + 1 - leftOffset       // 화면 오른쪽에 보이는 쪽 관절
        fun seen(i: Int) = c[i] >= SEEN
        /** 관절 from → to 방향의 화면 각도. 너무 짧으면(카메라 쪽을 향함) null */
        fun angle(from: Int, to: Int, minLen: Float): Float? {
            val dx = sgn * (x[to] - x[from])
            val dy = y[to] - y[from]
            return if (hypot(dx, dy) < minLen) null else dirDeg(dx, -dy)
        }

        val shL = left(Lm.L_SHOULDER)
        val shR = right(Lm.L_SHOULDER)
        if (!seen(shL) || !seen(shR)) return false
        val shoulderDx = sgn * (x[shR] - x[shL])
        val shoulderDy = y[shR] - y[shL]
        val shoulderW = hypot(shoulderDx, shoulderDy)
        if (shoulderW < 1f) return false
        val midX = (x[shL] + x[shR]) / 2f
        val midY = (y[shL] + y[shR]) / 2f
        val p = out.pose

        // 몸통 — 골반이 보이면 골반→어깨, 안 보이면(가까이 섰을 때) 어깨선에 수직인 방향
        val hipL = left(Lm.L_HIP)
        val hipR = right(Lm.L_HIP)
        val spine = if (seen(hipL) && seen(hipR)) {
            dirDeg(sgn * (midX - (x[hipL] + x[hipR]) / 2f), -(midY - (y[hipL] + y[hipR]) / 2f))
        } else {
            dirDeg(shoulderDy, shoulderDx)
        }
        p[Bone.SPINE] = spine

        // 목 — 두 귀를 잇는 선의 기울기가 고개 기울기. 귀가 안 보이면 어깨 중심→코
        val earL = left(Lm.L_EAR)
        val earR = right(Lm.L_EAR)
        val earDx = sgn * (x[earR] - x[earL])
        val earDy = y[earR] - y[earL]
        p[Bone.NECK] = when {
            seen(earL) && seen(earR) && hypot(earDx, earDy) > shoulderW * 0.12f -> dirDeg(earDy, earDx)
            seen(Lm.NOSE) -> dirDeg(sgn * (x[Lm.NOSE] - midX), -(y[Lm.NOSE] - midY))
            else -> spine
        }

        // 팔 — 화면 밖으로 나간 손목도 ML Kit 이 추정해 주므로 그대로 쓴다
        val minLen = shoulderW * 0.2f
        angle(shL, left(Lm.L_ELBOW), minLen)?.let { p[Bone.L_UPPER_ARM] = it }
        angle(left(Lm.L_ELBOW), left(Lm.L_WRIST), minLen)?.let { p[Bone.L_FOREARM] = it }
        angle(shR, right(Lm.L_ELBOW), minLen)?.let { p[Bone.R_UPPER_ARM] = it }
        angle(right(Lm.L_ELBOW), right(Lm.L_WRIST), minLen)?.let { p[Bone.R_FOREARM] = it }

        // 다리 — 양쪽 골반·무릎·발목이 모두 보일 때만
        val kneeL = left(Lm.L_KNEE)
        val kneeR = right(Lm.L_KNEE)
        val ankL = left(Lm.L_ANKLE)
        val ankR = right(Lm.L_ANKLE)
        val legs = seen(hipL) && seen(hipR) && seen(kneeL) && seen(kneeR) && seen(ankL) && seen(ankR)
        if (legs) {
            angle(hipL, kneeL, minLen)?.let { p[Bone.L_THIGH] = it }
            angle(kneeL, ankL, minLen)?.let { p[Bone.L_SHIN] = it }
            angle(hipR, kneeR, minLen)?.let { p[Bone.R_THIGH] = it }
            angle(kneeR, ankR, minLen)?.let { p[Bone.R_SHIN] = it }
        }
        out.legsTracked = legs
        return true
    }
}
