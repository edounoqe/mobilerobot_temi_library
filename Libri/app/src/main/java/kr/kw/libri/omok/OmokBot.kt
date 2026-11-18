package kr.kw.libri.omok

import kotlin.math.abs
import kotlin.random.Random

/** 봇 난이도 — 몇 수 앞까지 읽는지가 다르다 */
enum class BotLevel(val label: String, val desc: String, internal val depth: Int, internal val width: Int) {
    EASY("쉬움", "연습용", 0, 0),
    NORMAL("보통", "한 수 읽기", 2, 12),      // 내 수 + 상대의 응수
    HARD("어려움", "네 수 읽기", 4, 9),       // 내 수 · 상대 · 내 수 · 상대
}

/**
 * 혼자 연습할 때의 상대. 학습된 모델이 아니라, 자리마다 값을 매기고 그 위에서 몇 수 앞을 읽는 규칙 기반 봇이다.
 *
 *  1) 자리 값 매기기 — 그 자리를 지나는 '다섯 칸짜리 창' 을 모두 보고,
 *       · 상대 돌이 없는 창은 내 돌이 많을수록 (내가 둘 가치)
 *       · 내 돌이 없는 창은 상대 돌이 많을수록 (막을 가치) 점수를 준다.
 *  2) 수읽기 — 값이 높은 자리 몇 개만 골라, 서로 가장 좋은 수를 둔다고 보고 depth 수 앞의 판을 평가한다
 *     (알파-베타 가지치기). 4-3 처럼 두세 수 뒤에 터지는 수를 만들고, 상대의 그런 수를 미리 막는다.
 *
 *  쉬움   : 1) 만 쓰고, 급하지 않을 때는 가끔 덜 좋은 자리에 둔다
 *  보통   : 내 수와 상대의 응수까지 읽는다
 *  어려움 : 네 수 앞까지 읽는다
 */
object OmokBot {
    // 창 안의 돌 수(이번에 두는 돌 포함)에 따른 자리 값
    private val MINE = intArrayOf(0, 1, 12, 150, 6_000, 1_000_000)
    private val THEIRS = intArrayOf(0, 1, 8, 100, 2_500, 200_000)
    private const val URGENT = 200_000 * 4      // 이 값 이상이면 지금 이기거나, 지금 막아야 하는 자리 (score 는 4 를 곱한다)

    // 판 전체 평가 — 창 안에 한쪽 돌만 있을 때, 그 돌 수에 따른 값. 둘 차례인 쪽의 네 알은 다음 수에 이긴다
    private val EVAL_MOVER = intArrayOf(0, 1, 12, 150, 50_000, 0)
    private val EVAL_OTHER = intArrayOf(0, 1, 10, 110, 3_000, 0)
    private const val TWO_FOURS = 40_000        // 상대의 네 알이 두 군데 — 한 번에 다 막을 수 없다
    private const val WIN = 10_000_000
    private const val INF = Int.MAX_VALUE / 2

    /** 판 위의 모든 '다섯 칸짜리 창' 의 칸 번호 (창 하나에 다섯 개씩) */
    private val WINDOWS: IntArray by lazy {
        val out = ArrayList<Int>()
        for (d in 0 until 4) for (y in 0 until BOARD) for (x in 0 until BOARD) {
            if (stoneAt(EMPTY_BOARD, x + 4 * DIR_X[d], y + 4 * DIR_Y[d]) == -1) continue
            for (k in 0 until 5) out += cellOf(x + k * DIR_X[d], y + k * DIR_Y[d])
        }
        out.toIntArray()
    }
    private val EMPTY_BOARD = IntArray(CELLS)

    /** 수읽기 한 층에서 쓰는 임시 배열 */
    private class Buf {
        val cell = IntArray(CELLS)
        val score = IntArray(CELLS)
        val top = IntArray(16)
    }

    /** who 가 둘 칸을 고른다. 둘 곳이 없으면 -1 */
    fun pick(cells: IntArray, who: Int, level: BotLevel = BotLevel.NORMAL, rnd: Random = Random.Default): Int {
        var stones = 0
        for (v in cells) if (v != EMPTY) stones++
        if (stones == 0) return cellOf(BOARD / 2, BOARD / 2)
        // 쉬움은 수읽기 없이. 다른 난이도도 처음 몇 수는 수읽기 없이 둔다 (판마다 다르게 시작하도록)
        if (level == BotLevel.EASY || stones < 3) return quick(cells, who, rnd, careless = level == BotLevel.EASY)

        val board = cells.copyOf()
        val bufs = Array(level.depth + 1) { Buf() }
        val root = bufs[level.depth]
        val n = candidates(board, who, level.width, root)
        if (n == 0) return quick(cells, who, rnd, careless = false)
        var best = -INF
        var bestCell = root.top[0]
        for (i in 0 until n) {
            val m = root.top[i]
            board[m] = who
            val v = if (makesFive(board, m, who)) WIN + level.depth
            else -negamax(board, otherStone(who), level.depth - 1, -INF, -best, level.width, bufs)
            board[m] = EMPTY
            if (v > best) {
                best = v
                bestCell = m
            }
        }
        return bestCell
    }

    /** 둘 차례인 who 의 입장에서 본 판의 값 — 서로 가장 좋은 수를 둔다고 보고 depth 수 앞까지 읽는다 */
    private fun negamax(cells: IntArray, who: Int, depth: Int, alphaIn: Int, beta: Int, width: Int, bufs: Array<Buf>): Int {
        if (depth == 0) return evaluate(cells, who)
        val buf = bufs[depth]
        val n = candidates(cells, who, width, buf)
        if (n == 0) return 0
        var alpha = alphaIn
        var best = -INF
        for (i in 0 until n) {
            val m = buf.top[i]
            cells[m] = who
            // 빨리 이길수록 값이 크다 (남은 depth 가 클수록 빨리 이긴 것)
            val v = if (makesFive(cells, m, who)) WIN + depth
            else -negamax(cells, otherStone(who), depth - 1, -beta, -alpha, width, bufs)
            cells[m] = EMPTY
            if (v > best) best = v
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }
        return best
    }

    /** 값이 높은 순서로 둘 수 있는 자리 width 개를 buf.top 에 담고, 그 수를 돌려준다 */
    private fun candidates(cells: IntArray, who: Int, width: Int, buf: Buf): Int {
        var n = 0
        for (c in 0 until CELLS) {
            if (cells[c] != EMPTY || !nearStone(cells, c)) continue
            buf.cell[n] = c
            buf.score[n] = score(cells, c, who)
            n++
        }
        var k = 0
        while (k < width) {
            var bi = -1
            var bs = -1
            for (i in 0 until n) if (buf.score[i] > bs) {
                bs = buf.score[i]
                bi = i
            }
            if (bi < 0) break
            buf.score[bi] = -1
            val c = buf.cell[bi]
            if (checkPlaceInPlace(cells, c, who) == Place.OK) buf.top[k++] = c
        }
        return k
    }

    /** 판 전체의 값 — 둘 차례인 mover 에게 좋을수록 크다 */
    private fun evaluate(cells: IntArray, mover: Int): Int {
        val w = WINDOWS
        var total = 0
        var otherFours = 0
        var i = 0
        while (i < w.size) {
            var mine = 0
            var theirs = 0
            for (k in 0 until 5) {
                val v = cells[w[i + k]]
                if (v == mover) mine++ else if (v != EMPTY) theirs++
            }
            i += 5
            if (theirs == 0) {
                total += EVAL_MOVER[mine]
            } else if (mine == 0) {
                total -= EVAL_OTHER[theirs]
                if (theirs == 4) otherFours++
            }
        }
        if (otherFours >= 2) total -= TWO_FOURS
        return total
    }

    /** 수읽기 없이 — 자리 값이 가장 높은 곳 (비슷하게 좋은 자리 가운데 하나) */
    private fun quick(cells: IntArray, who: Int, rnd: Random, careless: Boolean): Int {
        var best = -1
        val scores = IntArray(CELLS) { -1 }
        for (cell in 0 until CELLS) {
            if (cells[cell] != EMPTY || !nearStone(cells, cell)) continue
            if (checkPlace(cells, cell, who) != Place.OK) continue
            val s = score(cells, cell, who)
            scores[cell] = s
            if (s > best) best = s
        }
        if (best < 0) {
            // 돌 가까이에 둘 수 있는 자리가 없다 — 아무 빈 칸이나
            return (0 until CELLS).firstOrNull { checkPlace(cells, it, who) == Place.OK } ?: -1
        }
        val top = ArrayList<Int>()
        if (careless && best < URGENT && rnd.nextFloat() < 0.3f) {
            // 쉬움 — 급하지 않으면 열 번에 세 번쯤은 좋은 자리 다섯 곳 가운데 아무 데나 둔다
            val order = (0 until CELLS).filter { scores[it] >= 0 }.sortedByDescending { scores[it] }
            top += order.take(5)
        } else {
            val slack = best / 40
            for (cell in 0 until CELLS) if (scores[cell] >= 0 && scores[cell] >= best - slack) top += cell
        }
        return top[rnd.nextInt(top.size)]
    }

    /** 돌에서 두 칸 안쪽인 자리만 살핀다 */
    private fun nearStone(cells: IntArray, cell: Int): Boolean {
        val x = cell % BOARD
        val y = cell / BOARD
        for (dy in -2..2) for (dx in -2..2) {
            val v = stoneAt(cells, x + dx, y + dy)
            if (v == BLACK || v == WHITE) return true
        }
        return false
    }

    /** cell 에 who 가 둘 때의 자리 값 */
    private fun score(cells: IntArray, cell: Int, who: Int): Int {
        val x = cell % BOARD
        val y = cell / BOARD
        var total = 0
        for (d in 0 until 4) {
            for (start in -4..0) {
                var mine = 0
                var theirs = 0
                var inside = true
                for (i in 0..4) {
                    val v = stoneAt(cells, x + (start + i) * DIR_X[d], y + (start + i) * DIR_Y[d])
                    if (v == -1) {
                        inside = false
                        break
                    }
                    if (v == who) mine++ else if (v != EMPTY) theirs++
                }
                if (!inside) continue
                if (theirs == 0) total += MINE[mine + 1]
                if (mine == 0) total += THEIRS[theirs + 1]
            }
        }
        // 값이 같으면 가운데 쪽
        return total * 4 + (BOARD - abs(x - BOARD / 2) - abs(y - BOARD / 2)) / 4
    }
}
