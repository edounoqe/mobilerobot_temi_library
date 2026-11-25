package kr.kw.libri.photobooth

import kr.kw.libri.R
import kr.kw.libri.MainActivity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

class PhotoEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_entry)

        findViewById<Button>(R.id.btnOpenFrameSelect).setOnClickListener {
            startActivity(Intent(this, FrameSelectActivity::class.java))
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnHome).setOnClickListener { navigateToHome() }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
