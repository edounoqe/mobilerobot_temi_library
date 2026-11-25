package kr.kw.libri.photobooth

import kr.kw.libri.R
import kr.kw.libri.MainActivity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

class FrameSelectActivity : Activity() {
    private var selectedFrameName = "프레임 1 (frame_1_library)"
    private var selectedFrameResId = R.drawable.frame_1_library

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frame_select)

        val btnFrame1 = findViewById<View>(R.id.btnFrame1)
        val btnFrame2 = findViewById<View>(R.id.btnFrame2)
        val btnFrame3 = findViewById<View>(R.id.btnFrame3)
        val btnFrame4 = findViewById<View>(R.id.btnFrame4)

        val txtCheck1 = findViewById<TextView>(R.id.txtCheck1)
        val txtCheck2 = findViewById<TextView>(R.id.txtCheck2)
        val txtCheck3 = findViewById<TextView>(R.id.txtCheck3)
        val txtCheck4 = findViewById<TextView>(R.id.txtCheck4)

        fun selectFrame(index: Int, name: String, resId: Int) {
            selectedFrameName = name
            selectedFrameResId = resId

            btnFrame1?.setBackgroundResource(if (index == 1) R.drawable.bg_sketch_card_selected else R.drawable.bg_sketch_card)
            btnFrame2?.setBackgroundResource(if (index == 2) R.drawable.bg_sketch_card_selected else R.drawable.bg_sketch_card)
            btnFrame3?.setBackgroundResource(if (index == 3) R.drawable.bg_sketch_card_selected else R.drawable.bg_sketch_card)
            btnFrame4?.setBackgroundResource(if (index == 4) R.drawable.bg_sketch_card_selected else R.drawable.bg_sketch_card)

            txtCheck1?.text = "프레임 1" + (if (index == 1) " ●" else " ○")
            txtCheck1?.setTextColor(if (index == 1) 0xFF24221F.toInt() else 0xFFA29B8F.toInt())

            txtCheck2?.text = "프레임 2" + (if (index == 2) " ●" else " ○")
            txtCheck2?.setTextColor(if (index == 2) 0xFF24221F.toInt() else 0xFFA29B8F.toInt())

            txtCheck3?.text = "프레임 3" + (if (index == 3) " ●" else " ○")
            txtCheck3?.setTextColor(if (index == 3) 0xFF24221F.toInt() else 0xFFA29B8F.toInt())

            txtCheck4?.text = "프레임 4" + (if (index == 4) " ●" else " ○")
            txtCheck4?.setTextColor(if (index == 4) 0xFF24221F.toInt() else 0xFFA29B8F.toInt())
        }

        btnFrame1?.setOnClickListener { selectFrame(1, "프레임 1 (frame_1_library)", R.drawable.frame_1_library) }
        btnFrame2?.setOnClickListener { selectFrame(2, "프레임 2 (frame_2_libri_burgundy)", R.drawable.frame_2_libri_burgundy) }
        btnFrame3?.setOnClickListener { selectFrame(3, "프레임 3 (frame_3_libri_beige)", R.drawable.frame_3_libri_beige) }
        btnFrame4?.setOnClickListener { selectFrame(4, "프레임 4 (frame_4_central_library)", R.drawable.frame_4_central_library) }

        findViewById<Button>(R.id.btnStartShoot).setOnClickListener {
            val intent = Intent(this, PhotoCaptureActivity::class.java)
            intent.putExtra("selected_frame", selectedFrameName)
            intent.putExtra("selected_frame_res", selectedFrameResId)
            startActivity(intent)
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnHome).setOnClickListener { navigateToHome() }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
