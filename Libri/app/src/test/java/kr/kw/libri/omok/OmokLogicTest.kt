package kr.kw.libri.omok

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 오목 — 규칙(다섯 알 · 삼삼), 봇, 두 사람이 한 방에서 두는 약속 */
class OmokLogicTest {

    /** 그림으로 판을 만든다 — 'X' 흑, 'O' 백, '.' 빈 칸. 왼쪽 위 구석부터 채운다 */
    private fun board(vararg rows: String): IntArray {
        val cells = IntArray(CELLS)
        for ((y, row) in rows.withIndex()) for ((x, ch) in row.withIndex()) {
            cells[cellOf(x, y)] = when (ch) {
                'X' -> BLACK
                'O' -> WHITE
                else -> EMPTY
            }
        }
        return cells
    }

    // ── 다섯 알 ──

    @Test
    fun `가로 세로 대각선 어느 쪽이든 다섯 알이면 이긴다`() {
        val dirs = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
        for ((dx, dy) in dirs) {
            val cells = IntArray(CELLS)
            for (i in 0 until 5) cells[cellOf(5 + i * dx, 7 + i * dy)] = BLACK
            val line = winLine(cells, cellOf(5 + 2 * dx, 7 + 2 * dy), BLACK)
            assertNotNull("방향 ($dx, $dy)", line)
            assertEquals(5, line!!.size)
        }
    }

    @Test
    fun `네 알은 아직 이긴 것이 아니고, 여섯 알도 이긴 것으로 친다`() {
        val four = board("", ".XXXX")
        assertNull(winLine(four, cellOf(2, 1), BLACK))
        val six = board("", ".XXXXXX")
        assertEquals(6, winLine(six, cellOf(3, 1), BLACK)!!.size)
    }

    @Test
    fun `판 가장자리를 넘어 이어지지 않는다`() {
        // 윗줄 오른쪽 끝 세 칸과 다음 줄 왼쪽 끝 두 칸 — 칸 번호로는 이어져 보여도 줄이 아니다
        val cells = IntArray(CELLS)
        for (x in 12..14) cells[cellOf(x, 0)] = BLACK
        for (x in 0..1) cells[cellOf(x, 1)] = BLACK
        assertNull(winLine(cells, cellOf(14, 0), BLACK))
    }

    // ── 삼삼 ──

    @Test
    fun `열린 3 이 두 줄 생기는 자리는 삼삼이라 둘 수 없다`() {
        val cells = board(
            "...............",
            "...............",
            ".....X.........",
            ".....X.........",
            "...XX..........",        // (5, 4) 에 두면 가로 3, 세로 3
        )
        val spot = cellOf(5, 4)
        assertTrue(isDoubleThree(cells, spot, BLACK))
        assertEquals(Place.DOUBLE_THREE, checkPlace(cells, spot, BLACK))
        // 백에게도 같은 규칙 — 다만 백 돌이 아니므로 백이 여기 두는 것은 삼삼이 아니다
        assertEquals(Place.OK, checkPlace(cells, spot, WHITE))
    }

    @Test
    fun `한 칸 띈 3 도 열린 3 이다`() {
        val cells = board(
            "...............",
            "...............",
            ".....X.........",
            ".....X.........",
            "..X.X..........",        // (5, 4) 에 두면 가로 X.XX, 세로 3
        )
        assertTrue(isDoubleThree(cells, cellOf(5, 4), BLACK))
    }

    @Test
    fun `한쪽이 막힌 3 은 열린 3 이 아니어서 둘 수 있다`() {
        val cells = board(
            "...............",
            "...............",
            ".....X.........",
            ".....X.........",
            "..OXX.O........",        // 가로는 양쪽이 백으로 막혀 네 알을 만들어도 열린 4 가 안 된다
        )
        assertFalse(isDoubleThree(cells, cellOf(5, 4), BLACK))
        assertEquals(Place.OK, checkPlace(cells, cellOf(5, 4), BLACK))
    }

    @Test
    fun `4 와 3 이 함께 생기는 자리는 둘 수 있다`() {
        val cells = board(
            "...............",
            ".....X.........",
            ".....X.........",
            ".....X.........",
            "...XX..........",        // (5, 4): 세로는 네 알(4), 가로는 3
        )
        assertFalse(isDoubleThree(cells, cellOf(5, 4), BLACK))
        // 한 칸 띈 4 (X.XXX 꼴)도 3 으로 세지 않는다
        val split = board(
            "...............",
            ".....X.........",
            "...............",
            ".....X.........",
            ".....X.........",
            "...XX..........",        // (5, 5): 세로 X.XXX, 가로 3
        )
        assertFalse(isDoubleThree(split, cellOf(5, 5), BLACK))
    }

    @Test
    fun `삼삼 자리라도 다섯 알이 완성되면 둘 수 있다`() {
        val cells = board(
            ".....X.........",
            ".....X.........",
            ".....X.........",
            ".....X.........",
            "...XX..........",
        )
        assertEquals(Place.OK, checkPlace(cells, cellOf(5, 4), BLACK))
    }

    @Test
    fun `이미 돌이 있는 칸과 판 밖에는 둘 수 없다`() {
        val cells = board("X")
        assertEquals(Place.TAKEN, checkPlace(cells, 0, WHITE))
        assertEquals(Place.TAKEN, checkPlace(cells, -1, WHITE))
        assertEquals(Place.TAKEN, checkPlace(cells, CELLS, WHITE))
    }

    // ── 다시 놓아 보기 ──

    @Test
    fun `흑이 먼저 두고 번갈아 놓이며, 다섯 알에서 판이 끝난다`() {
        // 흑은 7번째 줄에 나란히, 백은 8번째 줄에 나란히
        val moves = ArrayList<Int>()
        for (i in 0 until 5) {
            moves += cellOf(3 + i, 7)
            if (i < 4) moves += cellOf(3 + i, 8)
        }
        val before = replay(moves.dropLast(1))
        assertEquals(EMPTY, before.winner)
        assertEquals(BLACK, before.turn)
        assertEquals(8, before.count)

        val done = replay(moves + cellOf(0, 0))          // 끝난 뒤에 적힌 수는 놓지 않는다
        assertEquals(BLACK, done.winner)
        assertEquals(9, done.count)
        assertEquals(cellOf(7, 7), done.last)
        assertArrayEquals(IntArray(5) { cellOf(3 + it, 7) }, done.line)
    }

    @Test
    fun `규칙에 어긋난 수가 섞여 있으면 그 앞까지만 놓는다`() {
        val view = replay(listOf(cellOf(7, 7), cellOf(7, 7), cellOf(8, 8)))
        assertEquals(1, view.count)
        assertEquals(WHITE, view.turn)
    }

    // ── 봇 ──

    @Test
    fun `봇은 빈 판에서 가운데에 둔다`() {
        for (level in BotLevel.entries) assertEquals(cellOf(7, 7), OmokBot.pick(IntArray(CELLS), BLACK, level, Random(1)))
    }

    @Test
    fun `봇은 이길 수 있으면 이기고, 아니면 상대의 다섯 알을 막는다`() {
        // 백(봇)이 네 알, 흑도 네 알 — 막기보다 이기는 쪽을 고른다
        val both = board(
            "...............",
            ".XXXX..........",
            "...............",
            ".OOOO..........",
        )
        for (level in BotLevel.entries) {
            val win = OmokBot.pick(both, WHITE, level, Random(1))
            assertTrue("$level 이 고른 자리 $win", win == cellOf(0, 3) || win == cellOf(5, 3))
        }

        // 흑만 네 알(한쪽은 막힘) — 남은 한쪽을 막는다
        val threat = board(
            "...............",
            "OXXXX..........",
            "...............",
            "..O............",
        )
        for (level in BotLevel.entries) assertEquals("$level", cellOf(5, 1), OmokBot.pick(threat, WHITE, level, Random(1)))
    }

    @Test
    fun `봇은 열린 3 을 막는다`() {
        val cells = board(
            "...............",
            "...............",
            "....XXX........",
            "...............",
            "......O........",
        )
        for (level in listOf(BotLevel.NORMAL, BotLevel.HARD)) {
            val pick = OmokBot.pick(cells, WHITE, level, Random(3))
            assertTrue("$level 이 고른 자리 $pick", pick == cellOf(3, 2) || pick == cellOf(7, 2))
        }
    }

    /** 봇 둘이 한 판을 둔다 — 규칙에 어긋난 수가 나오면 실패. 이긴 돌(또는 DRAW)을 돌려준다 */
    private fun botGame(black: BotLevel, white: BotLevel, seed: Int): Int {
        val rnd = Random(seed)
        val moves = ArrayList<Int>()
        var view = replay(moves)
        while (view.winner == EMPTY) {
            val cell = OmokBot.pick(view.cells, view.turn, if (view.turn == BLACK) black else white, rnd)
            assertEquals(Place.OK, checkPlace(view.cells, cell, view.turn))
            moves += cell
            view = replay(moves)
            assertEquals(moves.size, view.count)
        }
        return view.winner
    }

    @Test
    fun `봇끼리 두어도 규칙을 어기지 않고 끝까지 간다`() {
        for (level in BotLevel.entries) {
            val winner = botGame(level, level, seed = 7)
            assertTrue(winner == BLACK || winner == WHITE || winner == DRAW)
        }
    }

    @Test
    fun `수를 더 읽는 봇이 더 잘 둔다`() {
        // 흑·백을 바꿔 가며 여러 판 — 어려움이 쉬움을, 어려움이 보통을 더 많이 이긴다
        fun wins(strong: BotLevel, weak: BotLevel): Int {
            var n = 0
            for (seed in 1..5) {
                if (botGame(strong, weak, seed) == BLACK) n++
                if (botGame(weak, strong, seed + 100) == WHITE) n++
            }
            return n
        }
        val hardVsEasy = wins(BotLevel.HARD, BotLevel.EASY)
        val normalVsEasy = wins(BotLevel.NORMAL, BotLevel.EASY)
        val hardVsNormal = wins(BotLevel.HARD, BotLevel.NORMAL)
        println("10판 중 승 — 어려움:쉬움 $hardVsEasy, 보통:쉬움 $normalVsEasy, 어려움:보통 $hardVsNormal")
        assertTrue("어려움 대 쉬움 $hardVsEasy/10", hardVsEasy >= 8)
        assertTrue("보통 대 쉬움 $normalVsEasy/10", normalVsEasy >= 6)
        assertTrue("어려움 대 보통 $hardVsNormal/10", hardVsNormal >= 6)
    }

    @Test
    fun `어려움 봇은 네 수 앞의 이기는 길을 찾는다`() {
        // 백(봇): 가로로 막힌 3, 세로로 열린 2 가 (6, 6) 에서 만난다 — 거기 두면 4 와 3 이 한꺼번에 생겨 막을 수 없다
        val cells = board(
            "...............",
            "...............",
            "...............",
            "...............",
            "......O........",
            "......O........",
            "..XOOO.........",
            "...............",
            "..X.X..........",
        )
        assertEquals(cellOf(6, 6), OmokBot.pick(cells, WHITE, BotLevel.HARD, Random(1)))
    }

    // ── 두 사람이 한 방에서 ──

    private val jin = Player("id-jin", "진")
    private val bom = Player("id-bom", "봄")

    private class Table(val store: MemoryRoomStore, val a: OmokSession, val b: OmokSession) {
        val room get() = store.room.value
        val game get() = room.game!!

        /** 방이 바뀌었다고 두 사람에게 알린다 (앱에서는 방 상태를 구독해 자동으로 불린다) */
        fun sync() {
            a.sync(room)
            b.sync(room)
        }
    }

    private fun table(): Table {
        val store = MemoryRoomStore()
        var id = 100L
        val a = OmokSession(store, jin) { id++ }
        val b = OmokSession(store, bom) { id++ }
        a.join { }
        b.join { }
        return Table(store, a, b).also { it.sync() }
    }

    @Test
    fun `먼저 온 사람이 A, 다음 사람이 B 에 앉고 세 번째 사람은 못 들어온다`() {
        val t = table()
        assertEquals(Seat.A, t.a.seat)
        assertEquals(Seat.B, t.b.seat)
        var third: Seat? = Seat.A
        OmokSession(t.store, Player("id-x", "셋째")).join { third = it }
        assertNull(third)
    }

    @Test
    fun `두 사람이 모이면 A 가 흑으로 새 판이 열린다`() {
        val t = table()
        assertEquals(Seat.A, t.game.black)
        assertEquals(jin.id, t.game.aId)
        assertEquals(bom.id, t.game.bId)
        assertTrue(t.game.moves.isEmpty())
    }

    @Test
    fun `자기 차례에만 둘 수 있다`() {
        val t = table()
        assertNull(t.b.place(t.room, cellOf(7, 7)))                     // 백이 먼저 두려 함
        assertEquals(Place.OK, t.a.place(t.room, cellOf(7, 7)))
        assertNull(t.a.place(t.room, cellOf(8, 8)))                     // 흑이 연달아 두려 함
        assertEquals(Place.TAKEN, t.b.place(t.room, cellOf(7, 7)))      // 이미 돌이 있는 칸
        assertEquals(Place.OK, t.b.place(t.room, cellOf(8, 8)))
        assertEquals(listOf(cellOf(7, 7), cellOf(8, 8)), t.game.moves)
    }

    /** 흑(A)이 가로 다섯 알을 완성한다 */
    private fun blackWins(t: Table) {
        for (i in 0 until 5) {
            assertEquals(Place.OK, t.a.place(t.room, cellOf(3 + i, 7)))
            if (i < 4) assertEquals(Place.OK, t.b.place(t.room, cellOf(3 + i, 9)))
        }
    }

    @Test
    fun `다섯 알을 이으면 이기고, 끝난 판에는 더 둘 수 없다`() {
        val t = table()
        blackWins(t)
        assertEquals(Outcome(Seat.A, Reason.FIVE), outcomeOf(t.game))
        assertNull(t.b.place(t.room, cellOf(0, 0)))
        assertFalse(t.b.resign(t.room))                                 // 끝난 판은 기권할 수 없다
    }

    @Test
    fun `한 판 더 하면 흑이 바뀐다`() {
        val t = table()
        assertFalse(t.a.rematch(t.room))                                // 판이 끝나기 전에는 안 된다
        blackWins(t)
        val first = t.game.id
        assertTrue(t.b.rematch(t.room))
        assertTrue(t.game.id != first)
        assertEquals(Seat.B, t.game.black)
        assertTrue(t.game.moves.isEmpty())
        assertNull(t.a.place(t.room, cellOf(7, 7)))                     // 이번에는 B 가 흑(선)
        assertEquals(Place.OK, t.b.place(t.room, cellOf(7, 7)))
    }

    @Test
    fun `기권하면 상대가 이긴다`() {
        val t = table()
        t.a.place(t.room, cellOf(7, 7))
        assertTrue(t.b.resign(t.room))
        assertEquals(Outcome(Seat.A, Reason.RESIGN), outcomeOf(t.game))
    }

    @Test
    fun `판 도중에 나가면 남은 사람이 이기고, 새 사람이 오면 새 판이 열린다`() {
        val t = table()
        t.a.place(t.room, cellOf(7, 7))
        val first = t.game.id
        t.b.leave(t.room)
        t.sync()
        assertNull(t.room.b)
        assertEquals(Outcome(Seat.A, Reason.RESIGN), outcomeOf(t.game))

        val sol = OmokSession(t.store, Player("id-sol", "솔")) { 900L }
        var seat: Seat? = null
        sol.join { seat = it }
        assertEquals(Seat.B, seat)
        t.a.sync(t.room)
        assertTrue(t.game.id != first)
        assertEquals("id-sol", t.game.bId)
        assertNull(outcomeOf(t.game))
    }

    @Test
    fun `앱이 꺼져 자리만 비어도 남은 사람이 기권 처리한다`() {
        val t = table()
        t.a.place(t.room, cellOf(7, 7))
        t.store.releaseSeat(Seat.B, bom)                                // 연결이 끊겨 자리만 사라짐
        t.a.sync(t.room)
        assertEquals(Outcome(Seat.A, Reason.RESIGN), outcomeOf(t.game))
    }

    @Test
    fun `다른 두 사람이 남기고 간 판은 건드리지 않고 새 판을 연다`() {
        val stale = OmokGame(1L, Seat.A, "old-a", "old-b", moves = listOf(cellOf(7, 7)))
        val store = MemoryRoomStore(OmokRoom(game = stale))
        val a = OmokSession(store, jin) { 50L }
        a.join { }
        a.sync(store.room.value)
        assertEquals(stale, store.room.value.game)                      // 혼자서는 남의 판을 기권 처리하지 않는다
        assertNull(a.place(store.room.value, cellOf(8, 8)))
        val b = OmokSession(store, bom) { 51L }
        b.join { }
        a.sync(store.room.value)
        assertEquals(50L, store.room.value.game!!.id)
        assertEquals(jin.id, store.room.value.game!!.aId)
    }

    // ── 랭킹 ──

    @Test
    fun `랭킹은 이긴 판이 많은 순서, 같으면 진 판이 적은 순서`() {
        val sorted = sortRanking(
            listOf(RankEntry("다", 2, 5), RankEntry("가", 5, 3), RankEntry("나", 5, 1), RankEntry("라", 0, 0)),
        )
        assertEquals(listOf("나", "가", "다", "라"), sorted.map { it.name })
    }
}
