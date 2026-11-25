package kr.kw.libri.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/*
 * 대기모드(얼굴 화면) 타이머 멈춤.
 *
 * MainActivity 는 한동안 화면을 만지지 않으면 얼굴 화면으로 돌아간다. 그런데 실제 게임 중에는
 * 화면을 안 만지는 시간(참참참 수비, 스트레칭 자세 잡기, 오목 수 읽기)이 길어서, 그동안은 세지 않는다.
 * 대기실 · 상대 기다리기 · 게임 끝 화면은 게임 중이 아니므로 그대로 센다.
 */
object IdleGuard {
    /** 지금 타이머를 멈춰 달라는 화면 수 — 0 보다 크면 세지 않는다 */
    var holds by mutableIntStateOf(0)
        private set

    val held: Boolean get() = holds > 0

    internal fun acquire() { holds++ }
    internal fun release() { holds = (holds - 1).coerceAtLeast(0) }
}

/** [playing] 이 true 인 동안 대기모드 타이머를 멈춘다. 끝나면(또는 화면이 사라지면) 그때부터 다시 센다 */
@Composable
fun HoldIdleTimer(playing: Boolean) {
    DisposableEffect(playing) {
        if (playing) IdleGuard.acquire()
        onDispose { if (playing) IdleGuard.release() }
    }
}
