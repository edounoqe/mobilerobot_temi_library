package kr.kw.libri.stretch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 스트레칭 게임의 순수 로직 검증 — 각도 계산, 동작 목록, 랜드마크 변환, 게임 진행 */
class StretchLogicTest {

    private fun figureOf(p: BodyPose) = Figure().also { p.layout(it) }

    @Test
    fun angles_wrapAroundCorrectly() {
        assertEquals(180f, normDeg(-180f), 1e-4f)
        assertEquals(-170f, normDeg(190f), 1e-4f)
        assertEquals(20f, angleDiff(-170f, 170f), 1e-4f)
        assertEquals(-20f, angleDiff(170f, -170f), 1e-4f)
        assertEquals(90f, dirDeg(1f, 0f), 1e-4f)
        assertEquals(180f, abs(dirDeg(0f, -1f)), 1e-4f)
    }

    @Test
    fun chase_takesShortestArc() {
        val a = BodyPose().apply { this[Bone.SPINE] = 170f }
        val b = BodyPose().apply { this[Bone.SPINE] = -170f }
        a.chase(b, 0.5f)
        assertEquals(180f, abs(a[Bone.SPINE]), 1e-3f)
    }

    @Test
    fun layout_standsOnFloorAndMirrorsLeftRight() {
        val f = figureOf(StretchPoses.WIDE.pose)
        // 발목은 바닥 바로 위, 양팔은 어깨 높이에서 좌우 대칭
        assertEquals(Body.ANKLE_H, f.y[Joint.ANK_L], 1e-4f)
        assertEquals(f.y[Joint.SHO_L], f.y[Joint.WRI_L], 1e-3f)
        assertEquals(-f.x[Joint.WRI_L], f.x[Joint.WRI_R], 1e-3f)
        assertTrue(f.x[Joint.WRI_L] < f.x[Joint.SHO_L])
        // 기지개는 손이 머리보다 위
        val r = figureOf(StretchPoses.REACH.pose)
        assertTrue(r.y[Joint.WRI_L] > r.y[Joint.HEAD] + Body.HEAD_R)
    }

    @Test
    fun everyHole_fitsInsideTheWall() {
        val pad = HOLE_MARGIN + 0.12f + Body.HAND_R
        for (p in StretchPoses.ALL) {
            val f = figureOf(p.pose)
            for (j in 0 until Joint.COUNT) {
                assertTrue("${p.id} 관절 $j 가 벽 옆으로 나감", abs(f.x[j]) + pad < WALL_W / 2f)
                assertTrue("${p.id} 관절 $j 가 벽 위로 나감", f.y[j] + pad + Body.HEAD_R < WALL_H)
            }
        }
    }

    @Test
    fun fit_isPerfectForTargetAndLowForOtherPoses() {
        val bone = FloatArray(BONE_COUNT)
        for (target in StretchPoses.ALL) {
            assertEquals(1f, target.fit(target.pose, legsTracked = true, boneOut = bone), 1e-4f)
            assertTrue("${target.id}: 차렷 자세가 통과됨", target.fit(BodyPose.rest(), true) < StretchEngine.GOOD_FIT)
            for (other in StretchPoses.ALL) {
                if (other === target) continue
                for (legs in listOf(true, false)) {
                    val s = target.fit(other.pose, legs)
                    assertTrue("${other.id} 자세로 ${target.id} 벽을 통과함 (fit=$s, legs=$legs)", s < StretchEngine.GOOD_FIT)
                }
            }
        }
    }

    @Test
    fun fit_toleratesSmallErrors() {
        val p = StretchPoses.WIDE.pose.copy()
        for (i in p.a.indices) p.a[i] = normDeg(p.a[i] + if (i % 2 == 0) 5f else -5f)
        assertTrue(StretchPoses.WIDE.fit(p, true) >= StretchEngine.PERFECT_FIT)
    }

    /** 마네킹 자세를 ML Kit 이 줄 법한 랜드마크로 바꾼다 (사람이 카메라를 마주 본 상태) */
    private fun landmarksOf(p: BodyPose, mirrored: Boolean, scale: Float = 120f): Triple<FloatArray, FloatArray, FloatArray> {
        val f = figureOf(p)
        val w = 640f
        val x = FloatArray(Lm.COUNT)
        val y = FloatArray(Lm.COUNT)
        val c = FloatArray(Lm.COUNT) { 0.95f }
        // 화면에 보이는 좌표를 원본 이미지 좌표로 — 거울 모드면 원본은 좌우가 뒤집혀 있다
        fun rawX(fx: Float) = (w / 2f + fx * scale).let { if (mirrored) w - it else it }
        fun rawY(fy: Float) = 460f - fy * scale
        // 거울 모드: 사람의 왼쪽 = 화면 왼쪽. 아니면 사람의 왼쪽 = 화면 오른쪽
        fun put(landmarkLeft: Int, screenLeftX: Float, screenLeftY: Float, screenRightX: Float, screenRightY: Float) {
            val l = if (mirrored) landmarkLeft else landmarkLeft + 1
            val r = if (mirrored) landmarkLeft + 1 else landmarkLeft
            x[l] = rawX(screenLeftX); y[l] = rawY(screenLeftY)
            x[r] = rawX(screenRightX); y[r] = rawY(screenRightY)
        }
        fun putJoints(landmarkLeft: Int, jl: Int, jr: Int) = put(landmarkLeft, f.x[jl], f.y[jl], f.x[jr], f.y[jr])
        putJoints(Lm.L_SHOULDER, Joint.SHO_L, Joint.SHO_R)
        putJoints(Lm.L_ELBOW, Joint.ELB_L, Joint.ELB_R)
        putJoints(Lm.L_WRIST, Joint.WRI_L, Joint.WRI_R)
        putJoints(Lm.L_HIP, Joint.HIP_L, Joint.HIP_R)
        putJoints(Lm.L_KNEE, Joint.KNE_L, Joint.KNE_R)
        putJoints(Lm.L_ANKLE, Joint.ANK_L, Joint.ANK_R)
        // 귀 — 머리 중심에서 고개 방향에 수직으로 양옆
        val neck = Math.toRadians(p[Bone.NECK].toDouble())
        val rx = cos(neck).toFloat() * 0.16f
        val ry = -sin(neck).toFloat() * 0.16f
        put(Lm.L_EAR, f.x[Joint.HEAD] - rx, f.y[Joint.HEAD] - ry, f.x[Joint.HEAD] + rx, f.y[Joint.HEAD] + ry)
        x[Lm.NOSE] = rawX(f.x[Joint.HEAD]); y[Lm.NOSE] = rawY(f.y[Joint.HEAD])
        return Triple(x, y, c)
    }

    @Test
    fun tracker_recoversEveryPoseFromLandmarks() {
        for (mirrored in listOf(true, false)) for (target in StretchPoses.ALL) {
            val (x, y, c) = landmarksOf(target.pose, mirrored)
            val obs = BodyObservation()
            assertTrue(PoseTracker.observe(x, y, c, mirrored, obs))
            assertTrue(obs.legsTracked)
            for (b in BONES) {
                val d = abs(angleDiff(obs.pose[b], target.pose[b]))
                assertTrue("${target.id} ${b.name} mirrored=$mirrored 오차 $d°", d < 1.5f)
            }
            assertTrue(target.fit(obs.pose, true) > 0.97f)
        }
    }

    @Test
    fun tracker_handlesUpperBodyOnlyAndNobody() {
        val (x, y, c) = landmarksOf(StretchPoses.SIDE_LEFT.pose, mirrored = true)
        // 골반 아래가 화면 밖 — 몸통 기울기는 어깨선으로 추정한다
        for (i in Lm.L_HIP until Lm.COUNT) c[i] = 0.1f
        val obs = BodyObservation()
        assertTrue(PoseTracker.observe(x, y, c, true, obs))
        assertFalse(obs.legsTracked)
        assertEquals(StretchPoses.SIDE_LEFT.pose[Bone.SPINE], obs.pose[Bone.SPINE], 1.5f)
        assertTrue(StretchPoses.SIDE_LEFT.fit(obs.pose, legsTracked = false) > 0.97f)
        // 어깨가 안 보이면 사람 없음
        c[Lm.L_SHOULDER] = 0.2f
        assertFalse(PoseTracker.observe(x, y, c, true, obs))
    }

    private fun play(course: StretchCourse): StretchSession {
        val s = StretchSession(course, demo = true)
        var guard = 0
        while (s.engine.phase != Phase.RESULT && guard++ < 20_000) s.frame(16.7f) {}
        return s
    }

    @Test
    fun demoRun_passesWallsAndShowsEveryGrade() {
        for (course in StretchCourses.ALL) {
            val s = play(course)
            val e = s.engine
            assertEquals(Phase.RESULT, e.phase)
            assertEquals(course.poses.size, e.walls.size)
            val grades = e.walls.map { it.grade }
            assertEquals("일부러 틀리는 벽", Grade.MISS, grades[4])
            assertEquals("어설프게 맞추는 벽 fit=${e.walls[2].fit}", Grade.GOOD, grades[2])
            grades.forEachIndexed { i, g ->
                if (i != 2 && i != 4) assertEquals("벽 $i (${e.walls[i].pose.id}) fit=${e.walls[i].fit}", Grade.PERFECT, g)
            }
            val r = e.result
            assertNotNull(r)
            assertEquals(1, r!!.miss)
            assertTrue(r.score > 0 && r.stars in 2..3)
            assertTrue(r.maxCombo >= 2)
        }
    }

    @Test
    fun nobodyInFront_neverStartsAndStandingStillFails() {
        // 사람이 없으면 READY 에서 넘어가지 않는다
        val e = StretchEngine(StretchCourses.LIGHT)
        repeat(600) { e.update(16.7f, BodyPose.rest(), tracked = false, legsTracked = false) }
        assertEquals(Phase.READY, e.phase)
        // 차렷으로 서 있기만 하면 전부 부딪힌다
        var guard = 0
        while (e.phase != Phase.RESULT && guard++ < 20_000) e.update(16.7f, BodyPose.rest(), tracked = true, legsTracked = false)
        assertEquals(Phase.RESULT, e.phase)
        assertEquals(StretchCourses.LIGHT.poses.size, e.result!!.miss)
        assertEquals(0, e.result!!.score)
        assertEquals(1, e.result!!.stars)
    }

    @Test
    fun events_arriveInOrder() {
        val s = StretchSession(StretchCourses.LIGHT, demo = true)
        val seen = ArrayList<StretchEvent>()
        var guard = 0
        while (s.engine.phase != Phase.RESULT && guard++ < 20_000) s.frame(16.7f) { seen += it }
        assertEquals(listOf(3, 2, 1), seen.filterIsInstance<StretchEvent.Countdown>().map { it.n })
        assertEquals(1, seen.count { it is StretchEvent.Go })
        assertEquals((0 until 6).toList(), seen.filterIsInstance<StretchEvent.Judged>().map { it.index })
        assertTrue(seen.last() is StretchEvent.Finished)
    }
}
