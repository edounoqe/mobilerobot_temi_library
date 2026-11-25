package kr.kw.libri.chamchamcham

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import kr.kw.libri.LibriFirebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "Cham"

internal enum class OnlineRoomStatus {
    CONNECTING, READY, DISCONNECTED, PERMISSION_DENIED, FAILED, TIMED_OUT, SEAT_TAKEN,
}

/** Google Services 설정이 로드되었는지 확인한다. 네트워크 연결 보장은 아니다. */
fun firebaseConfigured(app: Application): Boolean =
    FirebaseApp.getApps(app).any { it.name == FirebaseApp.DEFAULT_APP_NAME }

/**
 * Firebase Realtime Database 서버
 *  rooms/{방코드}/players/{A|B}/{입장 세션 ID} : true
 *  rooms/{방코드}/attacker      : "A" | "B"
 *  rooms/{방코드}/score/{A|B}   : 점수
 *  rooms/{방코드}/round         : { id, attacker, dir, startAt, result, look }
 */
class FirebaseChamServer(app: Application, roomCode: String) : ChamServer {

    private val _status = MutableStateFlow(OnlineRoomStatus.CONNECTING)
    internal val status = _status.asStateFlow()
    @Volatile private var active = true
    private val sessionId = UUID.randomUUID().toString().replace("-", "")
    private var connected = false
    private var roomRead = false
    private var seatWritten = false
    private var registering = false

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (!active) return
            connected = snapshot.value == true
            if (connected) registerSeat()
            else if (_status.value == OnlineRoomStatus.READY || registering) {
                _status.value = OnlineRoomStatus.DISCONNECTED
            }
        }
        override fun onCancelled(error: DatabaseError) = fail(error)
    }

    private val _state = MutableStateFlow(RoomState())
    override val state: StateFlow<RoomState> = _state.asStateFlow()

    @Volatile private var offset = 0L   // 서버 시각 - 기기 시각 (두 기기의 "참" 타이밍을 맞춘다)
    private var mySeat: Seat? = null

    private val db: FirebaseDatabase
    private val room: DatabaseReference

    private val offsetListener = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            offset = (s.value as? Number)?.toLong() ?: 0L
        }
        override fun onCancelled(e: DatabaseError) {}
    }
    private val roomListener = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            if (!active) return
            _state.value = parseRoom(s)
            val seat = mySeat
            if (seat != null && _status.value == OnlineRoomStatus.READY &&
                !s.child("players").child(seat.name).child(sessionId).exists()) {
                _status.value = OnlineRoomStatus.DISCONNECTED
            }
            if (seat != null && s.child("round").exists() &&
                !s.child("players").child(seat.other.name).exists()) {
                changeGame(requireOpponent = false) { data, currentSeat ->
                    if (data.child("players").child(currentSeat.other.name).value != null ||
                        data.child("round").value == null) return@changeGame false
                    data.child("round").value = null
                    true
                }
            }
            roomRead = true
            markReady()
        }
        override fun onCancelled(e: DatabaseError) = fail(e)
    }

    init {
        db = LibriFirebase.realtimeDatabase(app)
        val code = roomCode.filter { it.isLetterOrDigit() }.ifBlank { "temi" }   // . # $ [ ] 는 키에 못 쓴다
        room = db.getReference("rooms").child(code)
        db.getReference(".info/serverTimeOffset").addValueEventListener(offsetListener)
        room.addValueEventListener(roomListener)
        db.getReference(".info/connected").addValueEventListener(connectionListener)
    }

    override fun serverNow() = System.currentTimeMillis() + offset

    override fun join(seat: Seat) {
        mySeat = seat
        registerSeat()
    }

    private fun registerSeat() {
        val seat = mySeat ?: return
        if (!active || !connected || registering || _status.value != OnlineRoomStatus.CONNECTING) return
        registering = true
        val me = sessionReference(seat)
        me.onDisconnect().removeValue { error, _ ->
            if (error != null) fail(error)
            else if (active && _status.value == OnlineRoomStatus.CONNECTING && connected) {
                room.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData): Transaction.Result {
                        if (!active || _status.value != OnlineRoomStatus.CONNECTING) return Transaction.abort()
                        val players = data.child("players")
                        val chosen = players.child(seat.name)
                        if (chosen.value != null) return Transaction.abort()
                        if (players.child(seat.other.name).value == null) {
                            data.child("attacker").value = Seat.A.name
                            data.child("score").value = mapOf("A" to 0, "B" to 0)
                        }
                        // A new participant must never continue a previous participant's round.
                        data.child("round").value = null
                        chosen.child(sessionId).value = true
                        return Transaction.success(data)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                        if (!active || _status.value != OnlineRoomStatus.CONNECTING) {
                            if (committed) me.removeValue()
                            return
                        }
                        if (error != null) fail(error)
                        else if (committed) {
                            seatWritten = true
                            markReady()
                        } else {
                            _status.value = OnlineRoomStatus.SEAT_TAKEN
                        }
                    }
                }, false)
            }
        }
    }

    private fun sessionReference(seat: Seat) =
        room.child("players").child(seat.name).child(sessionId)

    private fun markReady() {
        if (connected && roomRead && seatWritten && _status.value == OnlineRoomStatus.CONNECTING) {
            _status.value = OnlineRoomStatus.READY
        }
    }

    private fun fail(error: DatabaseError) {
        if (!active) return
        Log.w(TAG, "Firebase room operation failed (${error.code})")
        _status.value = when (error.code) {
            DatabaseError.PERMISSION_DENIED -> OnlineRoomStatus.PERMISSION_DENIED
            DatabaseError.DISCONNECTED, DatabaseError.NETWORK_ERROR -> OnlineRoomStatus.DISCONNECTED
            else -> OnlineRoomStatus.FAILED
        }
    }

    override fun leave() {
        if (!active) return
        active = false
        if (seatWritten) mySeat?.let { sessionReference(it).removeValue() }
        room.removeEventListener(roomListener)
        db.getReference(".info/serverTimeOffset").removeEventListener(offsetListener)
        db.getReference(".info/connected").removeEventListener(connectionListener)
    }

    override fun startRound(attacker: Seat, dir: Dir) {
        if (mySeat != attacker) return
        val roundId = serverNow()
        changeGame(requireOpponent = true) { data, _ ->
            val currentAttacker = data.child("attacker").value ?: Seat.A.name
            val round = data.child("round")
            val previousStart = (round.child("startAt").value as? Number)?.toLong()
            if (currentAttacker != attacker.name ||
                (round.value != null && (round.child("result").value == null ||
                    (previousStart != null && serverNow() < previousStart + JUDGE_END + RESULT_SHOW_MS)))) {
                return@changeGame false
            }
            round.value = mapOf("id" to roundId, "attacker" to attacker.name, "dir" to dir.name,
                "startAt" to serverNow() + LEAD_MS)
            true
        }
    }

    override fun reportResult(roundId: Long, look: HeadDir, hit: Boolean) {
        changeGame(requireOpponent = true) { data, seat ->
            val roundNode = data.child("round")
            val round = readRound(roundNode.value) ?: return@changeGame false
            if (round.attacker != seat.other) return@changeGame false
            val score = data.child("score")
            val previous = RoomState(
                attacker = round.attacker,
                scoreA = (score.child("A").value as? Number)?.toInt() ?: 0,
                scoreB = (score.child("B").value as? Number)?.toInt() ?: 0,
                round = round,
            )
            val updated = applyResult(previous, roundId, look, hit) ?: return@changeGame false
            val finished = requireNotNull(updated.round)
            roundNode.child("result").value = finished.result
            roundNode.child("look").value = finished.look
            if (updated.scoreA != previous.scoreA) score.child("A").value = updated.scoreA
            if (updated.scoreB != previous.scoreB) score.child("B").value = updated.scoreB
            if (updated.attacker != previous.attacker) {
                data.child("attacker").value = updated.attacker.name
            }
            true
        }
    }

    private fun changeGame(requireOpponent: Boolean, change: (MutableData, Seat) -> Boolean) {
        val seat = mySeat ?: return
        if (_status.value != OnlineRoomStatus.READY) return
        room.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val players = data.child("players")
                if (!active || _status.value != OnlineRoomStatus.READY ||
                    players.child(seat.name).child(sessionId).value != true ||
                    (requireOpponent && players.child(seat.other.name).value == null)) return Transaction.abort()
                return if (change(data, seat)) Transaction.success(data) else Transaction.abort()
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) fail(error)
            }
        }, false)
    }

    override fun reset() {
        changeGame(requireOpponent = false) { data, _ ->
            data.child("attacker").value = Seat.A.name
            data.child("score").value = mapOf("A" to 0, "B" to 0)
            data.child("round").value = null
            true
        }
    }

    private fun parseRoom(s: DataSnapshot): RoomState {
        fun num(snap: DataSnapshot) = (snap.value as? Number)?.toLong()
        val joinedA = s.child("players").child("A").exists()
        val joinedB = s.child("players").child("B").exists()
        val round = if (joinedA && joinedB) readRound(s.child("round").value) else null
        val attacker = (s.child("attacker").value as? String)
            ?.let { runCatching { Seat.valueOf(it) }.getOrNull() } ?: Seat.A
        return RoomState(
            attacker = attacker,
            scoreA = num(s.child("score").child("A"))?.toInt() ?: 0,
            scoreB = num(s.child("score").child("B"))?.toInt() ?: 0,
            round = round,
            joinedA = joinedA,
            joinedB = joinedB,
        )
    }

    private fun readRound(value: Any?): Round? {
        val fields = value as? Map<*, *> ?: return null
        val id = (fields["id"] as? Number)?.toLong() ?: return null
        val attacker = Seat.entries.find { it.name == fields["attacker"] } ?: return null
        val direction = Dir.entries.find { it.name == fields["dir"] } ?: return null
        val startAt = (fields["startAt"] as? Number)?.toLong() ?: return null
        return Round(id, attacker, direction, startAt, fields["result"] as? String, fields["look"] as? String)
    }
}

/**
 * 혼자 테스트용 서버 — 상대 자리를 봇이 맡는다 (Firebase 설정 없이 AVD 한 대로 확인)
 *  봇이 공격: 랜덤 방향으로 라운드 시작 / 봇이 수비: 랜덤으로 고개를 돌린 것으로 판정
 */
class LocalChamServer(private val scope: CoroutineScope, private val bot: Seat) : ChamServer {

    private val _state = MutableStateFlow(RoomState())
    override val state: StateFlow<RoomState> = _state.asStateFlow()
    private var botJob: Job? = null
    private var joinedAt = 0L

    override fun serverNow() = System.currentTimeMillis()

    override fun join(seat: Seat) {
        joinedAt = serverNow()
        _state.value = RoomState(joinedA = true, joinedB = true)
        botJob = scope.launch {
            while (isActive) {
                botStep()
                delay(100)
            }
        }
    }

    override fun leave() {
        botJob?.cancel()
    }

    override fun startRound(attacker: Seat, dir: Dir) {
        val now = serverNow()
        _state.update { it.copy(round = Round(id = now, attacker = attacker, dir = dir, startAt = now + LEAD_MS)) }
    }

    override fun reportResult(roundId: Long, look: HeadDir, hit: Boolean) {
        _state.update { applyResult(it, roundId, look, hit) ?: it }
    }

    override fun reset() {
        _state.update { RoomState(joinedA = true, joinedB = true) }
    }

    private fun botStep() {
        val s = _state.value
        val now = serverNow()
        val r = s.round
        // 봇 차례 공격 — 이전 결과를 다 보여 준 뒤
        val idle = r == null || (r.result != null && now > r.startAt + JUDGE_END + RESULT_SHOW_MS + 800)
        if (s.attacker == bot && idle && now - joinedAt > 1500) {
            startRound(bot, if (Math.random() < 0.5) Dir.LEFT else Dir.RIGHT)
            return
        }
        // 봇이 수비 — 판정 시각이 지나면 랜덤으로 고개를 돌린 것으로 처리
        if (r != null && r.attacker != bot && r.result == null && now > r.startAt + JUDGE_END) {
            val look = if (Math.random() < 0.5) HeadDir.LEFT else HeadDir.RIGHT
            reportResult(r.id, look, look.name == r.dir.name)
        }
    }
}
