package kr.kw.libri

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "Cham"

/** local.properties 에 Firebase 값이 들어 있으면 온라인 모드 사용 가능 */
val firebaseConfigured: Boolean
    get() = BuildConfig.FIREBASE_DB_URL.isNotBlank() &&
        BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
        BuildConfig.FIREBASE_APP_ID.isNotBlank()

/**
 * Firebase Realtime Database 서버
 *  rooms/{방코드}/players/{A|B} : true (접속 중, 연결이 끊기면 자동 삭제)
 *  rooms/{방코드}/attacker      : "A" | "B"
 *  rooms/{방코드}/score/{A|B}   : 점수
 *  rooms/{방코드}/round         : { id, attacker, dir, startAt, result, look }
 */
class FirebaseChamServer(app: Application, roomCode: String) : ChamServer {

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
            _state.value = parseRoom(s)
        }
        override fun onCancelled(e: DatabaseError) {
            Log.e(TAG, "room listen failed: ${e.message}")
        }
    }

    init {
        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setDatabaseUrl(BuildConfig.FIREBASE_DB_URL)
            .build()
        val fbApp = FirebaseApp.getApps(app).firstOrNull { it.name == "cham" }
            ?: FirebaseApp.initializeApp(app, options, "cham")
        db = FirebaseDatabase.getInstance(fbApp)
        val code = roomCode.filter { it.isLetterOrDigit() }.ifBlank { "temi" }   // . # $ [ ] 는 키에 못 쓴다
        room = db.getReference("rooms").child(code)
        db.getReference(".info/serverTimeOffset").addValueEventListener(offsetListener)
        room.addValueEventListener(roomListener)
    }

    override fun serverNow() = System.currentTimeMillis() + offset

    override fun join(seat: Seat) {
        mySeat = seat
        val me = room.child("players").child(seat.name)
        me.setValue(true)
        me.onDisconnect().removeValue()
    }

    override fun leave() {
        mySeat?.let { room.child("players").child(it.name).removeValue() }
        room.removeEventListener(roomListener)
        db.getReference(".info/serverTimeOffset").removeEventListener(offsetListener)
    }

    override fun startRound(attacker: Seat, dir: Dir) {
        val now = serverNow()
        room.child("round").setValue(
            mapOf("id" to now, "attacker" to attacker.name, "dir" to dir.name, "startAt" to now + LEAD_MS)
        )
    }

    override fun reportResult(roundId: Long, look: HeadDir, hit: Boolean) {
        val ns = applyResult(_state.value, roundId, look, hit) ?: return
        room.updateChildren(
            mapOf<String, Any?>(
                "round/result" to ns.round?.result,
                "round/look" to look.name,
                "attacker" to ns.attacker.name,
                "score/A" to ns.scoreA,
                "score/B" to ns.scoreB,
            )
        )
    }

    override fun reset() {
        room.updateChildren(
            mapOf<String, Any?>("attacker" to "A", "score/A" to 0, "score/B" to 0, "round" to null)
        )
    }

    private fun parseRoom(s: DataSnapshot): RoomState {
        fun num(snap: DataSnapshot) = (snap.value as? Number)?.toLong()
        val r = s.child("round")
        val round = if (r.exists()) runCatching {
            Round(
                id = num(r.child("id"))!!,
                attacker = Seat.valueOf(r.child("attacker").value as String),
                dir = Dir.valueOf(r.child("dir").value as String),
                startAt = num(r.child("startAt"))!!,
                result = r.child("result").value as? String,
                look = r.child("look").value as? String,
            )
        }.getOrNull() else null
        val attacker = (s.child("attacker").value as? String)
            ?.let { runCatching { Seat.valueOf(it) }.getOrNull() } ?: Seat.A
        return RoomState(
            attacker = attacker,
            scoreA = num(s.child("score").child("A"))?.toInt() ?: 0,
            scoreB = num(s.child("score").child("B"))?.toInt() ?: 0,
            round = round,
            joinedA = s.child("players").child("A").exists(),
            joinedB = s.child("players").child("B").exists(),
        )
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
