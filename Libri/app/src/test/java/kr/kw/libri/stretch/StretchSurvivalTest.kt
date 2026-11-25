package kr.kw.libri.stretch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 크리에이티브 서바이벌 — 하트 3개, 끝없이 나오는 벽, 점점 빨라지는 간격 */
class StretchSurvivalTest {

    private val course = StretchCourses.CREATIVE

    @Test
    fun `벽 간격은 점점 줄어들다가 가장 빠른 간격에서 멈춘다`() {
        val gaps = (0 until 40).map { course.intervalMs(it) }
        for (i in 1 until gaps.size) assertTrue("간격이 늘어남 at $i", gaps[i] <= gaps[i - 1])
        assertTrue(gaps.first() > gaps.last())
        assertEquals(course.lastIntervalMs, gaps.last(), 0.01f)
    }

    @Test
    fun `처음엔 쉬운 도형, 뒤로 갈수록 어려운 도형이 나온다`() {
        val rnd = Random(7)
        var prev: StretchPose? = null
        val seq = (0 until 60).map { i -> course.poseAt(i, prev, rnd).also { prev = it } }
        assertTrue("처음 4개는 쉬운 도형", seq.take(4).all { it in StretchShapes.EASY })
        assertTrue("뒤쪽엔 어려운 도형이 자주", seq.drop(20).count { it in StretchShapes.HARD } >= 15)
        for (i in 1 until seq.size) assertTrue("같은 구멍이 연달아 나옴 at $i", seq[i] !== seq[i - 1])
    }

    @Test
    fun `차렷으로 서 있으면 하트 3개를 잃고 게임 오버`() {
        val e = StretchEngine(course, rnd = Random(1))
        var guard = 0
        while (e.phase != Phase.RESULT && guard++ < 50_000) e.update(16.7f, BodyPose.rest(), tracked = true, legsTracked = false)
        assertEquals(Phase.RESULT, e.phase)
        assertEquals(0, e.lives)
        val r = e.result!!
        assertTrue(r.gameOver)
        assertEquals(3, r.miss)
        assertEquals(0, r.passed)
        assertEquals("부딪힌 벽 뒤로는 남지 않는다", 3, e.walls.size)
    }

    @Test
    fun `튜토리얼은 정해진 수의 벽에서 끝난다`() {
        val s = StretchSession(course, demo = true)
        var guard = 0
        while (s.engine.phase != Phase.RESULT && guard++ < 50_000) s.frame(16.7f) {}
        assertEquals(Phase.RESULT, s.engine.phase)
        assertTrue("벽이 너무 많음: ${s.engine.walls.size}", s.engine.walls.size <= 8)
    }
}
