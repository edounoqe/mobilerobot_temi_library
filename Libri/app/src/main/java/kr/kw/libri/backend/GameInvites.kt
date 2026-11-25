package kr.kw.libri.backend

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.UUID

/*
 * 기기 간 게임 신청 — 멀티플레이를 누른 기기가 다른 기기를 골라 신청하고,
 * 받은 기기는 어느 화면에 있든 수락/거절 팝업을 띄운다. 수락하면 두 기기가 같은 게임방에 들어간다.
 *
 *  Firebase
 *   robots/{robotId}                   : 각 기기의 접속 상태 (RobotPresence 가 쓴다)
 *   requests/pair_temi_L_temi_1        : 진행 중인 신청 하나 { fromRobot, toRobot, roomCode, type, status, createdAt, respondedAt }
 *     status : pending → accepted | rejected | cancelled   (pending 은 PAIRING_TIMEOUT_MS 가 지나면 만료)
 *     type   : 어느 게임인지 (InviteGame.type)
 *
 *  신청은 한 번에 하나 — 두 기기가 동시에 보내도 트랜잭션이 하나만 받아들인다.
 */

/** 신청할 수 있는 게임 */
enum class InviteGame(val type: String, val title: String) {
    CHAM("chamchamcham", "참참참"),
    OMOK("omok", "오목");

    companion object {
        fun of(type: String?): InviteGame? = entries.firstOrNull { it.type == type }
    }
}

/** 운영하는 기기 목록 — 꺼져 있어도 목록에는 보이게 한다 */
val KNOWN_ROBOTS = listOf("temi_L" to "L", "temi_1" to "1")

fun floorLabel(robotId: String): String = (KNOWN_ROBOTS.firstOrNull { it.first == robotId }?.second ?: robotId) + "층 로봇"

/** 다른 기기의 지금 상태 */
data class RobotPeer(val id: String, val online: Boolean, val service: String?) {
    /** 지금 신청을 받을 수 있는지 — 켜져 있고, 실제 게임 중이나 관리자 설정 화면이 아닐 때 */
    val available: Boolean get() = online && service != SERVICE_PLAYING && service != "settings"

    val stateLabel: String
        get() = when {
            !online -> "꺼져 있음"
            service == SERVICE_PLAYING -> "게임 중"
            service == "settings" -> "관리자 설정 중"
            service == null || service == "idle" -> "대기 중"
            else -> "다른 화면 사용 중"
        }
}

/** 실제 게임 중인 기기가 알리는 currentService 값 — 이 동안은 신청을 받지 않는다 */
const val SERVICE_PLAYING = "playing"

data class Invite(
    val room: String,
    val from: String,
    val to: String,
    val game: InviteGame,
    val status: String,
    val createdAt: Long,
)

/** 신청이 성사됐다 — 이 게임방에 이 자리(A = 신청한 쪽, B = 받은 쪽)로 들어간다 */
data class InviteMatch(val game: InviteGame, val room: String, val asHost: Boolean, val id: Long = System.nanoTime())

/** 앱 어디서든 신청 상태를 보는 통로 (MainActivity 가 채운다. 승인 전·폰에서는 null) */
val LocalGameInvites = staticCompositionLocalOf<GameInvites?> { null }

/**
 * 신청 상태와 동작. MainActivity 가 하나 만들어 start/stop 한다.
 * 상태는 Compose state 라서 화면이 그대로 읽으면 된다.
 */
class GameInvites(context: Context, val me: RobotIdentity, private val onMatch: (InviteMatch) -> Unit) {
    private val backend = LibriBackend(context)

    var peers by mutableStateOf(KNOWN_ROBOTS.filter { it.first != me.id }.map { RobotPeer(it.first, false, null) })
        private set
    /** 지금 요청 노드에 있는 신청 (끝난 것 포함) */
    var invite by mutableStateOf<Invite?>(null)
        private set
    /** 내가 보내고 답을 기다리는 신청의 방 코드 */
    var outgoing by mutableStateOf<Invite?>(null)
        private set
    var connected by mutableStateOf(false)
        private set
    /** 전송·응답을 저장하는 중 */
    var saving by mutableStateOf(false)
        private set
    /** 마지막 결과 안내 ("거절했어요" 등). 화면이 보여 주고 clearMessage 로 지운다 */
    var message by mutableStateOf<String?>(null)
        private set
    /** 내 신청이 거절되거나 응답이 없었을 때 띄울 팝업 (제목, 설명). 확인을 누르면 dismissNotice */
    var notice by mutableStateOf<Pair<String, String>?>(null)
        private set

    private var offset = 0L
    fun now(): Long = System.currentTimeMillis() + offset

    private var refs: List<Pair<com.google.firebase.database.Query, ValueEventListener>> = emptyList()
    private var active = false

    /** 나에게 온, 아직 답하지 않은 신청 */
    fun incoming(now: Long = now()): Invite? = invite?.takeIf {
        it.to == me.id && it.status == "pending" && now - it.createdAt < PAIRING_TIMEOUT_MS
    }

    fun start() {
        if (active) return
        active = true
        fun listener(onData: (DataSnapshot) -> Unit) = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { if (active) onData(snapshot) }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listen failed: ${error.message}")
                if (active) message = "게임 신청 정보를 읽지 못했어요. Firebase 승인을 확인해 주세요."
            }
        }
        val robots = listener { s ->
            peers = KNOWN_ROBOTS.filter { it.first != me.id }.map { (id, _) ->
                val p = s.child(id)
                RobotPeer(id, p.child("online").value == true, p.child("currentService").value as? String)
            }
        }
        val request = listener { s -> onRequest(s) }
        val conn = listener { connected = it.value == true }
        val clock = listener { offset = (it.value as? Number)?.toLong() ?: 0L }
        val list = mutableListOf<Pair<com.google.firebase.database.Query, ValueEventListener>>()
        runCatching {
            list += backend.observeRobots(robots) to robots
            list += backend.pairingRequest().also { it.addValueEventListener(request) } to request
            list += backend.pairingConnection().also { it.addValueEventListener(conn) } to conn
            list += backend.pairingTimeOffset().also { it.addValueEventListener(clock) } to clock
        }.onFailure { Log.e(TAG, "start failed", it); message = "게임 신청 기능을 시작하지 못했어요." }
        refs = list
    }

    fun stop() {
        if (!active) return
        outgoing?.let { cancel() }
        active = false
        refs.forEach { (ref, l) -> ref.removeEventListener(l) }
        refs = emptyList()
        invite = null
        outgoing = null
        saving = false
        connected = false
    }

    private fun onRequest(s: DataSnapshot) {
        val room = s.child("roomCode").value as? String
        val from = s.child("fromRobot").value as? String
        val to = s.child("toRobot").value as? String
        val game = InviteGame.of(s.child("type").value as? String)
        val status = s.child("status").value as? String
        val created = (s.child("createdAt").value as? Number)?.toLong()
        val inv = if (room != null && from != null && to != null && game != null && status != null && created != null) {
            Invite(room, from, to, game, status, created)
        } else null
        invite = inv
        // 내가 보낸 신청의 답
        val out = outgoing ?: return
        if (inv == null || inv.room != out.room) return
        when (inv.status) {
            "accepted" -> { outgoing = null; onMatch(InviteMatch(inv.game, inv.room, asHost = true)) }
            "rejected" -> { outgoing = null; notice = "대결 신청이 거절됐어요" to "${floorLabel(inv.to)}이(가) ${inv.game.title} 대결을 거절했어요." }
            "cancelled" -> { outgoing = null }
        }
    }

    /** 만료 확인 — 화면이 주기적으로 부른다 (pending 이 시간을 넘기면 정리) */
    fun tick(now: Long = now()) {
        val out = outgoing ?: return
        if (now - out.createdAt >= PAIRING_TIMEOUT_MS + 3_000L) {
            outgoing = null
            notice = "응답이 없어요" to "${floorLabel(out.to)}이(가) ${out.game.title} 대결 신청에 답하지 않았어요."
        }
    }

    /** [to] 기기에 [game] 대결을 신청한다 */
    fun send(game: InviteGame, to: String) {
        if (saving || outgoing != null) return
        val room = "invite" + UUID.randomUUID().toString().replace("-", "")
        val now = now()
        saving = true
        message = null
        outgoing = Invite(room, me.id, to, game, "pending", now)
        backend.sendPairingRequest(me.id, to, room, game.type, now) { ok, error ->
            saving = false
            if (!ok && outgoing?.room == room) {
                outgoing = null
                message = if (error != null) "신청을 보내지 못했어요. 연결을 확인해 주세요." else "다른 신청이 진행 중이에요. 잠시 후 다시 해 주세요."
            }
        }
    }

    /** 보낸 신청을 취소한다 */
    fun cancel() {
        val out = outgoing ?: return
        outgoing = null
        backend.finishPairingRequest(out.room, me.id, "cancelled", now()) { _, _ -> }
    }

    /** 받은 신청에 답한다 — 수락하면 그 게임으로 들어간다 */
    fun respond(accept: Boolean) {
        val inv = incoming() ?: return
        if (saving) return
        saving = true
        backend.finishPairingRequest(inv.room, me.id, if (accept) "accepted" else "rejected", now()) { ok, error ->
            saving = false
            when {
                ok && accept -> onMatch(InviteMatch(inv.game, inv.room, asHost = false))
                ok -> Unit
                error != null -> message = "응답을 보내지 못했어요. 연결을 확인해 주세요."
                else -> message = "이미 끝난 신청이에요."
            }
        }
    }

    fun clearMessage() { message = null }

    fun dismissNotice() { notice = null }

    private companion object { const val TAG = "GameInvites" }
}
