package kr.kw.libri.chamchamcham

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kr.kw.libri.backend.LibriBackend
import kr.kw.libri.backend.PAIRING_TIMEOUT_MS
import kr.kw.libri.backend.RobotIdentity
import java.util.UUID

private data class PairingInvite(val room: String, val from: String, val to: String, val status: String, val createdAt: Long)

@Composable
internal fun ChamPairing(identity: RobotIdentity, onAvailable: (Boolean) -> Unit, onJoin: (String, Seat) -> Unit) {
    val context = LocalContext.current.applicationContext
    val backend = remember(context) { LibriBackend(context) }
    val peerId = if (identity.id == "temi_L") "temi_1" else "temi_L"
    val peerFloor = if (peerId == "temi_1") "1층" else "L층"
    val availability by rememberUpdatedState(onAvailable)
    val join by rememberUpdatedState(onJoin)
    var invite by remember(identity) { mutableStateOf<PairingInvite?>(null) }
    var outgoing by remember(identity) { mutableStateOf<String?>(null) }
    var peerState by remember(identity) { mutableStateOf("확인 중") }
    var connected by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var delayed by remember { mutableStateOf(false) }
    var session by remember(identity) { mutableStateOf<Any?>(null) }

    LifecycleStartEffect(identity, backend) {
        session = Any()
        var active = true
        fun listener(onData: (DataSnapshot) -> Unit) = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (active) onData(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {
                if (active) message = "게임 요청 정보를 읽지 못했습니다. Firebase 접근 권한을 확인해주세요."
            }
        }
        val robots = listener { snapshot ->
            val peer = snapshot.child(peerId)
            peerState = when {
                peer.child("online").value != true -> "오프라인"
                peer.child("currentService").value == "cham_lobby" -> "요청 가능"
                peer.child("busy").value == true -> "사용 중"
                else -> "대기 중 · 참참참 대기실 입장 필요"
            }
        }
        val request = listener { snapshot ->
            ready = true
            val room = snapshot.child("roomCode").value as? String
            val from = snapshot.child("fromRobot").value as? String
            val to = snapshot.child("toRobot").value as? String
            val status = snapshot.child("status").value as? String
            val created = (snapshot.child("createdAt").value as? Number)?.toLong()
            invite = if (room != null && from != null && to != null && status != null && created != null)
                PairingInvite(room, from, to, status, created) else null
        }
        val connection = listener { connected = it.value == true }
        val clock = listener { offset = (it.value as? Number)?.toLong() ?: 0L }
        val robotsRef = backend.observeRobots(robots)
        val requestRef = backend.pairingRequest().also { it.addValueEventListener(request) }
        val connectionRef = backend.pairingConnection().also { it.addValueEventListener(connection) }
        val clockRef = backend.pairingTimeOffset().also { it.addValueEventListener(clock) }
        availability(true)
        onStopOrDispose {
            active = false
            session = null
            robotsRef.removeEventListener(robots)
            requestRef.removeEventListener(request)
            connectionRef.removeEventListener(connection)
            clockRef.removeEventListener(clock)
            outgoing?.let { backend.finishPairingRequest(it, identity.id, "cancelled", now) { _, _ -> } }
            outgoing = null
            invite = null
            connected = false
            ready = false
            saving = false
            availability(false)
        }
    }
    LaunchedEffect(offset) {
        while (true) {
            now = System.currentTimeMillis() + offset
            delay(500)
        }
    }
    LaunchedEffect(connected, ready, saving) {
        delayed = false
        if (!connected || !ready || saving) {
            delay(12_000)
            delayed = true
        }
    }
    val current = invite
    val pending = current?.takeIf { it.status == "pending" && now - it.createdAt < PAIRING_TIMEOUT_MS }
    LaunchedEffect(outgoing) {
        if (outgoing == null) return@LaunchedEffect
        delay(PAIRING_TIMEOUT_MS + 5_000L)
        outgoing = null
        saving = false
        message = "요청 시간이 지났습니다."
    }
    LaunchedEffect(current, pending, outgoing) {
        if (current == null || current.room != outgoing || current.from != identity.id) return@LaunchedEffect
        when {
            current.status == "accepted" -> {
                outgoing = null
                join(current.room, Seat.A)
            }
            current.status == "rejected" -> { outgoing = null; message = "상대 로봇이 요청을 거절했습니다." }
            current.status == "cancelled" -> { outgoing = null; message = "요청을 취소했습니다." }
            pending == null -> { outgoing = null; message = "요청 시간이 지났습니다." }
        }
    }

    Text("다른 층 로봇과 대결")
    Text(if (connected) "$peerFloor 로봇 · $peerState" else "Firebase 연결 대기 중")
    if (delayed) Text(
        if (saving) "요청 처리 응답이 늦어지고 있습니다. 뒤로 가거나 연결을 확인해주세요."
        else "Firebase 응답이 늦어지고 있습니다. 네트워크를 확인하거나 봇 모드를 이용해주세요."
    )
    if (message.isNotBlank()) Text(message)
    if (pending?.to == identity.id && pending.from == peerId) {
        Text("$peerFloor 로봇이 게임을 요청했습니다.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("accepted" to "수락", "rejected" to "거절").forEach { (status, label) ->
                OutlinedButton(enabled = connected && !saving, onClick = {
                    val operationSession = session ?: return@OutlinedButton
                    saving = true
                    backend.finishPairingRequest(pending.room, identity.id, status, now) { ok, error ->
                        if (session !== operationSession) return@finishPairingRequest
                        saving = false
                        if (ok && status == "accepted") join(pending.room, Seat.B)
                        else message = if (ok) "요청을 거절했습니다."
                        else if (error != null) "응답을 저장하지 못했습니다." else "이미 종료된 요청입니다."
                    }
                }) { Text(label) }
            }
        }
    } else if (outgoing != null) {
        Text("상대 로봇의 응답을 기다리는 중…")
        OutlinedButton(enabled = connected && !saving, onClick = {
            val room = outgoing ?: return@OutlinedButton
            val operationSession = session ?: return@OutlinedButton
            saving = true
            backend.finishPairingRequest(room, identity.id, "cancelled", now) { ok, error ->
                if (session !== operationSession) return@finishPairingRequest
                saving = false
                if (!ok && error != null) message = "요청을 취소하지 못했습니다."
            }
        }) { Text("요청 취소") }
    } else {
        Button(enabled = connected && ready && peerState == "요청 가능" && pending == null && !saving, onClick = {
            val operationSession = session ?: return@Button
            val room = "invite" + UUID.randomUUID().toString().replace("-", "")
            outgoing = room
            saving = true
            message = ""
            backend.sendPairingRequest(identity.id, peerId, room, now) { ok, error ->
                if (session !== operationSession || outgoing != room) return@sendPairingRequest
                saving = false
                if (!ok && outgoing == room) {
                    outgoing = null
                    message = if (error != null) "게임 요청을 보내지 못했습니다." else "다른 요청이 진행 중입니다. 잠시 후 다시 시도해주세요."
                }
            }
        }) { Text("${peerFloor}에 게임 요청") }
    }
}
