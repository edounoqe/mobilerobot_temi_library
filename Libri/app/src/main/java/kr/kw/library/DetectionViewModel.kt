package kr.kw.library

import android.util.Log
import androidx.lifecycle.ViewModel
import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.voice.WakeupRequest
import com.robotemi.sdk.listeners.OnDetectionDataChangedListener
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.model.DetectionData
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 로봇이 '사람을 보고 듣는' 콜백을 받아 화면에 그대로 보여준다.
 *
 * 센서 기반 사람 감지 — 감지 모드는 앱이 켠다 (setDetectionModeOn() 은 temi Settings 권한 필요, 첫 실행 때 한 번 승인)
 *  OnDetectionStateChangedListener  → onDetectionStateChanged(state)   IDLE(0) · LOST(1) · DETECTED(2)
 *  OnDetectionDataChangedListener   → onDetectionDataChanged(detectionData)   distance(m) · angle(정면 0, 왼쪽 +) · isDetected
 *
 * 음성 인식 (STT)
 *  Robot.TtsListener                → onTtsStatusChanged(request)       speak() 로 한 말이 끝났는지
 *  Robot.AsrListener                → onAsrResult(text, lang)          wakeup() 으로 듣기 시작하면 사람 말이 문장으로 온다
 *
 * 회전 · 헤드 틸트 — 콜백 없이 명령만 보낸다
 *  turnBy(degrees, speed)      몸체를 상대 각도로 회전 (왼쪽 +, 오른쪽 −)
 *  tiltAngle(degrees, speed)   머리를 절대 각도로 — 아래 -30 ~ 위 +50, 정면 0
 *
 * (비전 기반 얼굴 검출은 카메라 프레임을 다루므로 FaceDetectionView 에 따로 있다)
 */
class DetectionViewModel : ViewModel(),
    OnDetectionStateChangedListener,
    OnDetectionDataChangedListener,
    Robot.TtsListener,
    Robot.AsrListener,
    OnRequestPermissionResultListener {

    private val robot: Robot = Robot.getInstance()

    // ── 사람 감지 상태 ──────────────────────────────────────────────

    private val _state = MutableStateFlow("IDLE")
    val state: StateFlow<String> = _state.asStateFlow()

    private val _distance = MutableStateFlow(0.0)
    val distance: StateFlow<Double> = _distance.asStateFlow()

    private val _angle = MutableStateFlow(0.0)
    val angle: StateFlow<Double> = _angle.asStateFlow()

    private val _detectionModeOn = MutableStateFlow(false)
    val detectionModeOn: StateFlow<Boolean> = _detectionModeOn.asStateFlow()

    /** 머리 각도 — 앱이 마지막으로 보낸 값 */
    private val _tilt = MutableStateFlow(0)
    val tilt: StateFlow<Int> = _tilt.asStateFlow()

    // ── 음성 인식 상태 ──────────────────────────────────────────────

    /** 마지막으로 사람이 한 말 (STT 결과) */
    private val _heard = MutableStateFlow("")
    val heard: StateFlow<String> = _heard.asStateFlow()

    // ── 센서 기반 사람 감지 ─────────────────────────────

    /** 로봇과 연결된 뒤(onRobotReady) 호출 — 감지 모드가 켜져 있는지 읽는다 */
    fun refreshDetectionMode() {
        _detectionModeOn.value = robot.detectionModeOn
    }

    /**
     * 앱 실행 시 호출 — temi Settings 권한을 먼저 확보하고(없으면 승인 창), 감지 모드가 꺼져 있으면 켠다.
     * 권한은 첫 실행 때 한 번만 승인하면 된다.
     */
    fun ensureDetectionMode() {
        refreshDetectionMode()
        if (robot.checkSelfPermission(Permission.SETTINGS) != Permission.GRANTED) {
            robot.requestPermissions(listOf(Permission.SETTINGS), REQ_SETTINGS)   // 승인되면 onRequestPermissionResult 로 온다
            return
        }
        if (!_detectionModeOn.value) turnDetectionOn()
    }

    /** '감지 모드 켜기' 버튼 — 권한 확인부터 다시 거친다 */
    fun enableDetectionMode() = ensureDetectionMode()

    /** 권한 요청 결과 — 로봇이 호출. 승인되면 감지 모드를 켠다 */
    override fun onRequestPermissionResult(permission: Permission, grantResult: Int, requestCode: Int) {
        if (requestCode == REQ_SETTINGS && grantResult == Permission.GRANTED && !_detectionModeOn.value) turnDetectionOn()
    }

    private fun turnDetectionOn() {
        robot.setDetectionModeOn(true, 1.5f)   // 최대 감지 거리 1.5m (0.5~2.0)
        refreshDetectionMode()
    }

    /** 사람 몸이 감지됐는지 — 상태가 바뀔 때만 온다 */
    override fun onDetectionStateChanged(state: Int) {
        val name = when (state) {
            OnDetectionStateChangedListener.DETECTED -> "DETECTED"
            OnDetectionStateChangedListener.LOST -> "LOST"
            OnDetectionStateChangedListener.IDLE -> "IDLE"
            else -> "UNKNOWN($state)"
        }
        Log.d(TAG_STATE, "state = $name ($state)")
        _state.value = name
    }

    /** 감지된 사람까지의 거리(m)와 각도 — 감지 중에는 계속 들어온다. 각도는 정면 0, 로봇 기준 왼쪽 +, 오른쪽 − */
    override fun onDetectionDataChanged(detectionData: DetectionData) {
        Log.d(TAG_DATA, "distance = ${"%.2f".format(detectionData.distance)} m, angle = ${"%.1f".format(detectionData.angle)}, isDetected = ${detectionData.isDetected}")
        _distance.value = detectionData.distance
        _angle.value = detectionData.angle
    }

    // ── 회전 · 헤드 틸트 ──────────────────────────────────────

    /** 몸체 회전 — 지금 향한 방향에서 degrees 만큼 (왼쪽 +, 오른쪽 −) */
    fun turn(degrees: Int) {
        robot.turnBy(degrees, 1.0f)
    }

    /** 머리 각도 — 지금 값에서 delta 만큼. 범위 -30(아래) ~ +50(위), 0 이 정면 */
    fun tiltBy(delta: Int) {
        val next = (_tilt.value + delta).coerceIn(-30, 50)
        _tilt.value = next
        robot.tiltAngle(next, 1.0f)
    }

    // ── 음성 인식 (STT) ─────────────────────────────────

    /** 말 걸기 — 인사말을 말하고(TTS), 말이 끝나면 듣기 시작한다 */
    private var listenAfterTts = false
    fun greet() {
        _heard.value = "말하는 중…"
        listenAfterTts = true
        robot.speak(TtsRequest.create("안녕하세요. 어디로 안내할까요?", false))   // false = temi 대화창에 글자를 띄우지 않는다
    }

    /** 말하기 상태 — 인사말이 끝났을 때(COMPLETED) 듣기로 넘어간다 */
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        if (ttsRequest.status != TtsRequest.Status.COMPLETED || !listenAfterTts) return
        listenAfterTts = false
        listen()
    }

    /** 듣기 — 마이크를 켠다. wakeupResponse=false 로 temi 기본 응답("무엇을 도와드릴까요")은 끈다 */
    fun listen() {
        _heard.value = "듣는 중…"
        robot.wakeup(listOf(SttLanguage.KO_KR), WakeupRequest(wakeupResponse = false, newSession = false))
    }

    /** 사람이 한 말이 문장으로 바뀌어 온다 — 로봇이 호출 */
    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        Log.d(TAG_ASR, "사람이 한 말 = \"$asrResult\" ($sttLanguage)")
        _heard.value = asrResult
        robot.finishConversation()                 // 한 문장 받았으면 듣기 종료
    }

    companion object {
        // 콜백마다 Logcat 태그를 다르게 — 보고 싶은 것만 필터한다
        //   adb logcat -s DetectionState        감지 상태 변화
        //   adb logcat -s DetectionData         거리·각도 (감지 중 계속 들어온다)
        //   adb logcat -s Asr                   음성 인식 결과
        private const val TAG_STATE = "DetectionState"
        private const val TAG_DATA = "DetectionData"
        private const val TAG_ASR = "Asr"
        private const val REQ_SETTINGS = 1
    }
}
