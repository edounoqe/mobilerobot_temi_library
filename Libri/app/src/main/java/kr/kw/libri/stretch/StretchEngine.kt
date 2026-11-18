package kr.kw.libri.stretch

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

enum class Phase { READY, COUNTDOWN, PLAYING, RESULT }

enum class Grade(val label: String, val base: Int) {
    PERFECT("완벽!", 100),
    GOOD("좋아요!", 60),
    MISS("쿵!", 0),
}

sealed interface StretchEvent {
    /** 시작 전 카운트다운 숫자 (3, 2, 1) */
    data class Countdown(val n: Int) : StretchEvent
    data object Go : StretchEvent
    data class Judged(val index: Int, val grade: Grade, val gained: Int, val combo: Int) : StretchEvent
    data object Finished : StretchEvent
}

/** 코스 위에 놓인 벽 하나 */
class Wall(val index: Int, val pose: StretchPose, val at: Float) {
    var grade: Grade? = null
    var judgedAtMs = 0f
    var fit = 0f            // 판정 순간의 일치도
    var activeMs = 0f       // 이 벽이 목표였던 시간
    var holdMs = 0f         // 그중 자세를 맞추고 있던 시간
}

class StretchResult(
    val score: Int,
    val perfect: Int,
    val good: Int,
    val miss: Int,
    val maxCombo: Int,
    val stars: Int,
)

/**
 * 게임 진행 — 화면·카메라와 무관한 순수 로직.
 *
 * 아바타는 도서관 통로를 일정한 속도로 나아가고, 벽은 통로 위 정해진 위치에 서 있다.
 * 벽에 닿는 순간(distance >= wall.at) 직전의 자세로 통과 여부를 판정한다.
 */
class StretchEngine(val course: StretchCourse) {

    var phase = Phase.READY
        private set
    var timeMs = 0f
        private set
    /** 지금까지 나아간 거리 (단위: 몸통 길이) */
    var distance = 0f
        private set
    val walls = ArrayList<Wall>()
    /** 다음에 통과할 벽 (없으면 -1) */
    var targetIndex = -1
        private set
    val target: Wall? get() = walls.getOrNull(targetIndex)

    /** 지금 이 순간의 일치도와 뼈대별 점수 (판정 안 하는 뼈는 -1) */
    var fit = 0f
        private set
    val boneFit = FloatArray(BONE_COUNT) { -1f }
    /** 판정에 쓰는 값 — 최근 일치도의 지수 이동 평균. 닿는 순간에만 반짝 맞추면 통과하지 못한다 */
    var judgeFit = 0f
        private set

    var score = 0
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set

    /** READY: 몸이 잡힌 채로 유지한 시간 */
    var readyMs = 0f
        private set
    var countdownMs = 0f
        private set

    /** 통과 순간 아바타를 구멍 모양으로 끌어당기는 정도(0~1)와 그 대상 자세 */
    var snap = 0f
        private set
    var snapPose: StretchPose? = null
        private set

    var result: StretchResult? = null
        private set

    private var speedFactor = 1f
    private var snapHoldUntilMs = -1f
    private var passedPose: StretchPose? = null
    private var finishAtMs = -1f
    private val events = ArrayList<StretchEvent>()

    /** 지난 update 이후 쌓인 이벤트를 꺼낸다 */
    fun drainEvents(sink: (StretchEvent) -> Unit) {
        if (events.isEmpty()) return
        for (i in events.indices) sink(events[i])
        events.clear()
    }

    /** 목표 벽까지 남은 거리 비율 (1 = 막 목표가 됨, 0 = 닿음) */
    fun approach(): Float {
        val t = target ?: return 0f
        val span = SPEED * course.intervalMs(t.index) / 1000f
        return ((t.at - distance) / span).coerceIn(0f, 1f)
    }

    /**
     * @param dtMs        지난 프레임 이후 흐른 시간
     * @param player      사용자 자세 (스무딩된 값)
     * @param tracked     몸(상체)이 카메라에 잡혀 있는가
     * @param legsTracked 다리까지 잡혀 있는가
     */
    fun update(dtMs: Float, player: BodyPose, tracked: Boolean, legsTracked: Boolean) {
        timeMs += dtMs
        speedFactor += (1f - speedFactor) * ease(dtMs, STUMBLE_RECOVER_MS)
        distance += SPEED * speedFactor * dtMs / 1000f

        when (phase) {
            Phase.READY -> {
                readyMs = if (tracked) readyMs + dtMs else 0f
                if (readyMs >= READY_HOLD_MS) {
                    phase = Phase.COUNTDOWN
                    countdownMs = COUNTDOWN_MS
                    events += StretchEvent.Countdown(ceil(COUNTDOWN_MS / 1000f).toInt())
                }
            }

            Phase.COUNTDOWN -> {
                val before = ceil(countdownMs / 1000f).toInt()
                countdownMs -= dtMs
                val now = ceil(countdownMs / 1000f).toInt()
                if (countdownMs <= 0f) {
                    begin()
                } else if (now != before) {
                    events += StretchEvent.Countdown(now)
                }
            }

            Phase.PLAYING -> play(dtMs, player, tracked, legsTracked)
            Phase.RESULT -> Unit
        }
    }

    private fun begin() {
        phase = Phase.PLAYING
        walls.clear()
        var at = distance
        course.poses.forEachIndexed { i, pose ->
            at += SPEED * course.intervalMs(i) / 1000f
            walls += Wall(i, pose, at)
        }
        targetIndex = 0
        judgeFit = 0f
        events += StretchEvent.Go
    }

    private fun play(dtMs: Float, player: BodyPose, tracked: Boolean, legsTracked: Boolean) {
        val t = target
        if (t == null) {
            fit = 0f
            boneFit.fill(-1f)
            updateSnap(dtMs, null, 0f)
            if (finishAtMs >= 0f && timeMs >= finishAtMs) finish()
            return
        }

        if (tracked) {
            fit = t.pose.fit(player, legsTracked, boneFit)
        } else {
            fit = 0f
            boneFit.fill(-1f)
        }
        judgeFit += (fit - judgeFit) * ease(dtMs, JUDGE_TAU_MS)
        t.activeMs += dtMs
        if (fit >= GOOD_FIT) t.holdMs += dtMs

        val ahead = t.at - distance
        updateSnap(dtMs, t, ahead)
        if (ahead <= 0f) judge(t)
    }

    private fun judge(w: Wall) {
        val grade = when {
            judgeFit >= PERFECT_FIT -> Grade.PERFECT
            judgeFit >= GOOD_FIT -> Grade.GOOD
            else -> Grade.MISS
        }
        w.grade = grade
        w.fit = judgeFit
        w.judgedAtMs = timeMs

        var gained = 0
        if (grade == Grade.MISS) {
            combo = 0
            speedFactor = STUMBLE_SPEED       // 부딪혀 잠깐 주춤
        } else {
            combo++
            maxCombo = maxOf(maxCombo, combo)
            val mult = 1f + COMBO_STEP * min(combo - 1, COMBO_CAP)
            val hold = if (w.activeMs > 0f) (w.holdMs / w.activeMs).coerceIn(0f, 1f) else 0f
            gained = (grade.base * mult + HOLD_BONUS * hold).roundToInt()
            score += gained
            // 통과 직후 잠깐은 구멍 모양을 유지해 깔끔하게 빠져나가게 한다
            passedPose = w.pose
            snapPose = w.pose
            snapHoldUntilMs = timeMs + SNAP_HOLD_MS
        }
        events += StretchEvent.Judged(w.index, grade, gained, combo)

        targetIndex++
        judgeFit = 0f
        if (targetIndex >= walls.size) {
            targetIndex = -1
            finishAtMs = timeMs + RESULT_DELAY_MS
        }
    }

    private fun updateSnap(dtMs: Float, t: Wall?, ahead: Float) {
        val goal: Float
        if (timeMs < snapHoldUntilMs) {
            goal = SNAP_MAX                                   // 방금 통과한 벽의 자세 유지
        } else if (passedPose != null && snapPose === passedPose && snap > 0.02f) {
            goal = 0f                                         // 풀리는 중 — 다 풀릴 때까지 대상은 그대로
        } else {
            passedPose = null
            snapPose = t?.pose
            goal = if (t != null && ahead <= SNAP_DIST && judgeFit >= GOOD_FIT) {
                SNAP_MAX * (1f - (ahead / SNAP_DIST).coerceIn(0f, 1f))
            } else 0f
        }
        snap += (goal - snap) * ease(dtMs, SNAP_TAU_MS)
    }

    private fun finish() {
        phase = Phase.RESULT
        val perfect = walls.count { it.grade == Grade.PERFECT }
        val good = walls.count { it.grade == Grade.GOOD }
        val miss = walls.count { it.grade == Grade.MISS }
        val ratio = if (walls.isEmpty()) 0f else (perfect * 100f + good * 60f) / (walls.size * 100f)
        val stars = when {
            ratio >= 0.85f -> 3
            ratio >= 0.5f -> 2
            else -> 1
        }
        result = StretchResult(score, perfect, good, miss, maxCombo, stars)
        events += StretchEvent.Finished
    }

    companion object {
        /** 통로를 나아가는 속도 (몸통 길이 / 초) */
        const val SPEED = 7f
        const val READY_HOLD_MS = 1200f
        const val COUNTDOWN_MS = 3000f
        const val GOOD_FIT = 0.6f
        const val PERFECT_FIT = 0.85f
        const val JUDGE_TAU_MS = 250f
        const val HOLD_BONUS = 40f
        const val COMBO_STEP = 0.1f
        const val COMBO_CAP = 5
        const val RESULT_DELAY_MS = 1900f
        private const val STUMBLE_SPEED = 0.2f
        private const val STUMBLE_RECOVER_MS = 380f
        private const val SNAP_MAX = 0.85f
        private const val SNAP_DIST = SPEED * 0.28f
        private const val SNAP_HOLD_MS = 240f
        private const val SNAP_TAU_MS = 90f

        /** 시간 상수 tau 인 지수 추종에서 dt 동안 따라잡는 비율 */
        fun ease(dtMs: Float, tauMs: Float): Float = 1f - exp(-dtMs / tauMs)
    }
}

/**
 * 자동 시연 — 사람 대신 자세를 만들어 주는 가상 플레이어.
 * 새 벽을 보고 잠깐 뒤에 움직이기 시작하고, 일부 벽은 일부러 어설프게 하거나 틀려서 '좋아요'·'쿵'도 보여 준다.
 */
class DemoPilot(private val sloppyWall: Int = 2, private val failWall: Int = 4) {
    val pose: BodyPose = BodyPose.rest()

    private val rest = BodyPose.rest()
    private val aim = BodyPose.rest()
    private var seenIndex = -1
    private var reactMs = 0f
    private var clock = 0f

    fun update(dtMs: Float, engine: StretchEngine) {
        clock += dtMs
        val t = engine.target
        if (engine.phase == Phase.PLAYING && t != null) {
            if (t.index != seenIndex) {
                seenIndex = t.index
                reactMs = REACT_MS
            }
            reactMs -= dtMs
            if (reactMs <= 0f) {
                aim.set(if (t.index == failWall) rest else t.pose.pose)
                if (t.index == sloppyWall) {          // 팔을 덜 뻗은 어설픈 자세 → '좋아요'
                    for (b in BONES) {
                        val side = when (b) {
                            Bone.L_UPPER_ARM, Bone.L_FOREARM -> -1f
                            Bone.R_UPPER_ARM, Bone.R_FOREARM -> 1f
                            else -> 0f
                        }
                        aim.a[b.ordinal] = normDeg(aim.a[b.ordinal] + side * SLOPPY_DEG)
                    }
                }
            }
        } else if (engine.phase != Phase.PLAYING) {
            aim.set(rest)
        }

        pose.chase(aim, StretchEngine.ease(dtMs, MOVE_TAU_MS))
        // 사람처럼 미세하게 흔들린다
        val w = kotlin.math.sin(clock / 410f) * 0.3f
        pose.a[Bone.L_FOREARM.ordinal] = normDeg(pose.a[Bone.L_FOREARM.ordinal] + w * dtMs / 16f)
        pose.a[Bone.R_FOREARM.ordinal] = normDeg(pose.a[Bone.R_FOREARM.ordinal] - w * dtMs / 16f)
    }

    private companion object {
        const val REACT_MS = 700f
        const val MOVE_TAU_MS = 170f
        const val SLOPPY_DEG = 24f
    }
}
