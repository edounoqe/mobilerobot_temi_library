package kr.kw.libri.stretch

import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors

private const val TAG = "PoseCamera"

/**
 * 전면 카메라일 때 거울처럼(사용자의 왼팔 = 화면 왼쪽 팔) 움직이게 한다.
 * 실제 기기에서 아바타 팔이 좌우 반대로 움직이면 false 로 바꾼다.
 */
internal const val MIRROR_FRONT_CAMERA = true

/**
 * 카메라 미리보기 + ML Kit 포즈 검출. 프레임마다 몸 뼈대를 읽어 [onObservation] 으로 넘긴다 (사람이 없으면 null).
 * 카메라 권한은 이 화면에 들어오기 전에 받아 둔다.
 */
@OptIn(ExperimentalGetImage::class)   // imageProxy.image 는 CameraX 실험 API — 명시적으로 허용
@Composable
fun PoseCameraView(
    modifier: Modifier = Modifier,
    onObservation: (BodyObservation?) -> Unit,
) {
    val currentOnObservation by rememberUpdatedState(onObservation)
    val lifecycleOwner = LocalLifecycleOwner.current

    // 실시간용 검출기 — 이전 프레임 결과를 이어 받아 빠르고 덜 떨린다
    val detector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
        )
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val camera = remember { BoundCamera() }
    DisposableEffect(Unit) {
        onDispose {
            camera.release()         // 화면을 떠나면 카메라를 바로 놓는다
            detector.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // TextureView 방식이어야 둥근 모서리로 잘라 작은 창으로 띄울 수 있다
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                if (camera.released) return@addListener
                val provider = providerFuture.get()
                // temi·폰은 전면 카메라(거울처럼 좌우 반전), 전면이 없으면 후면
                val front = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(true)
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val x = FloatArray(Lm.COUNT)
                val y = FloatArray(Lm.COUNT)
                val c = FloatArray(Lm.COUNT)
                val observation = BodyObservation()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val media = imageProxy.image
                    if (media == null || camera.released) { imageProxy.close(); return@setAnalyzer }
                    val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { pose ->          // 메인 스레드에서 불린다
                            if (camera.released) return@addOnSuccessListener
                            val landmarks = pose.allPoseLandmarks
                            if (landmarks.isEmpty()) {
                                currentOnObservation(null)
                            } else {
                                for (lm in landmarks) {
                                    val i = lm.landmarkType
                                    if (i in 0 until Lm.COUNT) {
                                        x[i] = lm.position.x; y[i] = lm.position.y; c[i] = lm.inFrameLikelihood
                                    }
                                }
                                val seen = PoseTracker.observe(x, y, c, mirrored = front && MIRROR_FRONT_CAMERA, out = observation)
                                currentOnObservation(if (seen) observation else null)
                            }
                        }
                        .addOnFailureListener { e -> if (!camera.released) Log.e(TAG, "pose detect failed", e) }
                        .addOnCompleteListener { imageProxy.close() }   // 닫아야 다음 프레임이 온다
                }

                try {
                    // 다른 화면(얼굴 인식)이 카메라를 붙잡은 채 남아 있을 수 있다 — 겹치면 카메라가 열리지 않는다
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    camera.bound(provider, preview, analysis)
                } catch (e: Exception) {
                    Log.e(TAG, "camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/** 이 화면이 붙잡은 카메라 — 화면을 떠날 때 내가 붙인 것만 떼어 낸다 */
private class BoundCamera {
    @Volatile
    var released = false
        private set
    private var provider: ProcessCameraProvider? = null
    private var useCases: Array<UseCase> = emptyArray()

    fun bound(provider: ProcessCameraProvider, vararg useCases: UseCase) {
        this.provider = provider
        this.useCases = arrayOf(*useCases)
    }

    fun release() {
        released = true
        runCatching { provider?.unbind(*useCases) }
        provider = null
    }
}
