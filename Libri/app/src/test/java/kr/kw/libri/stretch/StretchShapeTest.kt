package kr.kw.libri.stretch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 크리에이티브 코스 — 도형 구멍에는 정해진 자세가 없고, 몸이 구멍에 들어가기만 하면 통과한다 */
class StretchShapeTest {

    private fun fit(target: StretchPose, pose: BodyPose) = target.fit(pose, legsTracked = false)

    /** 팔을 몸 앞으로 모으고 선 자세 — 좁게 서서 어떤 구멍이든 빠져나가려는 꾀 */
    private fun tucked(): BodyPose = BodyPose.rest().apply {
        this[Bone.L_UPPER_ARM] = 178f; this[Bone.L_FOREARM] = 150f
        this[Bone.R_UPPER_ARM] = -178f; this[Bone.R_FOREARM] = -150f
    }

    @Test
    fun `도형은 벽 안에 있고, 예시 자세는 그 구멍에 꼭 맞는다`() {
        for (s in StretchShapes.ALL) {
            val shape = s.shape!!
            for (i in 0 until shape.count) {
                assertTrue("${s.id} 꼭짓점이 벽 옆으로 나감", abs(shape.pts[2 * i]) < WALL_W / 2f - 0.15f)
                assertTrue("${s.id} 꼭짓점이 벽 위로 나감", shape.pts[2 * i + 1] < WALL_H - 0.15f)
            }
            val bones = FloatArray(BONE_COUNT)
            val f = s.fit(s.pose, legsTracked = true, boneOut = bones)
            assertTrue("${s.id} 예시 자세 fit=$f bones=${bones.toList()}", f >= 0.97f)
        }
    }

    @Test
    fun `차렷으로 서 있으면 어떤 구멍에도 걸린다`() {
        for (s in StretchShapes.ALL) {
            val f = fit(s, BodyPose.rest())
            assertTrue("${s.id} 차렷 fit=$f", f < StretchEngine.GOOD_FIT - 0.05f)
        }
    }

    @Test
    fun `구멍 안에만 들어가면 다른 자세로도 통과한다`() {
        // 브이 구멍 — 만세도, 선인장 자세도 된다
        assertTrue(fit(StretchShapes.VEE, StretchPoses.REACH.pose) >= StretchEngine.GOOD_FIT)
        assertTrue(fit(StretchShapes.VEE, StretchPoses.CACTUS.pose) >= StretchEngine.GOOD_FIT)
        // 가로 틈 — 양팔 벌리기도, 선인장 자세도 된다
        assertTrue(fit(StretchShapes.BAR, StretchPoses.WIDE.pose) >= StretchEngine.PERFECT_FIT)
        assertTrue(fit(StretchShapes.BAR, StretchPoses.CACTUS.pose) >= StretchEngine.GOOD_FIT)
        // 동그란 창 — 머리 뒤 깍지도, 선인장 자세도 된다
        assertTrue(fit(StretchShapes.ROUND, StretchPoses.HEAD_HANDS.pose) >= StretchEngine.GOOD_FIT)
        assertTrue(fit(StretchShapes.ROUND, StretchPoses.CACTUS.pose) >= StretchEngine.GOOD_FIT)
        // 기울어진 틈 — 옆구리 늘이기 자세로도 된다
        assertTrue(fit(StretchShapes.LEAN_RIGHT, StretchPoses.SIDE_RIGHT.pose) >= StretchEngine.GOOD_FIT)
        assertTrue(fit(StretchShapes.LEAN_LEFT, StretchPoses.SIDE_LEFT.pose) >= StretchEngine.GOOD_FIT)
    }

    @Test
    fun `구멍 밖으로 나가는 자세는 걸린다`() {
        // 팔을 옆으로 뻗으면 브이 구멍과 동그란 창에 걸린다
        assertTrue(fit(StretchShapes.VEE, StretchPoses.WIDE.pose) < StretchEngine.GOOD_FIT)
        assertTrue(fit(StretchShapes.ROUND, StretchPoses.WIDE.pose) < StretchEngine.GOOD_FIT)
        // 팔을 위로 뻗으면 가로 틈에 걸린다
        assertTrue(fit(StretchShapes.BAR, StretchShapes.VEE.pose) < StretchEngine.GOOD_FIT)
        // 반대쪽으로 기울이면 걸린다
        assertTrue(fit(StretchShapes.LEAN_RIGHT, StretchShapes.LEAN_LEFT.pose) < StretchEngine.GOOD_FIT)
        assertTrue(fit(StretchShapes.LEAN_LEFT, StretchShapes.LEAN_RIGHT.pose) < StretchEngine.GOOD_FIT)
        // 기역 구멍은 좌우가 다르다
        assertTrue(fit(StretchShapes.CORNER_LEFT, StretchShapes.CORNER_RIGHT.pose) < StretchEngine.GOOD_FIT)
        assertTrue(fit(StretchShapes.CORNER_RIGHT, StretchShapes.CORNER_LEFT.pose) < StretchEngine.GOOD_FIT)
    }

    @Test
    fun `팔을 모으고 좁게 서면 지나가더라도 완벽은 못 받는다`() {
        for (s in listOf(StretchShapes.VEE, StretchShapes.BAR, StretchShapes.ROUND, StretchShapes.CORNER_LEFT, StretchShapes.CORNER_RIGHT)) {
            val f = fit(s, tucked())
            assertTrue("${s.id} 모은 자세 fit=$f", f < StretchEngine.PERFECT_FIT)
        }
        // 기울어진 틈은 좁게 서도 기울이지 않으면 어깨가 걸린다
        assertTrue(fit(StretchShapes.LEAN_RIGHT, tucked()) < StretchEngine.GOOD_FIT)
        assertTrue(fit(StretchShapes.LEAN_LEFT, tucked()) < StretchEngine.GOOD_FIT)
    }

    @Test
    fun `튜토리얼은 크리에이티브 코스를 끝까지 지나가고, 가만히 선 벽에서만 부딪힌다`() {
        val s = StretchSession(StretchCourses.CREATIVE, demo = true)
        var guard = 0
        while (s.engine.phase != Phase.RESULT && guard++ < 20_000) s.frame(16.7f) {}
        val e = s.engine
        assertEquals(Phase.RESULT, e.phase)
        assertEquals("서바이벌 튜토리얼은 벽 8개", 8, e.walls.size)
        e.walls.forEachIndexed { i, w ->
            if (i == 4) {
                assertEquals("일부러 가만히 선 벽", Grade.MISS, w.grade)
            } else {
                assertTrue("벽 $i (${w.pose.id}) fit=${w.fit}", w.grade == Grade.PERFECT || w.grade == Grade.GOOD)
            }
        }
        assertEquals(1, e.result!!.miss)
        assertTrue(e.result!!.score > 0)
    }
}
