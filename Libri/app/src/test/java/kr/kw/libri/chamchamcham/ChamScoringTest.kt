package kr.kw.libri.chamchamcham

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChamScoringTest {
    private val started = RoomState(
        attacker = Seat.B,
        scoreA = 2,
        scoreB = 3,
        round = Round(id = 42, attacker = Seat.B, dir = Dir.LEFT, startAt = 100),
    )

    @Test
    fun hitAwardsOnlyTheAttackerAndCannotBeCountedTwice() {
        val result = requireNotNull(applyResult(started, 42, HeadDir.LEFT, hit = true))
        assertEquals(2, result.scoreA)
        assertEquals(4, result.scoreB)
        assertEquals(Seat.B, result.attacker)
        assertEquals("HIT", result.round?.result)
        assertNull(applyResult(result, 42, HeadDir.LEFT, hit = true))
    }

    @Test
    fun dodgePassesTheTurnWithoutChangingEitherScore() {
        val result = requireNotNull(applyResult(started, 42, HeadDir.RIGHT, hit = false))
        assertEquals(2, result.scoreA)
        assertEquals(3, result.scoreB)
        assertEquals(Seat.A, result.attacker)
        assertEquals("MISS", result.round?.result)
        assertEquals("RIGHT", result.round?.look)
    }

    @Test
    fun resultForAStaleOrCancelledRoundIsIgnored() {
        assertNull(applyResult(started, 41, HeadDir.LEFT, hit = true))
        assertNull(applyResult(started.copy(round = null), 42, HeadDir.LEFT, hit = true))
    }
}
