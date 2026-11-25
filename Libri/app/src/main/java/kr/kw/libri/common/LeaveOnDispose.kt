package kr.kw.libri.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * 이 화면이 사라질 때 한 번 [onLeave] 를 부른다 — 사람이 떠나 얼굴 화면으로 돌아가는 등
 * '나가기' 버튼을 거치지 않고 화면이 바뀌어도 방(온라인 자리)·판을 정리하기 위해서다.
 * 화면 회전처럼 Activity 가 다시 만들어지는 경우에는 부르지 않는다 (ViewModel 은 그대로 남으므로).
 */
@Composable
fun LeaveOnDispose(onLeave: () -> Unit) {
    val context = LocalContext.current
    val current by rememberUpdatedState(onLeave)
    DisposableEffect(Unit) {
        onDispose {
            if (context.findActivity()?.isChangingConfigurations != true) current()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
