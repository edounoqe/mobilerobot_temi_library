package kr.kw.libri.stretch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.math.ceil

/** 판정 직후 화면 가운데에 띄우는 글자 */
class GradePopup(val grade: Grade, val gained: Int, val seq: Int)

/**
 * 스트레칭 게임 화면 상태.
 * 게임 진행은 [StretchSession] 이 하고, 여기서는 프레임을 돌리며 화면에 보일 값만 Compose 상태로 내보낸다.
 */
class StretchViewModel : ViewModel() {

    /** 대기실에서 고른 코스 */
    var course by mutableStateOf(StretchCourses.LIGHT)
    var soundOn by mutableStateOf(true)
        private set

    /** 진행 중인 판 — null 이면 대기실 */
    var session by mutableStateOf<StretchSession?>(null)
        private set

    /** 프레임 번호 — 무대를 프레임마다 다시 그리게 하는 신호 */
    var frame by mutableLongStateOf(0L)
        private set

    // 글자로 보여 주는 값들 — 바뀔 때만 다시 그려진다
    var phase by mutableStateOf(Phase.READY)
        private set
    var score by mutableIntStateOf(0)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var countdown by mutableIntStateOf(0)
        private set
    var targetIndex by mutableIntStateOf(-1)
        private set
    var judgedCount by mutableIntStateOf(0)
        private set
    var tracked by mutableStateOf(false)
        private set
    var popup by mutableStateOf<GradePopup?>(null)
        private set
    /** 남은 하트 (하트가 있는 코스만) */
    var lives by mutableIntStateOf(0)
        private set

    private val sfx = StretchSfx()
    private var lastNanos = 0L
    private var popupSeq = 0
    private val onEvent: (StretchEvent) -> Unit = ::handle

    fun start(demo: Boolean) {
        session = StretchSession(course, demo)
        lastNanos = 0L
        phase = Phase.READY
        score = 0
        combo = 0
        countdown = 0
        targetIndex = -1
        judgedCount = 0
        tracked = false
        popup = null
        lives = course.lives
    }

    fun quit() {
        session = null
    }

    fun toggleSound() {
        soundOn = !soundOn
        sfx.enabled = soundOn
    }

    /** 카메라 프레임 결과 */
    fun onObservation(o: BodyObservation?) {
        session?.onObservation(o)
    }

    /** 화면 프레임마다 호출 */
    fun onFrame(nanos: Long) {
        val s = session ?: return
        // 앱이 잠깐 멈췄다 돌아와도 게임이 건너뛰지 않게 한 프레임의 시간을 제한한다
        val dtMs = if (lastNanos == 0L) 16f else ((nanos - lastNanos) / 1_000_000f).coerceIn(0f, 50f)
        lastNanos = nanos
        s.frame(dtMs, onEvent)

        val e = s.engine
        phase = e.phase
        score = e.score
        combo = e.combo
        countdown = ceil(e.countdownMs / 1000f).toInt()
        targetIndex = e.targetIndex
        tracked = s.tracked
        lives = e.lives
        frame++
    }

    private fun handle(e: StretchEvent) {
        when (e) {
            is StretchEvent.Countdown -> sfx.tick()
            StretchEvent.Go -> sfx.go()
            is StretchEvent.Judged -> {
                judgedCount = e.index + 1
                popup = GradePopup(e.grade, e.gained, ++popupSeq)
                when (e.grade) {
                    Grade.PERFECT -> sfx.perfect()
                    Grade.GOOD -> sfx.good()
                    Grade.MISS -> sfx.miss()
                }
            }
            StretchEvent.Finished -> sfx.finish()
        }
    }

    override fun onCleared() {
        sfx.release()
    }
}
