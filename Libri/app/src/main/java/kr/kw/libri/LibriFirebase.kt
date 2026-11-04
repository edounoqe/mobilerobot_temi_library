package kr.kw.libri

import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

/** Shared Firebase instances; callers handle authentication, rules and errors. */
object LibriFirebase {
    fun realtimeDatabase(context: Context): FirebaseDatabase =
        FirebaseDatabase.getInstance(context.getString(R.string.firebase_realtime_database_url))

    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
