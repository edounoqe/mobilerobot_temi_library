package kr.kw.libri.backend

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import kr.kw.libri.LibriFirebase

/** Firebase operations used by robot pairing, feedback and usage screens. */
class LibriBackend(context: Context) {
    private val db = LibriFirebase.realtimeDatabase(context.applicationContext)
    private val firestore = LibriFirebase.firestore()

    private fun key(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid identifier" }
        return value
    }

    /** Call close when the service stops. Re-registers disconnect handling after reconnect. */
    fun observePresence(robotId: String, floor: String, onError: (String) -> Unit): RobotPresence {
        require(floor in setOf("L", "1"))
        return RobotPresence(db.getReference("robots").child(key(robotId)), db.getReference(".info/connected"), floor, onError)
    }

    fun sendRequest(from: String, to: String): Task<Void> {
        require(from != to)
        val fromId = key(from)
        val toId = key(to)
        val ref = db.getReference("requests").push()
        return ref.setValue(mapOf("fromRobot" to fromId, "toRobot" to toId,
            "type" to "chamchamcham", "status" to "pending", "createdAt" to ServerValue.TIMESTAMP))
    }

    /** Caller must remove the returned listener when the screen/service stops. */
    fun observeRobots(listener: ValueEventListener): DatabaseReference =
        db.getReference("robots").also { it.addValueEventListener(listener) }

    /** Receives request snapshots filtered for the destination robot. */
    fun observeIncomingRequests(robotId: String, listener: ValueEventListener): com.google.firebase.database.Query =
        db.getReference("requests").orderByChild("toRobot").equalTo(key(robotId))
            .also { it.addValueEventListener(listener) }

    fun pairingRequest(): DatabaseReference = db.getReference("requests").child("pair_temi_L_temi_1")

    fun pairingConnection(): DatabaseReference = db.getReference(".info/connected")

    fun pairingTimeOffset(): DatabaseReference = db.getReference(".info/serverTimeOffset")

    fun sendPairingRequest(from: String, to: String, room: String, now: Long, done: (Boolean, String?) -> Unit) {
        updatePairing(done) { data ->
            val createdAt = (data.child("createdAt").value as? Number)?.toLong()
            val respondedAt = (data.child("respondedAt").value as? Number)?.toLong()
            val status = data.child("status").value
            if ((status == "pending" && createdAt != null && now - createdAt < PAIRING_TIMEOUT_MS) ||
                (status == "accepted" && respondedAt != null && now - respondedAt < 10_000L)) {
                return@updatePairing false
            }
            data.value = mapOf("fromRobot" to key(from), "toRobot" to key(to), "roomCode" to room,
                "type" to "chamchamcham", "status" to "pending", "createdAt" to ServerValue.TIMESTAMP)
            true
        }
    }

    fun finishPairingRequest(room: String, actor: String, status: String, now: Long, done: (Boolean, String?) -> Unit) {
        require(status in setOf("accepted", "rejected", "cancelled"))
        updatePairing(done) { data ->
            val actorField = if (status == "cancelled") "fromRobot" else "toRobot"
            val createdAt = (data.child("createdAt").value as? Number)?.toLong()
            if (data.child("status").value != "pending" || data.child("roomCode").value != room ||
                data.child(actorField).value != actor || createdAt == null ||
                (status != "cancelled" && now - createdAt >= PAIRING_TIMEOUT_MS)) return@updatePairing false
            data.child("status").value = status
            data.child("respondedAt").value = ServerValue.TIMESTAMP
            true
        }
    }

    private fun updatePairing(done: (Boolean, String?) -> Unit, change: (MutableData) -> Boolean) {
        pairingRequest().runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result =
                if (change(data)) Transaction.success(data) else Transaction.abort()

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                done(committed, error?.message)
            }
        }, false)
    }

    /** Only pending requests can transition, even when clients respond concurrently. */
    fun respondToRequest(requestId: String, recipient: String, accept: Boolean, done: (Boolean, String?) -> Unit) {
        val recipientId = key(recipient)
        db.getReference("requests").child(key(requestId)).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (data.child("status").value != "pending" || data.child("toRobot").value != recipientId)
                    return Transaction.abort()
                data.child("status").value = if (accept) "accepted" else "rejected"
                data.child("respondedAt").value = ServerValue.TIMESTAMP
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                done(committed, error?.message)
            }
        }, false)
    }

    fun saveFeedback(rating: Int, service: String, robotId: String, floor: String): Task<DocumentReference> {
        require(rating in 1..5 && floor in setOf("L", "1"))
        return firestore.collection("feedback").add(mapOf("rating" to rating, "service" to key(service),
            "robotId" to key(robotId), "floor" to floor, "createdAt" to FieldValue.serverTimestamp()))
    }

    /** Immutable event documents can later be aggregated without concurrent counter loss. */
    fun recordUsage(service: String, robotId: String, floor: String, event: String): Task<DocumentReference> {
        require(floor in setOf("L", "1") && event in setOf("started", "completed"))
        return firestore.collection("usage_events").add(mapOf("service" to key(service), "robotId" to key(robotId),
            "floor" to floor, "event" to event, "createdAt" to FieldValue.serverTimestamp()))
    }
}

const val PAIRING_TIMEOUT_MS = 60_000L

class RobotPresence internal constructor(
    private val robot: DatabaseReference,
    private val connected: DatabaseReference,
    private val floor: String,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private var closed = false
    private var ready = false
    private var generation = 0
    private var service = "idle"
    private val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (closed) return
            if (snapshot.getValue(Boolean::class.java) != true) {
                ready = false
                generation++
                return
            }
            ready = false
            val currentGeneration = ++generation
            robot.onDisconnect().removeValue { error, _ ->
                if (error != null) onError(error.message)
                else if (!closed && currentGeneration == generation) {
                    ready = true
                    update(service)
                }
            }
        }
        override fun onCancelled(error: DatabaseError) = onError(error.message)
    }
    init { connected.addValueEventListener(listener) }

    fun update(currentService: String) {
        require(currentService.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        service = currentService
        if (closed || !ready) return
        robot.setValue(mapOf("floor" to floor, "online" to true, "busy" to (service != "idle"),
            "currentService" to service, "updatedAt" to ServerValue.TIMESTAMP))
            .addOnFailureListener { onError(it.message ?: "상태 저장 실패") }
    }

    override fun close() {
        if (closed) return
        closed = true
        ready = false
        generation++
        connected.removeEventListener(listener)
        // Keep onDisconnect registered in case this removal cannot reach the server.
        robot.removeValue().addOnFailureListener { onError(it.message ?: "상태 종료 실패") }
    }
}
