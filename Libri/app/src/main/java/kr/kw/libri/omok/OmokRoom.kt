package kr.kw.libri.omok

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * 오목 방 — 두 사람(또는 사람과 봇)이 함께 보는 상태와, 그 상태를 바꾸는 약속.
 *
 *  방에는 자리 두 개(A · B)와 판 하나가 있다.
 *   · 들어오면 빈 자리에 앉는다 (A 가 먼저, A 가 찼으면 B).
 *   · 두 사람이 다 앉으면 A 자리가 새 판을 연다. 첫 판은 A 가 흑(선), '한 판 더' 를 하면 흑이 바뀐다.
 *   · 판에는 둔 순서대로 칸 번호만 쌓는다. 누구 차례인지, 누가 이겼는지는 두 기기가 같은 규칙으로 계산한다.
 *   · 기권하거나 도중에 나가면 그 자리의 패배로 끝난다.
 *   · 한 수에 MOVE_TIME_MS(15초) — 체스처럼, 자기 차례에 시간을 다 쓰면 그 자리의 패배로 끝난다.
 *
 *  방을 어디에 두는지(OmokRoomStore)는 갈아 끼운다 — 기기 2대 대결은 Firebase, 혼자 연습은 기기 안의 메모리.
 *  약속 자체(OmokSession)는 어느 쪽이든 같아서, 단위 테스트는 메모리 방에 두 사람을 앉혀 확인한다.
 */

/** 한 수를 두는 데 쓸 수 있는 시간 — 다 쓰면 진다 */
const val MOVE_TIME_MS = 15_000L

enum class Seat {
    A, B;

    val other: Seat get() = if (this == A) B else A
}

/** 자리에 앉은 사람 — id 는 기기(앱 실행)마다 다르다 */
data class Player(val id: String, val name: String)

data class OmokGame(
    val id: Long,
    val black: Seat,                        // 흑(선)을 잡은 자리
    val aId: String,                        // 이 판을 두는 두 사람 — 자리에 다른 사람이 앉으면 새 판을 연다
    val bId: String,
    val moves: List<Int> = emptyList(),     // 둔 순서대로 칸 번호
    val resign: Seat? = null,               // 기권했거나 도중에 나간 자리
    val timeout: Seat? = null,              // 제한시간을 넘긴 자리
) {
    fun stoneOf(seat: Seat): Int = if (seat == black) BLACK else WHITE
    fun seatOf(stone: Int): Seat = if (stone == BLACK) black else black.other
    fun idOf(seat: Seat): String = if (seat == Seat.A) aId else bId
}

data class OmokRoom(val a: Player? = null, val b: Player? = null, val game: OmokGame? = null) {
    fun player(seat: Seat): Player? = if (seat == Seat.A) a else b
}

enum class Reason { FIVE, RESIGN, TIMEOUT, FULL }

/** 판의 결과 — winner 가 null 이면 무승부 */
data class Outcome(val winner: Seat?, val reason: Reason)

/** 끝난 판이면 결과, 아직 두는 중이면 null */
fun outcomeOf(game: OmokGame, view: GameView = replay(game.moves)): Outcome? = when {
    view.winner == BLACK || view.winner == WHITE -> Outcome(game.seatOf(view.winner), Reason.FIVE)
    view.winner == DRAW -> Outcome(null, Reason.FULL)
    game.resign != null -> Outcome(game.resign.other, Reason.RESIGN)
    game.timeout != null -> Outcome(game.timeout.other, Reason.TIMEOUT)
    else -> null
}

/** 방이 놓인 곳 */
interface OmokRoomStore {
    val room: StateFlow<OmokRoom>

    /** 방을 읽거나 쓸 수 없게 됐을 때의 안내 (정상이면 null) */
    val error: StateFlow<String?>

    /** 자리가 비어 있으면 앉는다 — 이미 내가 앉아 있어도 성공 */
    fun claimSeat(seat: Seat, player: Player, onResult: (Boolean) -> Unit)
    fun releaseSeat(seat: Seat, player: Player)
    fun putGame(game: OmokGame)
    fun putMove(gameId: Long, index: Int, cell: Int)
    fun putResign(gameId: Long, seat: Seat)
    fun putTimeout(gameId: Long, seat: Seat)
    fun close()
}

/** 기기 안의 방 — 혼자 연습(봇)과 단위 테스트에 쓴다 */
class MemoryRoomStore(initial: OmokRoom = OmokRoom()) : OmokRoomStore {
    private val _room = MutableStateFlow(initial)
    override val room: StateFlow<OmokRoom> = _room.asStateFlow()
    override val error: StateFlow<String?> = MutableStateFlow(null)

    override fun claimSeat(seat: Seat, player: Player, onResult: (Boolean) -> Unit) {
        val now = _room.value.player(seat)
        val ok = now == null || now.id == player.id
        if (ok) _room.update { if (seat == Seat.A) it.copy(a = player) else it.copy(b = player) }
        onResult(ok)
    }

    override fun releaseSeat(seat: Seat, player: Player) {
        _room.update {
            if (it.player(seat)?.id != player.id) it else if (seat == Seat.A) it.copy(a = null) else it.copy(b = null)
        }
    }

    override fun putGame(game: OmokGame) {
        _room.update { it.copy(game = game) }
    }

    override fun putMove(gameId: Long, index: Int, cell: Int) {
        _room.update { r ->
            val g = r.game
            if (g == null || g.id != gameId || index > g.moves.size) r
            else r.copy(game = g.copy(moves = g.moves.take(index) + cell))
        }
    }

    override fun putResign(gameId: Long, seat: Seat) {
        _room.update { r -> if (r.game?.id == gameId) r.copy(game = r.game.copy(resign = seat)) else r }
    }

    override fun putTimeout(gameId: Long, seat: Seat) {
        _room.update { r -> if (r.game?.id == gameId) r.copy(game = r.game.copy(timeout = seat)) else r }
    }

    override fun close() {}
}

/**
 * 방에 앉은 한 사람의 행동 — 자리 잡기, 새 판 열기, 돌 놓기, 기권, 한 판 더, 나가기.
 * 방 상태가 바뀔 때마다 sync 를 불러 줘야 한다.
 */
class OmokSession(
    private val store: OmokRoomStore,
    val me: Player,
    private val newGameId: () -> Long = System::currentTimeMillis,
) {
    var seat: Seat? = null
        private set

    /** 빈 자리에 앉는다 — 방이 가득 찼으면 null */
    fun join(onResult: (Seat?) -> Unit) {
        store.claimSeat(Seat.A, me) { gotA ->
            if (gotA) {
                seated(Seat.A, onResult)
            } else {
                store.claimSeat(Seat.B, me) { gotB -> if (gotB) seated(Seat.B, onResult) else onResult(null) }
            }
        }
    }

    private fun seated(s: Seat, onResult: (Seat?) -> Unit) {
        seat = s
        onResult(s)
        sync(store.room.value)
    }

    /** 내가 지금 두고 있는 판 (다른 두 사람이 남기고 간 판은 내 판이 아니다) */
    private fun myGame(room: OmokRoom): OmokGame? {
        val s = seat ?: return null
        if (room.player(s)?.id != me.id) return null
        return room.game?.takeIf { it.idOf(s) == me.id }
    }

    /** 방 상태가 바뀌면 — 두 사람이 모였으면 새 판을 열고, 판 도중에 상대가 나갔으면 기권으로 끝낸다 */
    fun sync(room: OmokRoom) {
        val s = seat ?: return
        if (room.player(s)?.id != me.id) return
        val a = room.a
        val b = room.b
        val g = room.game
        if (a != null && b != null) {
            if (s == Seat.A && (g == null || g.aId != a.id || g.bId != b.id)) {
                store.putGame(OmokGame(newGameId(), Seat.A, a.id, b.id))
            }
        } else {
            val mine = myGame(room) ?: return
            if (outcomeOf(mine) == null) store.putResign(mine.id, s.other)
        }
    }

    /** 돌을 놓는다 — 지금 내가 둘 수 없으면 null, 둘 수 있으면 그 자리의 판정 */
    fun place(room: OmokRoom, cell: Int): Place? {
        val s = seat ?: return null
        val g = myGame(room) ?: return null
        if (room.player(s.other) == null) return null
        val view = replay(g.moves)
        if (outcomeOf(g, view) != null || view.turn != g.stoneOf(s)) return null
        val check = checkPlace(view.cells, cell, view.turn)
        if (check == Place.OK) store.putMove(g.id, view.count, cell)
        return check
    }

    /** 기권 — 판이 진행 중일 때만 */
    fun resign(room: OmokRoom): Boolean {
        val s = seat ?: return false
        val g = myGame(room) ?: return false
        if (outcomeOf(g) != null) return false
        store.putResign(g.id, s)
        return true
    }

    /** 제한시간 초과 — 내 차례에 시간을 다 썼을 때 스스로 패배를 적는다 */
    fun timeout(room: OmokRoom): Boolean {
        val s = seat ?: return false
        val g = myGame(room) ?: return false
        val view = replay(g.moves)
        if (outcomeOf(g, view) != null || view.turn != g.stoneOf(s)) return false
        store.putTimeout(g.id, s)
        return true
    }

    /** 한 판 더 — 끝난 판의 두 사람이 그대로 있을 때. 흑을 바꾼다 */
    fun rematch(room: OmokRoom): Boolean {
        val g = myGame(room) ?: return false
        val a = room.a ?: return false
        val b = room.b ?: return false
        if (outcomeOf(g) == null) return false
        store.putGame(OmokGame(newGameId(), g.black.other, a.id, b.id))
        return true
    }

    /** 나간다 — 판 도중이면 기권으로 끝내고 자리를 비운다 */
    fun leave(room: OmokRoom) {
        val s = seat ?: return
        resign(room)
        store.releaseSeat(s, me)
        seat = null
    }
}

// ───────── 랭킹 ─────────

/** 랭킹의 한 줄 */
data class RankEntry(val name: String, val win: Int, val lose: Int, val draw: Int = 0)

/** 이긴 판이 많은 순서 — 같으면 진 판이 적은 사람, 그다음은 이름 순서 */
fun sortRanking(list: List<RankEntry>): List<RankEntry> =
    list.sortedWith(compareByDescending<RankEntry> { it.win }.thenBy { it.lose }.thenByDescending { it.draw }.thenBy { it.name })

/** 랭킹에 올릴 만한 판인지 — 몇 수 두지 않고 끝난 판(바로 나감 등)은 세지 않는다 */
const val RANK_MIN_MOVES = 6

/** 랭킹이 놓인 곳 */
interface OmokRanking {
    /** @param result +1 = 승, 0 = 무, -1 = 패 */
    fun record(name: String, result: Int)

    /** 위에서부터 읽는다 — 읽지 못하면 null */
    fun load(onResult: (List<RankEntry>?) -> Unit)
}
