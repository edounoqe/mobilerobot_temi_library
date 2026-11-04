package kr.kw.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

private const val TAG = "FaceDetect"

// 고개 방향 판정 — temi에서 실제로 돌려보고 반대로 나오면 부호를 -1f 로 바꾼다
private const val YAW_SIGN = 1f      // headEulerAngleY : 좌우
private const val PITCH_SIGN = 1f    // headEulerAngleX : 위아래
private const val DIR_THRESHOLD = 20f   // 이 각도(°)를 넘으면 해당 방향으로 판정

/** Yaw/Pitch → "왼쪽·위" 같은 방향 문자열 */
private fun headDirection(face: Face): String {
    val yaw = face.headEulerAngleY * YAW_SIGN
    val pitch = face.headEulerAngleX * PITCH_SIGN
    val lr = when { yaw > DIR_THRESHOLD -> "왼쪽"; yaw < -DIR_THRESHOLD -> "오른쪽"; else -> "" }
    val ud = when { pitch > DIR_THRESHOLD -> "위"; pitch < -DIR_THRESHOLD -> "아래"; else -> "" }
    return listOf(lr, ud).filter { it.isNotEmpty() }.joinToString("·").ifEmpty { "정면" }
}

private fun Float?.pct() = if (this == null) "-" else "${(this * 100).toInt()}%"

/** 카메라 프레임 → ML Kit 얼굴 검출 → 바운딩 박스와 결과를 프리뷰 위에 그린다 */
@OptIn(ExperimentalGetImage::class)   // imageProxy.image 는 CameraX 실험 API — 명시적으로 허용
@Composable
fun FaceDetectionView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1) 카메라 권한 — Android 6 부터는 실행 중에 물어봐야 한다
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (!granted) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),   // 화면 정중앙
        ) {
            Text("카메라 권한이 필요합니다", style = MaterialTheme.typography.headlineSmall)
            Button(
                onClick = { askPermission.launch(Manifest.permission.CAMERA) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            ) { Text("권한 허용") }
        }
        return
    }

    // 2) 검출기 — 빠른 모드. 화면이 사라지면 닫는다
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)   // 눈 뜸·웃음 확률
                .enableTracking()                                                    // 같은 얼굴에 ID 유지
                .build()
        )
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { detector.close(); executor.shutdown() }
    }

    var faceCount by remember { mutableIntStateOf(0) }
    var lastMs by remember { mutableLongStateOf(0L) }
    var faces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var imgW by remember { mutableIntStateOf(1) }
    var imgH by remember { mutableIntStateOf(1) }

    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 3) 카메라 프리뷰 + 프레임 분석
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

                        // 분석용 프레임은 작게 (640x480) — 처리 속도를 위해
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val media = imageProxy.image
                            if (media == null) { imageProxy.close(); return@setAnalyzer }
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val image = InputImage.fromMediaImage(media, rotation)
                            // 회전을 반영한 '똑바로 선' 이미지 크기 — 박스 좌표는 이 기준으로 온다
                            val upW = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
                            val upH = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
                            val t0 = SystemClock.elapsedRealtime()

                            // 4) ML Kit — 즉시 반환, 결과는 콜백으로 (goTo 와 같은 모양)
                            detector.process(image)
                                .addOnSuccessListener { found ->
                                    val ms = SystemClock.elapsedRealtime() - t0
                                    faces = found; faceCount = found.size; lastMs = ms; imgW = upW; imgH = upH
                                }
                                .addOnFailureListener { e -> Log.e(TAG, "detect failed", e) }
                                .addOnCompleteListener { imageProxy.close() }   // 닫아야 다음 프레임이 온다
                        }

                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )
        }
        // 바운딩 박스 — 분석 이미지 좌표를 화면 좌표로 바꿔 그린다
        //  PreviewView 기본(FILL_CENTER)은 화면을 꽉 채우도록 확대 후 가운데 정렬 → 같은 식으로 환산
        //  전면 카메라 프리뷰는 좌우 반전되어 보이므로 x 도 뒤집는다
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = maxOf(size.width / imgW, size.height / imgH)
            val dx = (size.width - imgW * scale) / 2f
            val dy = (size.height - imgH * scale) / 2f
            faces.forEach { face ->
                val b = face.boundingBox
                val left = size.width - (b.right * scale + dx)     // 좌우 반전
                val right = size.width - (b.left * scale + dx)
                val top = b.top * scale + dy
                val bottom = b.bottom * scale + dy
                drawRect(
                    color = Color(0xFF00E676),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    style = Stroke(width = 6f),
                )
            }
        }

        // 고개 방향 — 화면 위쪽에 크게 표시
        faces.firstOrNull()?.let { f ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "방향: ${headDirection(f)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676),
                )
                Text(
                    "Yaw(좌우) %.1f°   Pitch(위아래) %.1f°   Roll(갸웃) %.1f°".format(
                        f.headEulerAngleY, f.headEulerAngleX, f.headEulerAngleZ),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    "왼눈 ${f.leftEyeOpenProbability.pct()}   오른눈 ${f.rightEyeOpenProbability.pct()}   웃음 ${f.smilingProbability.pct()}   ID ${f.trackingId ?: "-"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }

        // 결과 — 프리뷰 위에 반투명 검정 띠 + 흰 글자로 띄운다
        Text(
            "얼굴 $faceCount 개  ·  처리 ${lastMs}ms" +
                (faces.firstOrNull()?.let { "  ·  첫 얼굴 ${it.boundingBox.width()}×${it.boundingBox.height()}px" } ?: ""),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(vertical = 14.dp, horizontal = 16.dp),
        )
    }
}
