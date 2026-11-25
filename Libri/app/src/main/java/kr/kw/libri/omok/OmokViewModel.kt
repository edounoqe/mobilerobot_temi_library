package kr.kw.libri.omok

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

const val NAME_MAX = 8                      // 이름 글자 수
private const val JOIN_TIMEOUT_MS = 9000L   // 이 안에 자리를 못 잡으면 연결 실패로 본다
private const val RANK_TIMEOUT_MS = 10000L
private const val BOT_THINK_MS = 750L       // 봇이 두기 전에 뜸 들이는 시간


enum class OmokMode { BOT, ONLINE }

sealed interface RankState {
    data object Loading : RankState
    data object Failed : RankState
    data class Loaded(val list: List<RankEntry>) : RankState
}

/**
 * 오목 진행 담당
 *  - 방(Firebase 또는 기기 안)의 상태를 화면에 전달하고, 화면의 조작을 OmokSession 에 넘긴다
 *  - 혼자 연습이면 상대 자리에 봇을 앉혀 대신 둔다
 *  - 기기 2대 대결이 끝나면 내 승패를 랭킹에 올린다
 */
class OmokViewModel(app: Application) : AndroidViewModel(app) {

    /** 이 기기(앱 실행)의 id — 자리 주인을 가리는 데 쓴다 */
    val myId: String = UUID.randomUUID().toString()

    /** 대기실에 미리 적어 두는 이름 — 로봇 앞에서 글자를 치지 않아도 되게 하나 지어 준다 */
    val suggestedName: String = listOf("책벌레", "책갈피", "부엉이", "다람쥐", "고양이", "연필", "밑줄", "도토리").random() + (10..99).random()

    private var store: OmokRoomStore? = null
    private var session: OmokSession? = null
    private var roomJob: Job? = null
    private var botJob: Job? = null
    private var botFor = ""                  // 봇이 두려고 생각 중인 (판, 수)
    private val recorded = HashSet<Long>()   // 랭킹에 이미 올린 판

    private val ranking: OmokRanking by lazy { FirestoreOmokRanking() }

    private val _seat = MutableStateFlow<Seat?>(null)
    val seat: StateFlow<Seat?> = _seat.asStateFlow()

    private val _room = MutableStateFlow(OmokRoom())
    val room: StateFlow<OmokRoom> = _room.asStateFlow()

    private var botLevel = BotLevel.NORMAL

    private val _mode = MutableStateFlow(OmokMode.BOT)
    val mode: StateFlow<OmokMode> = _mode.asStateFlow()

    private val _joining = MutableStateFlow(false)
    val joining: StateFlow<Boolean> = _joining.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _roomName = MutableStateFlow("")
    val roomName: StateFlow<String> = _roomName.asStateFlow()

    private val _myName = MutableStateFlow("")
    val myName: StateFlow<String> = _myName.asStateFlow()

    private val _rank = MutableStateFlow<RankState?>(null)      // null = 랭킹 화면을 열지 않음
    val rank: StateFlow<RankState?> = _rank.asStateFlow()

    /**
     * 방에 들어간다 — 혼자 연습이면 봇이 기다리는 방, 기기 2대 대결이면 같은 방 이름의 Firebase 방
     * @param level 혼자 연습할 때의 봇 난이도
     */
    fun start(mode: OmokMode, name: String, roomName: String, level: BotLevel = BotLevel.NORMAL) {
        leave()
        botLevel = level
        _error.value = null
        val me = Player(myId, name.trim().take(NAME_MAX).ifBlank { "손님" })
        val st: OmokRoomStore = try {
            if (mode == OmokMode.ONLINE) FirebaseRoomStore(getApplication(), roomName) else MemoryRoomStore(OmokRoom(b = Player("bot", "봇 · ${level.label}")))
        } catch (e: Exception) {
            Log.e("Omok", "open room failed", e)
            _error.value = "서버를 열지 못했어요. (${e.message})"
            return
        }
        val se = OmokSession(st, me)
        store = st
        session = se
        _mode.value = mode
        _myName.value = me.name
        _roomName.value = roomName
        _joining.value = true
        roomJob = viewModelScope.launch {
            launch {
                st.room.collect { r ->
                    _room.value = r
                    se.sync(r)
                    botStep(r)
                    recordIfOver(r)
                }
            }
            launch { st.error.collect { msg -> if (msg != null) fail(msg) } }
            launch {
                delay(JOIN_TIMEOUT_MS)
                if (_seat.value == null) fail("서버와 연결하지 못했어요. 인터넷 연결을 확인해 주세요.")
            }
        }
        se.join { got ->
            if (session !== se) return@join             // 그 사이에 나갔다
            if (got == null) {
                if (_error.value == null) fail("방이 가득 찼어요. 다른 방 이름으로 들어가 주세요.")
            } else {
                _seat.value = got
                _joining.value = false
            }
        }
    }

    private fun fail(message: String) {
        leave()
        _error.value = message
    }

    /** 방에서 나간다 — 판 도중이면 기권으로 끝난다 */
    fun leave() {
        val se = session
        val st = store
        if (se != null && st != null) {
            val r = st.room.value
            // 기권으로 끝나는 판의 패배는 방 상태가 돌아오기 전에 직접 올린다
            val g = r.game
            val s = se.seat
            if (_mode.value == OmokMode.ONLINE && s != null && g != null && g.idOf(s) == myId && outcomeOf(g) == null) {
                submit(g, -1)
            }
            se.leave(r)
        }
        roomJob?.cancel()
        botJob?.cancel()
        st?.close()
        store = null
        session = null
        _seat.value = null
        _joining.value = false
        _room.value = OmokRoom()
    }

    /** 돌을 놓는다 — 둘 수 없는 차례면 null */
    fun place(cell: Int): Place? = session?.place(_room.value, cell)

    fun resign() {
        session?.resign(_room.value)
    }

    fun rematch() {
        session?.rematch(_room.value)
    }

    // ── 봇 ──

    private fun botStep(r: OmokRoom) {
        if (_mode.value != OmokMode.BOT) return
        val g = r.game ?: return
        val view = replay(g.moves)
        if (outcomeOf(g, view) != null || view.turn != g.stoneOf(Seat.B)) return
        val key = "${g.id}-${view.count}"
        if (key == botFor && botJob?.isActive == true) return
        botFor = key
        botJob?.cancel()
        botJob = viewModelScope.launch {
            delay(BOT_THINK_MS)
            val cell = withContext(Dispatchers.Default) { OmokBot.pick(view.cells.copyOf(), view.turn, botLevel) }
            val now = store?.room?.value?.game
            if (cell >= 0 && now != null && now.id == g.id && now.moves.size == view.count && now.resign == null) {
                store?.putMove(g.id, view.count, cell)
            }
        }
    }

    // ── 랭킹 ──

    private fun recordIfOver(r: OmokRoom) {
        if (_mode.value != OmokMode.ONLINE) return
        val s = _seat.value ?: return
        val g = r.game ?: return
        if (g.idOf(s) != myId) return
        val out = outcomeOf(g) ?: return
        submit(g, if (out.winner == null) 0 else if (out.winner == s) 1 else -1)
    }

    /** 한 판에 한 번만, 몇 수 이상 둔 판만 올린다 */
    private fun submit(g: OmokGame, result: Int) {
        if (!recorded.add(g.id) || g.moves.size < RANK_MIN_MOVES) return
        ranking.record(_myName.value, result)
    }

    fun openRanking() {
        _rank.value = RankState.Loading
        var done = false
        ranking.load { list ->
            done = true
            if (_rank.value != null) _rank.value = if (list == null) RankState.Failed else RankState.Loaded(sortRanking(list))
        }
        viewModelScope.launch {
            delay(RANK_TIMEOUT_MS)
            if (!done && _rank.value == RankState.Loading) _rank.value = RankState.Failed
        }
    }

    /** 다음 사람에게 앞 사람의 오류 안내('방이 가득 찼어요' 등)가 남지 않게 */
    fun clearError() {
        _error.value = null
    }

    fun closeRanking() {
        _rank.value = null
    }

    override fun onCleared() {
        leave()
    }
}
