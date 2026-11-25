package kr.kw.libri.photobooth

import kr.kw.libri.R
import kr.kw.libri.MainActivity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 4컷 촬영 화면. CameraX 로 미리보기를 보여주고, 3초 카운트다운 뒤 실제로 촬영해서
 * 캐시 폴더(cache/shots)에 JPEG 로 저장한다. 4장이 모이면 경로 목록(photo_paths)을
 * 결과 화면으로 넘겨 프레임과 합성한다.
 *
 * CameraX 는 LifecycleOwner 가 필요해서 android.app.Activity 대신 androidx ComponentActivity 를 쓴다.
 * (AppCompat 테마가 필요 없어서 기존 테마를 그대로 쓸 수 있다.)
 */
class PhotoCaptureActivity : ComponentActivity() {
    private var selectedFrameName = "프레임 1 (frame_1_library)"
    private var selectedFrameResId = R.drawable.frame_1_library
    private var currentShot = 1
    private var isCounting = false
    private var cameraReady = false
    private var usingFrontCamera = false
    private val handler = Handler(Looper.getMainLooper())
    private val photoPaths = ArrayList<String>()
    private var imageCapture: ImageCapture? = null

    private lateinit var previewView: PreviewView
    private lateinit var txtCameraStatus: TextView
    private lateinit var txtFrameTitle: TextView
    private lateinit var txtShotProgress: TextView
    private lateinit var txtCountdown: TextView
    private lateinit var btnTakeNextPhoto: Button

    private lateinit var slot1: TextView
    private lateinit var slot2: TextView
    private lateinit var slot3: TextView
    private lateinit var slot4: TextView

    // 권한 요청 결과는 onCreate 이전(STARTED 전)에 등록해야 하므로 프로퍼티 초기화에서 등록한다.
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showCameraProblem("카메라 권한이 필요해요.\n아래 버튼을 눌러 권한을 허용해주세요.", "카메라 권한 허용하기")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_capture)

        selectedFrameName = intent.getStringExtra("selected_frame") ?: "프레임 1 (frame_1_library)"
        selectedFrameResId = intent.getIntExtra("selected_frame_res", R.drawable.frame_1_library)

        previewView = findViewById(R.id.previewView)
        txtCameraStatus = findViewById(R.id.cameraPreviewText)
        txtFrameTitle = findViewById(R.id.txtFrameTitle)
        txtShotProgress = findViewById(R.id.txtShotProgress)
        txtCountdown = findViewById(R.id.txtCountdown)
        btnTakeNextPhoto = findViewById(R.id.btnTakeNextPhoto)

        slot1 = findViewById(R.id.slot1)
        slot2 = findViewById(R.id.slot2)
        slot3 = findViewById(R.id.slot3)
        slot4 = findViewById(R.id.slot4)

        txtFrameTitle.text = selectedFrameName
        txtShotProgress.text = "1 / 4"

        // 이전 촬영 세션의 사진 파일 정리
        shotsDir().listFiles()?.forEach { it.delete() }

        btnTakeNextPhoto.isEnabled = false
        btnTakeNextPhoto.text = "카메라 준비 중..."
        btnTakeNextPhoto.setOnClickListener {
            when {
                isCounting -> Unit
                !cameraReady -> ensureCameraPermissionAndStart()
                currentShot <= 4 -> startCountdown()
                else -> Unit
            }
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnHome).setOnClickListener { navigateToHome() }

        ensureCameraPermissionAndStart()
    }

    // ---------------------------------------------------------------- 카메라

    private fun ensureCameraPermissionAndStart() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        txtCameraStatus.visibility = View.VISIBLE
        txtCameraStatus.text = "카메라 준비 중..."
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (isFinishing || isDestroyed) return@addListener
            try {
                val provider = providerFuture.get()
                if (provider.availableCameraInfos.isEmpty()) {
                    showCameraProblem("사용할 수 있는 카메라를 찾지 못했어요.", "다시 시도")
                    return@addListener
                }

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(this, pickCameraSelector(provider), preview, capture)

                imageCapture = capture
                cameraReady = true
                txtCameraStatus.visibility = View.GONE
                btnTakeNextPhoto.isEnabled = true
                updateShotProgressUI()
            } catch (e: Exception) {
                Log.e(TAG, "카메라 시작 실패", e)
                showCameraProblem("카메라를 시작하지 못했어요.", "다시 시도")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** 앞면 카메라 우선 -> 뒷면 -> 그 외(외장 카메라 등) 순서로 고른다. */
    private fun pickCameraSelector(provider: ProcessCameraProvider): CameraSelector {
        usingFrontCamera = false
        return when {
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> {
                usingFrontCamera = true
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.Builder().build()
        }
    }

    private fun showCameraProblem(message: String, buttonLabel: String) {
        cameraReady = false
        txtCameraStatus.visibility = View.VISIBLE
        txtCameraStatus.text = message
        btnTakeNextPhoto.isEnabled = true
        btnTakeNextPhoto.text = buttonLabel
    }

    // ---------------------------------------------------------------- 촬영

    private fun startCountdown() {
        isCounting = true
        btnTakeNextPhoto.isEnabled = false
        txtCountdown.visibility = View.VISIBLE

        var count = 3
        txtCountdown.text = count.toString()

        val runnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                count--
                if (count > 0) {
                    txtCountdown.text = count.toString()
                    handler.postDelayed(this, 1000)
                } else {
                    txtCountdown.text = "찰칵!"
                    takePhoto()
                }
            }
        }
        handler.postDelayed(runnable, 1000)
    }

    private fun takePhoto() {
        val capture = imageCapture
        if (capture == null) {
            onCaptureFailed()
            return
        }

        val dir = shotsDir()
        dir.mkdirs()
        val file = File(dir, "shot_${currentShot}_${System.currentTimeMillis()}.jpg")

        // 앞면 카메라는 거울처럼 좌우 반전해서 저장한다. 반전이 싫으면 MIRROR_FRONT_CAMERA 를 false 로.
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = usingFrontCamera && MIRROR_FRONT_CAMERA }
        val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onPhotoSaved(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "촬영 실패", exception)
                    onCaptureFailed()
                }
            }
        )
    }

    private fun onPhotoSaved(file: File) {
        if (isFinishing || isDestroyed) return
        photoPaths.add(file.absolutePath)
        markCurrentSlotCompleted()

        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            txtCountdown.visibility = View.GONE
            isCounting = false
            btnTakeNextPhoto.isEnabled = true

            currentShot++
            if (currentShot > 4) {
                finishAndGoToResult()
            } else {
                updateShotProgressUI()
            }
        }, 800)
    }

    private fun onCaptureFailed() {
        if (isFinishing || isDestroyed) return
        txtCountdown.visibility = View.GONE
        isCounting = false
        btnTakeNextPhoto.isEnabled = true
        Toast.makeText(this, "촬영에 실패했어요. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- UI

    private fun markCurrentSlotCompleted() {
        val slot = when (currentShot) {
            1 -> slot1
            2 -> slot2
            3 -> slot3
            else -> slot4
        }
        slot.setBackgroundResource(R.drawable.bg_photo_slot_filled)
        slot.text = "${currentShot}컷 완료"
        slot.setTextColor(0xFF780804.toInt())
    }

    private fun updateShotProgressUI() {
        txtShotProgress.text = "$currentShot / 4"
        btnTakeNextPhoto.text = "${currentShot}번째 컷 촬영하기 (3초 카운트)"
    }

    private fun shotsDir(): File = File(cacheDir, "shots")

    private fun finishAndGoToResult() {
        val intent = Intent(this, PhotoResultActivity::class.java)
        intent.putExtra("selected_frame", selectedFrameName)
        intent.putExtra("selected_frame_res", selectedFrameResId)
        intent.putStringArrayListExtra("photo_paths", photoPaths)
        startActivity(intent)
        finish()
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "PhotoCapture"
        private const val MIRROR_FRONT_CAMERA = true
    }
}
