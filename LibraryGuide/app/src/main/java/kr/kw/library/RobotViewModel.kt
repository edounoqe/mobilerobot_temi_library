package kr.kw.library

import android.util.Log
import androidx.lifecycle.ViewModel
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱이 가질 수 있는 상태의 목록. 로봇은 이 중 하나에만 있을 수 있다.
 *  IDLE              대기 — 버튼 입력을 기다린다
 *  NAVIGATING        이동 중 — 목적지로 가는 중
 *  ARRIVED_SPEAKING  도착 안내 — 도착해서 말하는 중
 *  ERROR_HANDLING    예외 처리 — 이동이 중단된 뒤 수습하는 중
 */
enum class RobotState { IDLE, NAVIGATING, ARRIVED_SPEAKING, ERROR_HANDLING }

/**
 * 로봇의 '지금 상태'를 들고 있고, 버튼과 로봇 콜백을 받아 처리하는 곳.
 * 두 리스너 인터페이스를 구현해서, 로봇이 이 안의 on... 함수를 직접 호출해 준다.
 *
 * 지금은 _state 가 한 번도 바뀌지 않는다 — 어디서 바꾸고, 어떤 상태에서만 동작을 허용할지 직접 정해 보자.
 */
class RobotViewModel : ViewModel(),
    OnGoToLocationStatusChangedListener,
    Robot.TtsListener {

    /** TEMI SDK 진입점 */
    private val robot: Robot = Robot.getInstance()

    /** 지금 상태 — 밑줄(_) 쪽은 이 안에서만 바꾼다 */
    private val _state = MutableStateFlow(RobotState.IDLE)
    /** 화면이 읽는 상태 — 읽기 전용 */
    val state: StateFlow<RobotState> = _state.asStateFlow()

    /** 화면 상단 메시지 */
    private val _message = MutableStateFlow("대기 중")
    val message: StateFlow<String> = _message.asStateFlow()

    /** 로봇에 저장된 위치 이름 목록 — 이 이름으로 버튼이 만들어진다 */
    private val _locations = MutableStateFlow(emptyList<String>())
    val locations: StateFlow<List<String>> = _locations.asStateFlow()

    /** 위치 목록을 읽어온다. 로봇과 연결된 뒤(onRobotReady)에 불러야 한다 — 그 전에는 빈 목록이 온다 */
    fun loadLocations() {
        _locations.value = robot.locations
        Log.d(TAG, "locations = ${robot.locations}")
    }

    // ── 화면이 부르는 명령 ───────────────────────────────────────

    /** 서가 버튼을 눌렀을 때 */
    fun goTo(location: String) {
        _message.value = "$location (으)로 이동 중"
        robot.goTo(location)                          // 즉시 반환 — 결과는 아래 콜백으로 온다
    }

    /** 비상 정지 버튼을 눌렀을 때 */
    fun stop() {
        robot.stopMovement()                          // 멈추면 ABORT 콜백이 돌아온다
    }

    // ── 로봇이 부르는 콜백 ───────────────────────────────────────

    /**
     * 이동 상태가 바뀔 때마다 로봇이 호출한다 (앱에는 이 함수를 부르는 코드가 없다).
     * status 는 start → going → ... 으로 가다가 COMPLETE 아니면 ABORT 로 끝난다.
     * descriptionId 는 이유 — 1005 면 사용자가 stopMovement() 로 멈춘 것.
     */
    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String,
    ) {
        Log.d(TAG, "$location → $status (descriptionId=$descriptionId)")

        when (status) {
            OnGoToLocationStatusChangedListener.COMPLETE -> {
                _message.value = "$location 도착"
                speak("$location 에 도착했습니다.")
            }

            OnGoToLocationStatusChangedListener.ABORT -> {
                _message.value = "이동 중단 (코드 $descriptionId)"
                speak("이동을 멈췄습니다.")
            }
        }
    }

    /** 말하기 상태가 바뀔 때마다 로봇이 호출한다. 여기서는 말이 끝난 경우(COMPLETED)만 본다 */
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        if (ttsRequest.status != TtsRequest.Status.COMPLETED) return
        _message.value = "대기 중"
    }

    /** 문장을 말하게 한다 */
    private fun speak(text: String) {
        robot.speak(TtsRequest.create(text, false))
    }

    companion object {
        /** Logcat 필터 태그 — adb logcat -s LibraryDemo */
        private const val TAG = "LibraryDemo"
    }
}
