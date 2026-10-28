package kr.kw.library

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 참참참 진행 담당
 *  - 서버(Firebase/로컬 봇) 상태를 화면에 전달
 *  - 라운드 박자에 맞춰 "참·참·참!" 소리
 *  - 내가 수비자면 카메라 고개 방향을 모아 판정하고 결과를 서버에 쓴다
 */
class ChamViewModel(app: Application) : AndroidViewModel(app) {

    val onlineAvailable: Boolean = firebaseConfigured

    private var server: ChamServer? = null
    private var roomJob: Job? = null
    private var tickJob: Job? = null

    private val _seat = MutableStateFlow<Seat?>(null)
    val seat: StateFlow<Seat?> = _seat.asStateFlow()

    private val _room = MutableStateFlow(RoomState())
    val room: StateFlow<RoomState> = _room.asStateFlow()

    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val sound = ChamSound(app)

    /** 두 기기가 같은 기준으로 쓰는 시각 (Firebase 서버 시각) */
    fun now(): Long = server?.serverNow() ?: System.currentTimeMillis()

    fun join(roomCode: String, seat: Seat, online: Boolean) {
        leave()
        val s: ChamServer = if (online && onlineAvailable) {
            FirebaseChamServer(getApplication(), roomCode)
        } else {
            LocalChamServer(viewModelScope, bot = seat.other)
        }
        server = s
        _online.value = online && onlineAvailable
        s.join(seat)
        _seat.value = seat
        roomJob = viewModelScope.launch { s.state.collect { _room.value = it } }
        tickJob = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(30)
            }
        }
    }

    fun leave() {
        roomJob?.cancel()
        tickJob?.cancel()
        server?.leave()
        server = null
        _seat.value = null
        _room.value = RoomState()
    }

    fun attack(dir: Dir) {
        val me = _seat.value ?: return
        server?.startRound(me, dir)
    }

    fun resetScore() {
        server?.reset()
    }

    // ── 수비 판정: 마지막 "참!" 전후의 고개 방향을 모은다 ──
    private val samples = mutableListOf<HeadDir>()
    private var samplesRound = -1L
    private var reportedRound = -1L
    private var spokenKey = ""

    /** FaceDetectionView 가 프레임마다 호출 (메인 스레드) */
    fun onFaces(faces: List<Face>) {
        val r = _room.value.round ?: return
        val me = _seat.value ?: return
        if (r.attacker == me || r.result != null) return
        val t = now() - r.startAt
        if (t in JUDGE_START..JUDGE_END) {
            if (samplesRound != r.id) {
                samples.clear()
                samplesRound = r.id
            }
            samples += yawDirection(faces.maxByOrNull { it.boundingBox.width() })   // 가장 큰(가까운) 얼굴
        }
    }

    private fun tick() {
        val r = _room.value.round ?: return
        val me = _seat.value ?: return
        val t = now() - r.startAt

        // 소리 — 박자마다 한 번씩
        val beat = when {
            t >= FINAL_AT -> 2
            t >= BEAT_MS -> 1
            t >= 0 -> 0
            else -> -1
        }
        val lateMs = t - beat * BEAT_MS
        if (beat >= 0 && lateMs < 300 && r.result == null) {
            val key = "${r.id}-$beat"
            if (key != spokenKey) {
                spokenKey = key
                sound.cham(final = beat == 2)
            }
        }

        // 판정 — 내가 수비자이고 판정 구간이 끝났으면 한 번만
        if (r.attacker != me && r.result == null && t >= JUDGE_END && reportedRound != r.id) {
            reportedRound = r.id
            val list = if (samplesRound == r.id) samples.toList() else emptyList()
            val look = list.filter { it != HeadDir.CENTER }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key ?: HeadDir.CENTER
            val hit = look == HeadDir.CENTER || look.name == r.dir.name   // 같은 쪽을 봤거나 못 돌렸으면 공격 성공
            server?.reportResult(r.id, look, hit)
        }
    }

    override fun onCleared() {
        leave()
        sound.release()
    }
}

/** "참" 소리 — 안드로이드 TTS(한국어), 안 되면 비프음 */
class ChamSound(context: Context) {
    @Volatile private var ready = false
    private lateinit var tts: TextToSpeech
    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (e: RuntimeException) {
        null
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val r = tts.setLanguage(Locale.KOREAN)
        ready = r >= TextToSpeech.LANG_AVAILABLE
        tts.setSpeechRate(1.6f)
    }

    fun cham(final: Boolean) {
        if (ready) {
            tts.speak(if (final) "참!" else "참", TextToSpeech.QUEUE_FLUSH, null, "cham")
        } else {
            tone?.startTone(if (final) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 150)
        }
    }

    fun release() {
        tts.stop()
        tts.shutdown()
        tone?.release()
    }
}
