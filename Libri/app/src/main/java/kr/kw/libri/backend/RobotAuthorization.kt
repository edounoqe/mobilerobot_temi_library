package kr.kw.libri.backend

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kr.kw.libri.LibriFirebase
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun firebaseRobotUid(): String {
    val auth = FirebaseAuth.getInstance()
    return auth.currentUser?.uid ?: requireNotNull(auth.signInAnonymously().awaitResult().user).uid
}

internal suspend fun approvedRobotId(context: Context, uid: String): String? =
    LibriFirebase.realtimeDatabase(context).getReference("robotAccess").child(uid).get()
        .awaitResult().getValue(String::class.java)

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase operation cancelled"))
    }
}
