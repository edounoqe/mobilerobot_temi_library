package kr.kw.libri.backend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun FeedbackScreen(service: String, identity: RobotIdentity, authorized: Boolean, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val backend = remember(context) { runCatching { LibriBackend(context) }.getOrNull() }
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var submission by remember { mutableStateOf<Task<DocumentReference>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(submission) {
        val task = submission ?: return@LaunchedEffect
        val timer = launch {
            delay(10_000)
            message = "저장 확인이 늦어지고 있습니다. 홈으로 돌아가도 연결이 복구되면 제출될 수 있습니다."
        }
        val success = suspendCancellableCoroutine<Boolean> { continuation ->
            task.addOnCompleteListener { continuation.resume(it.isSuccessful) }
        }
        timer.cancel()
        if (success) onExit()
        else {
            failed = true
            message = "저장하지 못했습니다. 네트워크와 접근 권한을 확인한 뒤 다시 시도해주세요."
            submission = null
        }
    }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("서비스가 어떠셨나요?", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text("만족도를 선택해주세요. 설문은 건너뛸 수 있습니다.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { score ->
                if (rating == score) Button(onClick = { rating = score }, enabled = submission == null) { Text("$score") }
                else OutlinedButton(onClick = { rating = score }, enabled = submission == null) { Text("$score") }
            }
        }
        if (!authorized) Text("설문을 저장하려면 로봇의 Firebase 승인이 필요합니다.")
        else if (backend == null) Text("Firebase를 초기화하지 못했습니다.")
        else if (message.isNotEmpty()) Text(message)
        Button(onClick = {
            message = "저장 중…"
            submission = backend?.saveFeedback(rating, service, identity.id, identity.floor)
        }, enabled = authorized && backend != null && submission == null && rating in 1..5, modifier = Modifier.fillMaxWidth()) {
            Text(if (failed) "다시 시도" else "평가 제출")
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(if (submission == null) "건너뛰기" else "홈으로 돌아가기")
        }
    }
}
