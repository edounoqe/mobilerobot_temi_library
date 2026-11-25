package kr.kw.libri.photobooth

import kr.kw.libri.R
import kr.kw.libri.MainActivity

import androidx.activity.ComponentActivity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// lifecycleScope(업로드)를 쓰려면 LifecycleOwner 여야 해서 ComponentActivity 를 쓴다
class PhotoResultActivity : ComponentActivity()
{
    private var compositeBitmap: Bitmap? = null
    private var isUploading: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_result)

        val selectedFrameRes = intent.getIntExtra("selected_frame_res", R.drawable.frame_1_library)
        // PhotoCaptureActivity 가 촬영해서 저장한 사진 4장의 경로. 없으면 회색 자리표시로 합성된다.
        val photoPaths = intent.getStringArrayListExtra("photo_paths")

        val imgComposite = findViewById<ImageView>(R.id.imgResultComposite)
        Thread {
            try
            {
                val photos = FrameComposer.loadPhotos(photoPaths)
                val result = FrameComposer.compose(this, selectedFrameRes, photos)
                // 사진마다 다른 이름 — 같은 이름이면 Supabase 에 덮어써져 앞사람 QR 이 다음 사람 사진을 가리킨다
                val resultFile = FrameComposer.saveToCache(this, result, "libri4cut_${System.currentTimeMillis()}.png")
                runOnUiThread {
                    if (isFinishing || isDestroyed)
                    {
                        result.recycle()
                    }
                    else
                    {
                        compositeBitmap = result
                        imgComposite.setImageBitmap(result)
                        uploadAndGenerateQr(resultFile)
                    }
                }
            }
            catch (e: Throwable)
            {
                Log.e("PhotoResult", "사진 합성 실패: ${e.message}", e)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed)
                    {
                        val qrContainer = findViewById<LinearLayout>(R.id.qrCodeContainer)
                        qrContainer.removeAllViews()
                        qrContainer.orientation = LinearLayout.VERTICAL
                        qrContainer.gravity = Gravity.CENTER
                        val errorText = TextView(this).apply {
                            text = "사진 합성에 실패했습니다. 다시 촬영해주세요."
                            gravity = Gravity.CENTER
                        }
                        qrContainer.addView(errorText)
                    }
                }
            }
        }.start()

        findViewById<Button>(R.id.btnReShoot).setOnClickListener {
            val intent = Intent(this, FrameSelectActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        findViewById<View>(R.id.btnHome).setOnClickListener { navigateToHome() }
    }

    private fun uploadAndGenerateQr(resultFile: File)
    {
        if (isUploading) return
        isUploading = true

        val qrContainer = findViewById<LinearLayout>(R.id.qrCodeContainer)
        qrContainer.removeAllViews()
        qrContainer.orientation = LinearLayout.VERTICAL
        qrContainer.gravity = Gravity.CENTER

        val statusText = TextView(this).apply {
            text = "QR 코드를 준비하고 있습니다..."
            gravity = Gravity.CENTER
        }
        qrContainer.addView(statusText)

        lifecycleScope.launch {
            try
            {
                val uploadResult = withContext(Dispatchers.IO) {
                    SupabaseManager.uploadAndRecordPhoto(this@PhotoResultActivity, resultFile)
                }
                uploadResult.onSuccess { url ->
                    // 올리는 김에 1시간 지난 다른 사진도 정리한다
                    lifecycleScope.launch { SupabaseManager.cleanupExpiredPhotos() }
                    val qr = QRCodeGenerator.generate(url)
                    qrContainer.removeAllViews()
                    if (qr != null)
                    {
                        val qrView = ImageView(this@PhotoResultActivity).apply {
                            setImageBitmap(qr)
                            contentDescription = "사진 다운로드 QR 코드"
                            layoutParams = LinearLayout.LayoutParams(
                                (140 * resources.displayMetrics.density).toInt(),
                                (140 * resources.displayMetrics.density).toInt()
                            )
                        }
                        qrContainer.addView(qrView)
                    }
                    else
                    {
                        showRetryUi(qrContainer, "QR 생성에 실패했습니다.", resultFile)
                    }
                }.onFailure { error ->
                    val reason = error.message ?: "네트워크를 확인해주세요"
                    showRetryUi(qrContainer, "업로드 실패: $reason", resultFile)
                }
            }
            finally
            {
                isUploading = false
            }
        }
    }

    private fun showRetryUi(qrContainer: LinearLayout, message: String, resultFile: File)
    {
        qrContainer.removeAllViews()
        qrContainer.orientation = LinearLayout.VERTICAL
        qrContainer.gravity = Gravity.CENTER

        val errorText = TextView(this).apply {
            text = message
            gravity = Gravity.CENTER
        }
        val retryButton = Button(this).apply {
            text = "QR 다시 시도"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener {
                uploadAndGenerateQr(resultFile)
            }
        }
        qrContainer.addView(errorText)
        qrContainer.addView(retryButton)
    }

    override fun onDestroy()
    {
        findViewById<ImageView>(R.id.imgResultComposite)?.setImageDrawable(null)
        compositeBitmap?.recycle()
        compositeBitmap = null
        super.onDestroy()
    }

    private fun navigateToHome()
    {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}