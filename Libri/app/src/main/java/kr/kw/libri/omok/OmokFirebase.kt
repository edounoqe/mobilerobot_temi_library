package kr.kw.libri.omok

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.kw.libri.LibriFirebase

private const val TAG = "Omok"

/**
 * 기기 2대 대결의 방 — Firebase Realtime Database (LibriFirebase 의 팀 공용 데이터베이스)
 *
 *  omok/rooms/{방 이름}/seats/{A|B} : { id, name }   (연결이 끊기면 자동으로 지워진다)
 *  omok/rooms/{방 이름}/game        : { id, black, a, b, moves: { 0: 칸, 1: 칸, … }, resign }
 *
 * 데이터베이스 규칙이 omok 아래의 읽기·쓰기를 허용해야 한다 (README 의 '오목' 참고).
 */
class FirebaseRoomStore(context: Context, roomName: String) : OmokRoomStore {

    private val _room = MutableStateFlow(OmokRoom())
    override val room: StateFlow<OmokRoom> = _room.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val ref: DatabaseReference
    private var mySeat: DatabaseReference? = null

    private val listener = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            _room.value = parseRoom(s)
        }

        override fun onCancelled(e: DatabaseError) {
            Log.e(TAG, "room listen failed: ${e.message}")
            _error.value = describe(e)
        }
    }

    init {
        // . # $ [ ] / 는 키에 쓸 수 없다
        val key = roomName.filter { it.isLetterOrDigit() }.ifBlank { "libri" }
        ref = LibriFirebase.realtimeDatabase(context).getReference("omok").child("rooms").child(key)
        ref.addValueEventListener(listener)
    }

    private fun describe(e: DatabaseError): String =
        if (e.code == DatabaseError.PERMISSION_DENIED) {
            "서버가 접근을 막고 있어요. Firebase 데이터베이스 규칙에서 omok 경로의 읽기·쓰기를 허용해 주세요."
        } else {
            "서버와 연결하지 못했어요. (${e.message})"
        }

    override fun claimSeat(seat: Seat, player: Player, onResult: (Boolean) -> Unit) {
        val seatRef = ref.child("seats").child(seat.name)
        seatRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                val owner = current.child("id").value as? String
                if (owner != null && owner != player.id) return Transaction.abort()
                current.value = mapOf("id" to player.id, "name" to player.name)
                return Transaction.success(current)
            }

            override fun onComplete(e: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (e != null) {
                    Log.e(TAG, "claim ${seat.name} failed: ${e.message}")
                    _error.value = describe(e)
                }
                if (committed) {
                    mySeat = seatRef
                    seatRef.onDisconnect().removeValue()
                }
                onResult(committed)
            }
        })
    }

    override fun releaseSeat(seat: Seat, player: Player) {
        val seatRef = ref.child("seats").child(seat.name)
        seatRef.onDisconnect().cancel()
        seatRef.removeValue()
        mySeat = null
    }

    override fun putGame(game: OmokGame) {
        ref.child("game").setValue(
            mapOf("id" to game.id, "black" to game.black.name, "a" to game.aId, "b" to game.bId),
        )
    }

    override fun putMove(gameId: Long, index: Int, cell: Int) {
        if (_room.value.game?.id != gameId) return
        ref.child("game").child("moves").child(index.toString()).setValue(cell)
    }

    override fun putResign(gameId: Long, seat: Seat) {
        if (_room.value.game?.id != gameId) return
        ref.child("game").child("resign").setValue(seat.name)
    }

    override fun close() {
        ref.removeEventListener(listener)
        // 자리를 비우지 못하고 닫히는 경우(오류 등)에도 남지 않게
        mySeat?.let {
            it.onDisconnect().cancel()
            it.removeValue()
        }
        mySeat = null
    }

    private fun parseRoom(s: DataSnapshot): OmokRoom {
        fun player(snap: DataSnapshot): Player? {
            val id = snap.child("id").value as? String ?: return null
            return Player(id, snap.child("name").value as? String ?: "손님")
        }
        val g = s.child("game")
        val game = if (g.exists()) runCatching {
            // moves 는 0, 1, 2 … 번 칸에 차례로 쌓인다 — 중간이 비어 있으면 그 앞까지만 읽는다
            val byIndex = HashMap<Int, Int>()
            for (m in g.child("moves").children) {
                val i = m.key?.toIntOrNull() ?: continue
                byIndex[i] = (m.value as Number).toInt()
            }
            val moves = ArrayList<Int>()
            while (true) moves += byIndex[moves.size] ?: break
            OmokGame(
                id = (g.child("id").value as Number).toLong(),
                black = Seat.valueOf(g.child("black").value as String),
                aId = g.child("a").value as String,
                bId = g.child("b").value as String,
                moves = moves,
                resign = (g.child("resign").value as? String)?.let { Seat.valueOf(it) },
            )
        }.getOrNull() else null
        return OmokRoom(a = player(s.child("seats").child("A")), b = player(s.child("seats").child("B")), game = game)
    }
}

/**
 * 랭킹 — Cloud Firestore 의 omok_ranking 모음. 문서 하나가 한 사람(이름)의 기록이다.
 *  omok_ranking/{이름} : { name, win, lose, draw, updatedAt }
 * 이름이 같으면 같은 기록에 쌓인다. 규칙이 omok_ranking 의 읽기·쓰기를 허용해야 한다.
 */
class FirestoreOmokRanking : OmokRanking {

    private fun collection() = LibriFirebase.firestore().collection("omok_ranking")

    override fun record(name: String, result: Int) {
        runCatching {
            val data = mapOf(
                "name" to name,
                "win" to FieldValue.increment(if (result > 0) 1L else 0L),
                "lose" to FieldValue.increment(if (result < 0) 1L else 0L),
                "draw" to FieldValue.increment(if (result == 0) 1L else 0L),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            collection().document(rankKey(name)).set(data, SetOptions.merge())
                .addOnFailureListener { Log.e(TAG, "ranking record failed: ${it.message}") }
        }.onFailure { Log.e(TAG, "ranking record failed: ${it.message}") }
    }

    override fun load(onResult: (List<RankEntry>?) -> Unit) {
        runCatching {
            // 기기에 남은 예전 목록이 아니라 서버의 지금 목록을 읽는다
            collection().orderBy("win", Query.Direction.DESCENDING).limit(50).get(Source.SERVER)
                .addOnSuccessListener { snap ->
                    onResult(
                        snap.documents.mapNotNull { d ->
                            val name = d.getString("name") ?: return@mapNotNull null
                            RankEntry(
                                name,
                                win = (d.getLong("win") ?: 0L).toInt(),
                                lose = (d.getLong("lose") ?: 0L).toInt(),
                                draw = (d.getLong("draw") ?: 0L).toInt(),
                            )
                        },
                    )
                }
                .addOnFailureListener {
                    Log.e(TAG, "ranking load failed: ${it.message}")
                    onResult(null)
                }
        }.onFailure {
            Log.e(TAG, "ranking load failed: ${it.message}")
            onResult(null)
        }
    }

    /** 문서 이름으로 쓸 수 있게 — '/' 는 쓸 수 없고, 점만으로 된 이름도 안 된다 */
    private fun rankKey(name: String): String = "n_" + name.trim().replace('/', '_')
}
