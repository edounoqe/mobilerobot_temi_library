package kr.kw.libri

import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.flow.StateFlow

/*
 * 참참참 게임 — 공통 모델과 규칙
 *
 *  1) 공격자가 터치로 방향(LEFT/RIGHT)을 고르면 서버에 라운드를 올린다 (startAt = 지금 + LEAD_MS)
 *  2) 두 기기가 같은 startAt 기준으로 "참(0ms) · 참(700ms) · 참!(1400ms)" 을 재생하고, 마지막 참!에서 손목이 돌아간다
 *  3) 수비자 기기가 마지막 참! 전후(JUDGE 구간)의 고개 방향을 모아 판정 → 결과를 서버에 쓴다
 *     - 손가락 방향과 같은 쪽을 보거나 고개를 돌리지 못하면 → 공격 성공(HIT): 공격자 +1점, 공격권 유지
 *     - 반대쪽을 보면 → 회피(MISS): 공격권이 넘어간다
 *  방향은 모두 "수비자 화면 기준"이다. 화면 왼쪽을 가리키는 손 = LEFT, 수비자가 자기 왼쪽을 봄 = LEFT
 */

const val LEAD_MS = 2500L          // 공격 버튼 → 첫 "참" 까지 (네트워크 지연 여유)
const val BEAT_MS = 700L           // 참 사이 간격
const val FINAL_AT = 2 * BEAT_MS   // 마지막 "참!" 시각
const val ROTATE_MS = 160L         // 손목이 돌아가는 시간
const val JUDGE_START = FINAL_AT - 100L
const val JUDGE_END = FINAL_AT + 800L
const val RESULT_SHOW_MS = 2500L   // 결과를 보여 주는 시간
const val HAND_TURN_DEG = 65f      // 손목 회전 각도

enum class Seat {
    A, B;

    val other: Seat get() = if (this == A) B else A
}

enum class Dir { LEFT, RIGHT }

enum class HeadDir { LEFT, RIGHT, CENTER }

data class Round(
    val id: Long,
    val attacker: Seat,
    val dir: Dir,
    val startAt: Long,          // 서버 시각(ms) 기준 첫 "참"
    val result: String? = null, // "HIT" | "MISS" | null(진행 중)
    val look: String? = null,   // 수비자가 본 방향
)

data class RoomState(
    val attacker: Seat = Seat.A,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val round: Round? = null,
    val joinedA: Boolean = false,
    val joinedB: Boolean = false,
)

/** 게임 서버 — Firebase(온라인) 또는 로컬 봇(혼자 테스트) */
interface ChamServer {
    val state: StateFlow<RoomState>
    fun serverNow(): Long
    fun join(seat: Seat)
    fun leave()
    fun startRound(attacker: Seat, dir: Dir)
    fun reportResult(roundId: Long, look: HeadDir, hit: Boolean)
    fun reset()
}

/** 판정 결과를 방 상태에 반영 — 이미 처리된 라운드면 null */
fun applyResult(s: RoomState, roundId: Long, look: HeadDir, hit: Boolean): RoomState? {
    val r = s.round ?: return null
    if (r.id != roundId || r.result != null) return null
    val done = r.copy(result = if (hit) "HIT" else "MISS", look = look.name)
    return if (hit) {
        s.copy(
            round = done,
            scoreA = s.scoreA + if (r.attacker == Seat.A) 1 else 0,
            scoreB = s.scoreB + if (r.attacker == Seat.B) 1 else 0,
        )
    } else {
        s.copy(round = done, attacker = r.attacker.other)
    }
}

/** 얼굴 → 좌우 방향 (FaceDetectionView 의 YAW_SIGN · DIR_THRESHOLD 를 같이 쓴다) */
fun yawDirection(face: Face?): HeadDir {
    if (face == null) return HeadDir.CENTER
    val yaw = face.headEulerAngleY * YAW_SIGN
    return when {
        yaw > DIR_THRESHOLD -> HeadDir.LEFT
        yaw < -DIR_THRESHOLD -> HeadDir.RIGHT
        else -> HeadDir.CENTER
    }
}

/** 손 애니메이션 상태 */
data class HandPose(val rotation: Float, val bounce: Float, val label: String)

/** t = 지금 - startAt. 참·참 에서는 손이 위아래로 까딱, 참! 에서 손목이 dir 쪽으로 회전 */
fun handPose(t: Long?, dir: Dir?): HandPose {
    if (t == null || dir == null) return HandPose(0f, 0f, "")
    if (t < 0) return HandPose(0f, 0f, "준비… ${(-t + 999) / 1000}")
    if (t < FINAL_AT) {
        val u = t % BEAT_MS
        val b = when {
            u < 120 -> u / 120f
            u < 260 -> 1f - (u - 120) / 140f
            else -> 0f
        }
        return HandPose(0f, b, "참")
    }
    val k = ((t - FINAL_AT) / ROTATE_MS.toFloat()).coerceIn(0f, 1f)
    val target = if (dir == Dir.LEFT) -HAND_TURN_DEG else HAND_TURN_DEG
    return HandPose(target * k, 0f, "참!")
}

fun Dir.ko() = if (this == Dir.LEFT) "왼쪽" else "오른쪽"

fun headKo(name: String?) = when (name) {
    "LEFT" -> "왼쪽"
    "RIGHT" -> "오른쪽"
    else -> "정면(안 돌림)"
}
