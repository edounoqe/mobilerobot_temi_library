package kr.kw.libri

import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TEMI 순회(Patrol) 주행 및 화면 상태별 자동 헤드 틸트(Tilt) 제어기.
 * - 일반 스마트폰/에뮬레이터에서도 크래시 없이 안전하게 동작하도록 예외 처리를 포함한다.
 */
class PatrolController(
    private val robot: Robot,
    private val scope: CoroutineScope,
    private val waypoints: List<String> = DEFAULT_WAYPOINTS,
) : OnGoToLocationStatusChangedListener {

    companion object {
        private const val TAG = "PatrolController"

        // 다음 주 실기기 맵핑 시 TEMI에 저장한 위치 이름과 맞추면 된다
        val DEFAULT_WAYPOINTS = listOf("point1", "point2", "point3")

        private const val ARRIVAL_DWELL_MS = 3_000L   // 도착 후 다음 지점 출발 전 대기 시간
        private const val OBSTACLE_RETRY_MS = 3_000L  // 장애물/이탈(ABORT) 발생 시 재시도 대기 시간
        private const val MAX_RETRY_PER_POINT = 2     // 한 지점당 연속 실패 허용 횟수 (초과 시 다음 지점으로 스킵)

        // 화면 상태별 권장 고개 각도 (-25 ~ 55도 범위)
        const val TILT_PATROL = 15   // 순회 주행 및 기본 얼굴 눈맞춤용
        const val TILT_CAMERA = 20   // 네컷 사진 촬영 / 스트레칭 카메라 인식용
        const val TILT_TOUCH = 30    // 홈 메뉴 / 참참참 / 오목 등 화면 터치 조작 편의를 위한 각도
    }

    var isRobotReady: Boolean = false
    private var isPatrolling: Boolean = false
    private var currentIndex: Int = 0
    private var retryCount: Int = 0
    private var nextMoveJob: Job? = null

    /** 로봇 준비 완료 시 리스너 등록 */
    fun attach() {
        try {
            robot.addOnGoToLocationStatusChangedListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "TEMI SDK 리스너 등록 생략 (비-TEMI 환경): ${e.message}")
        }
    }

    /** 화면/앱 종료 시 리스너 해제 및 주행 정지 */
    fun detach() {
        stopPatrol()
        try {
            robot.removeOnGoToLocationStatusChangedListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "TEMI SDK 리스너 해제 생략: ${e.message}")
        }
    }

    /** 순회 모드 시작 */
    fun startPatrol() {
        if (isPatrolling || waypoints.isEmpty()) return
        isPatrolling = true
        retryCount = 0
        Log.d(TAG, "순회 모드 시작 (웨이포인트: $waypoints)")
        moveToCurrentWaypoint()
    }

    /** 순회 모드 즉시 정지 (사람 근접, 화면 터치, 게임 진입 시 호출) */
    fun stopPatrol() {
        if (!isPatrolling && nextMoveJob == null) return
        isPatrolling = false
        nextMoveJob?.cancel()
        nextMoveJob = null
        Log.d(TAG, "순회 모드 정지")

        if (!isRobotReady) return
        try {
            robot.stopMovement()
        } catch (e: Exception) {
            Log.w(TAG, "stopMovement 호출 예외: ${e.message}")
        }
    }

    /** 현재 화면에 맞춰 고개 각도(Tilt) 자동 조절 */
    fun updateTiltForScreen(screen: Screen) {
        val targetAngle = when (screen) {
            Screen.FACE -> TILT_PATROL
            Screen.PHOTO, Screen.STRETCH -> TILT_CAMERA
            Screen.HOME, Screen.CHAM, Screen.OMOK, Screen.FEEDBACK, Screen.ROBOT_SETTINGS -> TILT_TOUCH
        }
        setTiltSafely(targetAngle)
    }

    private fun setTiltSafely(degrees: Int) {
        if (!isRobotReady) {
            Log.d(TAG, "로봇 미연결 상태 - Tilt(${degrees}도) 스킵")
            return
        }
        try {
            val clamped = degrees.coerceIn(-25, 55)
            robot.tiltAngle(clamped)
            Log.d(TAG, "헤드 틸트 조절: ${clamped}도")
        } catch (e: Exception) {
            Log.w(TAG, "tiltAngle 호출 예외: ${e.message}")
        }
    }

    private fun moveToCurrentWaypoint() {
        if (!isPatrolling || waypoints.isEmpty()) return
        if (!isRobotReady) {
            Log.d(TAG, "로봇 미연결 상태 - 주행 시뮬레이션 대기 (${waypoints[currentIndex]})")
            return
        }

        val target = waypoints[currentIndex]
        try {
            // 실기기 맵에 해당 위치가 저장되어 있는지 먼저 확인
            val savedLocations = robot.locations
            if (!savedLocations.contains(target)) {
                Log.w(TAG, "맵에 '$target' 위치가 없습니다. 저장된 위치 목록: $savedLocations")
                isPatrolling = false
                return
            }
            Log.d(TAG, "이동 명령 전송 -> $target")
            robot.goTo(target)
        } catch (e: Exception) {
            Log.w(TAG, "goTo($target) 호출 예외: ${e.message}")
        }
    }

    private fun scheduleNextMove(delayMs: Long, advanceIndex: Boolean) {
        nextMoveJob?.cancel()
        nextMoveJob = scope.launch {
            delay(delayMs)
            if (!isPatrolling) return@launch
            if (advanceIndex) {
                currentIndex = (currentIndex + 1) % waypoints.size
                retryCount = 0
            }
            moveToCurrentWaypoint()
        }
    }

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String,
    ) {
        Log.d(TAG, "이동 상태 변경 [$location]: status=$status ($descriptionId: $description)")
        if (!isPatrolling) return

        when (status) {
            OnGoToLocationStatusChangedListener.COMPLETE -> {
                // 목표 지점 도착 -> 3초 대기 후 다음 지점으로 순환
                scheduleNextMove(ARRIVAL_DWELL_MS, advanceIndex = true)
            }
            OnGoToLocationStatusChangedListener.ABORT -> {
                // 장애물이나 사람으로 경로 차단 시 -> 3초 대기 후 재시도 (2회 실패 시 다음 지점 스킵)
                retryCount++
                if (retryCount <= MAX_RETRY_PER_POINT) {
                    Log.w(TAG, "'$location' 이동 중단 -> ${OBSTACLE_RETRY_MS}ms 후 재시도 ($retryCount/$MAX_RETRY_PER_POINT)")
                    scheduleNextMove(OBSTACLE_RETRY_MS, advanceIndex = false)
                } else {
                    Log.w(TAG, "'$location' 연속 실패 초과 -> 다음 웨이포인트로 건너뜀")
                    scheduleNextMove(OBSTACLE_RETRY_MS, advanceIndex = true)
                }
            }
        }
    }
}