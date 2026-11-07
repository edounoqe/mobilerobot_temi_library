package kr.kw.libri.photobooth

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PhotoBoothScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val appContext = context.applicationContext

    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }

    // 화면 종료 시 카메라 리소스 반환
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isLoading by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val capture = ImageCapture.Builder()
                            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                            .build()
                        imageCapture = capture

                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                        } catch (e: Exception) {
                            Log.e("Camera", "카메라 바인딩 실패", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 상단 종료 버튼
        Button(
            onClick = {
                cameraProvider?.unbindAll()
                onExit()
            },
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f))
        ) {
            Text("종료", color = Color.White)
        }

        // 하단 촬영 버튼
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    val photoFile = File(context.cacheDir, "temi_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    isLoading = true

                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                coroutineScope.launch {
                                    val result = SupabaseManager.uploadAndRecordPhoto(context, photoFile)
                                    isLoading = false

                                    result.onSuccess { downloadUrl ->
                                        val qr = QRCodeGenerator.generate(downloadUrl)
                                        qrBitmap = qr
                                        showQrDialog = true

                                        // 1시간 후 자동 삭제 (ApplicationContext 및 IO 스레드로 안전 처리)
                                        val fileName = photoFile.name
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            if (photoFile.exists()) photoFile.delete()
                                            CoroutineScope(Dispatchers.IO).launch {
                                                SupabaseManager.deleteRemotePhoto(fileName)
                                            }
                                            showQrDialog = false
                                            qrBitmap = null
                                            Toast.makeText(appContext, "1시간이 경과하여 사진이 안전하게 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                        }, 3600000L)
                                    }.onFailure { err ->
                                        Toast.makeText(context, "업로드 실패: ${err.message}", Toast.LENGTH_LONG).show()
                                        if (photoFile.exists()) photoFile.delete()
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isLoading = false
                                Toast.makeText(context, "촬영 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                enabled = !isLoading && hasCameraPermission,
                modifier = Modifier.size(width = 200.dp, height = 56.dp)
            ) {
                Text("네컷 사진 촬영", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 업로드 진행 중 로딩 인디케이터
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("사진 최적화 및 업로드 중...", color = Color.White, fontSize = 16.sp)
                }
            }
        }

        // 다운로드 QR 다이얼로그
        if (showQrDialog && qrBitmap != null) {
            Dialog(onDismissRequest = { showQrDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.padding(16.dp).wrapContentSize()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "스마트폰으로 스캔하세요!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "다운로드 QR 코드",
                            modifier = Modifier.size(240.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "1시간 후 개인정보 보호를 위해\n사진이 서버에서 자동 영구 삭제됩니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showQrDialog = false }) {
                            Text("닫기")
                        }
                    }
                }
            }
        }
    }
}