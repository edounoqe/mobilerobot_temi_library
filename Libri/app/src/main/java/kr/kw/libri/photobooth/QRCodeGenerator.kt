package kr.kw.libri.photobooth

import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.journeyapps.barcodescanner.BarcodeEncoder

object QRCodeGenerator {

    private val barcodeEncoder = BarcodeEncoder()

    fun generate(contentUrl: String, size: Int = 400): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1 // 여백을 줄여 QR 본체 가독성 극대화
            )
            barcodeEncoder.encodeBitmap(contentUrl, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (e: Exception) {
            Log.e("QRCodeGenerator", "QR 코드 생성 실패: ${e.message}", e)
            null
        }
    }
}