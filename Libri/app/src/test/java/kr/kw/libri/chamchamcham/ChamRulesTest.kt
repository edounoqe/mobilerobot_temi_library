package kr.kw.libri.chamchamcham

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 참참참 규칙 — 공격이 성공하면 공격자가 계속 공격하고, 실패하면 공격권이 넘어간다 */
class ChamRulesTest {

    private fun roomWithRound(attacker: Seat, dir: Dir = Dir.LEFT) = RoomState(
        attacker = attacker,
        round = Round(id = 7L, attacker = attacker, dir = dir, startAt = 1000L),
        joinedA = true, joinedB = true,
    )

    @Test
    fun `공격 성공 - 공격자가 1점을 얻고 계속 공격한다`() {
        val after = applyResult(roomWithRound(Seat.A), roundId = 7L, look = HeadDir.LEFT, hit = true)
        assertNotNull(after)
        assertEquals(Seat.A, after!!.attacker)
        assertEquals(1, after.scoreA)
        assertEquals(0, after.scoreB)
        assertEquals("HIT", after.round?.result)
    }

    @Test
    fun `공격 실패 - 점수 없이 공격권이 수비자에게 넘어간다`() {
        val after = applyResult(roomWithRound(Seat.A), roundId = 7L, look = HeadDir.RIGHT, hit = false)
        assertNotNull(after)
        assertEquals(Seat.B, after!!.attacker)
        assertEquals(0, after.scoreA)
        assertEquals(0, after.scoreB)
        assertEquals("MISS", after.round?.result)
    }

    @Test
    fun `B 가 공격해도 같은 규칙`() {
        val hit = applyResult(roomWithRound(Seat.B), 7L, HeadDir.CENTER, hit = true)!!
        assertEquals(Seat.B, hit.attacker)
        assertEquals(1, hit.scoreB)
        val miss = applyResult(roomWithRound(Seat.B), 7L, HeadDir.RIGHT, hit = false)!!
        assertEquals(Seat.A, miss.attacker)
        assertEquals(0, miss.scoreB)
    }

    @Test
    fun `연속으로 성공하면 계속 공격하다가, 한 번 피하면 넘어간다`() {
        var room = RoomState(attacker = Seat.A, joinedA = true, joinedB = true)
        // A 가 세 번 공격: 성공, 성공, 실패
        for ((i, hit) in listOf(true, true, false).withIndex()) {
            assertEquals(Seat.A, room.attacker)
            room = room.copy(round = Round(id = i.toLong(), attacker = room.attacker, dir = Dir.RIGHT, startAt = 0L))
            room = applyResult(room, i.toLong(), if (hit) HeadDir.RIGHT else HeadDir.LEFT, hit)!!
        }
        assertEquals(2, room.scoreA)
        assertEquals(Seat.B, room.attacker)
    }

    @Test
    fun `이미 판정한 라운드나 다른 라운드의 결과는 무시한다`() {
        val room = roomWithRound(Seat.A)
        val once = applyResult(room, 7L, HeadDir.LEFT, hit = true)!!
        assertNull(applyResult(once, 7L, HeadDir.LEFT, hit = true))       // 같은 라운드를 두 번
        assertNull(applyResult(room, 99L, HeadDir.LEFT, hit = true))      // 다른 라운드
    }

    @Test
    fun `손은 마지막 참에서 고른 방향으로 돌아간다`() {
        assertEquals(0f, handPose(BEAT_MS + 10, Dir.LEFT).rotation, 0f)                 // 참 · 참 동안은 정면
        assertTrue(handPose(FINAL_AT + ROTATE_MS, Dir.LEFT).rotation < 0f)              // 왼쪽 = 음수
        assertTrue(handPose(FINAL_AT + ROTATE_MS, Dir.RIGHT).rotation > 0f)             // 오른쪽 = 양수
        assertEquals(HAND_TURN_DEG, handPose(FINAL_AT + ROTATE_MS, Dir.RIGHT).rotation, 0.001f)
    }
}
