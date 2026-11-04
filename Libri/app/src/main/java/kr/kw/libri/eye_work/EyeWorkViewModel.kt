package kr.kw.libri.eye_work

import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.kw.libri.common.PITCH_SIGN
import kr.kw.libri.common.YAW_SIGN
import kotlin.math.abs

/**
 * 눈 운동 — temi 는 시선 추적을 못 하므로, 화면 가운데 점을 '보게' 한 뒤 고개를 돌리게 한다.
 *  각 단계: "가운데 점을 보며 고개를 ○○로 돌리세요" → ML Kit 고개 각도가 목표(TARGET_DEG)를
 *  HOLD_MS 동안 넘으면 성공 → 정면(CENTER_DEG)으로 돌아오면 다음 방향 제시.
 *  방향은 모두 '사용자(수비자) 화면 기준' — FaceDetectionView 의 YAW_SIGN·PITCH_SIGN 을 같이 쓴다.
 */
enum class EyeDir(val label: String, val arrow: String) {
    LEFT("왼쪽", "←"),
    RIGHT("오른쪽", "→"),
    UP("위", "↑"),
    DOWN("아래", "↓"),
}

data class EyeState(
    val step: Int = 1,          // 현재 순서 (1-based, 표시용)
    val total: Int = 0,
    val target: EyeDir? = null, // 돌려야 할 방향 (정면 복귀/완료 중엔 null)
    val recenter: Boolean = false,
    val progress: Float = 0f,   // 목표 자세 유지 진행 0..1
    val done: Boolean = false,
)

private const val TARGET_DEG = 22f   // 이 각도(°) 이상 돌리면 인정
private const val CENTER_DEG = 10f   // 다음 동작 전, 정면으로 간주하는 범위
private const val HOLD_MS = 500L     // 목표 방향을 이만큼 유지하면 성공

class EyeWorkViewModel : ViewModel() {

    // 한 세트: 좌·우·상·하 × 2회
    private val routine = listOf(
        EyeDir.LEFT, EyeDir.RIGHT, EyeDir.UP, EyeDir.DOWN,
        EyeDir.LEFT, EyeDir.RIGHT, EyeDir.UP, EyeDir.DOWN,
    )

    private val _state = MutableStateFlow(
        EyeState(step = 1, total = routine.size, target = routine.first())
    )
    val state: StateFlow<EyeState> = _state.asStateFlow()

    private var idx = 0
    private var holdStart = 0L
    private var awaitingCenter = false

    /** FaceDetectionView 가 프레임마다 호출 (메인 스레드) */
    fun onFaces(faces: List<Face>) {
        if (_state.value.done) return
        val face = faces.maxByOrNull { it.boundingBox.width() }   // 가장 큰(가까운) 얼굴
        if (face == null) {                                       // 얼굴이 안 보이면 유지 리셋
            holdStart = 0L
            if (_state.value.progress != 0f) _state.value = _state.value.copy(progress = 0f)
            return
        }
        val yaw = face.headEulerAngleY * YAW_SIGN
        val pitch = face.headEulerAngleX * PITCH_SIGN
        val now = System.currentTimeMillis()

        // 성공 직후: 정면으로 돌아와야 다음 방향을 제시한다
        if (awaitingCenter) {
            if (abs(yaw) < CENTER_DEG && abs(pitch) < CENTER_DEG) {
                awaitingCenter = false
                _state.value = _state.value.copy(
                    step = idx + 1, target = routine[idx], recenter = false, progress = 0f,
                )
            }
            return
        }

        val target = routine[idx]
        val reached = when (target) {
            EyeDir.LEFT -> yaw > TARGET_DEG
            EyeDir.RIGHT -> yaw < -TARGET_DEG
            EyeDir.UP -> pitch > TARGET_DEG
            EyeDir.DOWN -> pitch < -TARGET_DEG
        }

        if (reached) {
            if (holdStart == 0L) holdStart = now
            val p = ((now - holdStart).toFloat() / HOLD_MS).coerceIn(0f, 1f)
            if (p >= 1f) {                        // 한 동작 성공
                holdStart = 0L
                idx++
                _state.value = if (idx >= routine.size) {
                    _state.value.copy(target = null, recenter = false, progress = 1f, done = true)
                } else {
                    awaitingCenter = true
                    _state.value.copy(target = null, recenter = true, progress = 0f)
                }
            } else {
                _state.value = _state.value.copy(progress = p)
            }
        } else {
            holdStart = 0L
            if (_state.value.progress != 0f) _state.value = _state.value.copy(progress = 0f)
        }
    }

    fun restart() {
        idx = 0
        holdStart = 0L
        awaitingCenter = false
        _state.value = EyeState(step = 1, total = routine.size, target = routine.first())
    }
}
