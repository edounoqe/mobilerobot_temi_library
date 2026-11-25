package kr.kw.libri.omok

/*
 * 오목 규칙 — 화면·서버와 상관없는 순수 계산.
 *  · 판은 15 × 15, 칸 번호 = y * 15 + x. 흑이 먼저 두고 번갈아 한 알씩 놓는다.
 *  · 가로·세로·대각선으로 다섯 알 이상을 먼저 이으면 이긴다 (여섯 알 이상도 승리).
 *  · 삼삼 금지: 한 수로 '열린 3' 이 두 줄 이상 생기는 자리에는 둘 수 없다 (흑·백 모두).
 *    다만 그 수로 다섯 알이 완성되면 둘 수 있다.
 */

const val BOARD = 15
const val CELLS = BOARD * BOARD

const val EMPTY = 0
const val BLACK = 1
const val WHITE = 2
const val DRAW = 3          // 승자 자리에만 쓰는 값 — 판이 가득 찼다

fun otherStone(stone: Int): Int = if (stone == BLACK) WHITE else BLACK

fun cellOf(x: Int, y: Int): Int = y * BOARD + x

// 줄의 네 방향: 가로, 세로, 대각선 두 개
internal val DIR_X = intArrayOf(1, 0, 1, 1)
internal val DIR_Y = intArrayOf(0, 1, 1, -1)

/** (x, y) 칸의 돌 — 판 밖이면 -1 */
internal fun stoneAt(cells: IntArray, x: Int, y: Int): Int =
    if (x in 0 until BOARD && y in 0 until BOARD) cells[y * BOARD + x] else -1

/** (x, y) 옆 칸부터 (dx, dy) 쪽으로 who 의 돌이 몇 알 이어지는지 — (x, y) 자신은 세지 않는다 */
private fun run(cells: IntArray, x: Int, y: Int, dx: Int, dy: Int, who: Int): Int {
    var n = 0
    var cx = x + dx
    var cy = y + dy
    while (stoneAt(cells, cx, cy) == who) {
        n++
        cx += dx
        cy += dy
    }
    return n
}

/** cell 에 놓인 who 의 돌로 다섯 알 이상이 이어졌는지 */
fun makesFive(cells: IntArray, cell: Int, who: Int): Boolean {
    val x = cell % BOARD
    val y = cell / BOARD
    for (d in 0 until 4) {
        if (run(cells, x, y, -DIR_X[d], -DIR_Y[d], who) + run(cells, x, y, DIR_X[d], DIR_Y[d], who) + 1 >= 5) return true
    }
    return false
}

/** cell 에 놓인 who 의 돌을 지나는 다섯 알 이상의 줄 (그 칸들) — 없으면 null */
fun winLine(cells: IntArray, cell: Int, who: Int): IntArray? {
    val x = cell % BOARD
    val y = cell / BOARD
    for (d in 0 until 4) {
        val back = run(cells, x, y, -DIR_X[d], -DIR_Y[d], who)
        val fwd = run(cells, x, y, DIR_X[d], DIR_Y[d], who)
        if (back + fwd + 1 >= 5) {
            return IntArray(back + fwd + 1) { i -> cellOf(x + (i - back) * DIR_X[d], y + (i - back) * DIR_Y[d]) }
        }
    }
    return null
}

/**
 * (x, y) 에 who 의 돌이 놓여 있을 때 d 방향 줄이 '열린 3' 인지.
 * 열린 3 = 한 수를 더 두면 양 끝이 빈 네 알(열린 4)이 되는 줄. 한 수로 다섯 알이 되는 줄은 3 이 아니라 4 이다.
 * cells 를 잠깐 고쳤다가 되돌린다.
 */
private fun openThree(cells: IntArray, x: Int, y: Int, d: Int, who: Int): Boolean {
    val dx = DIR_X[d]
    val dy = DIR_Y[d]
    var makesOpenFour = false
    for (k in -4..4) {
        if (k == 0) continue
        val ex = x + k * dx
        val ey = y + k * dy
        if (stoneAt(cells, ex, ey) != EMPTY) continue
        val e = cellOf(ex, ey)
        cells[e] = who
        val back = run(cells, x, y, -dx, -dy, who)
        val fwd = run(cells, x, y, dx, dy, who)
        cells[e] = EMPTY
        if (k !in -back..fwd) continue                      // 더 둔 돌이 이 줄에 이어지지 않는다
        val len = back + fwd + 1
        if (len >= 5) return false                          // 한 수로 다섯 알 — 이 줄은 4 이다
        if (len == 4 &&
            stoneAt(cells, x - (back + 1) * dx, y - (back + 1) * dy) == EMPTY &&
            stoneAt(cells, x + (fwd + 1) * dx, y + (fwd + 1) * dy) == EMPTY
        ) makesOpenFour = true
    }
    return makesOpenFour
}

/** 빈 칸 cell 에 who 가 두면 삼삼(열린 3 이 두 줄 이상)이 되는지 */
fun isDoubleThree(cells: IntArray, cell: Int, who: Int): Boolean {
    if (cells[cell] != EMPTY) return false
    val c = cells.copyOf()
    c[cell] = who
    val x = cell % BOARD
    val y = cell / BOARD
    var threes = 0
    for (d in 0 until 4) if (openThree(c, x, y, d, who)) threes++
    return threes >= 2
}

enum class Place { OK, TAKEN, DOUBLE_THREE }

/** cell 에 who 가 둘 수 있는지 */
fun checkPlace(cells: IntArray, cell: Int, who: Int): Place = checkPlaceInPlace(cells.copyOf(), cell, who)

/** checkPlace 와 같지만 cells 를 잠깐 고쳤다가 되돌린다 — 혼자 쓰는 판(봇의 수읽기)에서만 쓴다 */
internal fun checkPlaceInPlace(cells: IntArray, cell: Int, who: Int): Place {
    if (cell !in 0 until CELLS || cells[cell] != EMPTY) return Place.TAKEN
    cells[cell] = who
    var ok = makesFive(cells, cell, who)                        // 다섯 알이 되면 삼삼이어도 둘 수 있다
    if (!ok) {
        val x = cell % BOARD
        val y = cell / BOARD
        var threes = 0
        for (d in 0 until 4) if (openThree(cells, x, y, d, who)) threes++
        ok = threes < 2
    }
    cells[cell] = EMPTY
    return if (ok) Place.OK else Place.DOUBLE_THREE
}

/**
 * 지금까지 둔 수를 처음부터 다시 놓아 본 판.
 * @param cells  칸마다 EMPTY · BLACK · WHITE
 * @param count  놓인 돌의 수
 * @param last   마지막에 둔 칸 (없으면 -1)
 * @param winner EMPTY(진행 중) · BLACK · WHITE · DRAW
 * @param line   이긴 줄의 칸들
 */
class GameView(val cells: IntArray, val count: Int, val last: Int, val winner: Int, val line: IntArray?) {
    /** 다음에 둘 돌 */
    val turn: Int get() = if (count % 2 == 0) BLACK else WHITE
}

/** 둔 순서대로 적힌 칸 번호 목록으로 판을 만든다 — 규칙에 어긋난 수가 섞여 있으면 그 앞까지만 놓는다 */
fun replay(moves: List<Int>): GameView {
    val cells = IntArray(CELLS)
    var count = 0
    var last = -1
    var winner = EMPTY
    var line: IntArray? = null
    for (m in moves) {
        val who = if (count % 2 == 0) BLACK else WHITE
        if (checkPlace(cells, m, who) != Place.OK) break
        cells[m] = who
        count++
        last = m
        line = winLine(cells, m, who)
        if (line != null) {
            winner = who
            break
        }
        if (count == CELLS) {
            winner = DRAW
            break
        }
    }
    return GameView(cells, count, last, winner, line)
}
