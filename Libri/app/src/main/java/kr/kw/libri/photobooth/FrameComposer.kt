package kr.kw.libri.photobooth

import kr.kw.libri.R

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * 사진 4장과 프레임 PNG를 합쳐 1200x3600 비트맵을 만든다.
 * 순서: 흰 배경 -> 사진 4장(구멍 위치) -> 프레임(맨 위, 구멍은 투명이라 사진이 보임)
 */
object FrameComposer {
    // 구멍 가장자리의 반투명 픽셀 때문에 흰 틈이 보이지 않도록 사진을 살짝 크게 깐다.
    // 프레임의 불투명 테두리가 위에서 덮으므로 눈에 띄지 않는다.
    private const val BLEED_PX = 4
    private const val PLACEHOLDER_COLOR = 0xFFD9D4CA.toInt()

    /** photos[i] 가 null 이면 해당 칸은 회색 자리표시로 채운다. */
    fun compose(context: Context, frameResId: Int, photos: List<Bitmap?>): Bitmap {
        val result = Bitmap.createBitmap(FrameSpecs.WIDTH, FrameSpecs.HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val fillPaint = Paint().apply { color = PLACEHOLDER_COLOR }

        FrameSpecs.slotsFor(frameResId).forEachIndexed { index, slot ->
            val dst = Rect(
                (slot.left - BLEED_PX).coerceAtLeast(0),
                (slot.top - BLEED_PX).coerceAtLeast(0),
                (slot.right + BLEED_PX).coerceAtMost(FrameSpecs.WIDTH),
                (slot.bottom + BLEED_PX).coerceAtMost(FrameSpecs.HEIGHT)
            )
            val photo = photos.getOrNull(index)
            if (photo == null || photo.isRecycled) {
                canvas.drawRect(dst, fillPaint)
            } else {
                canvas.drawBitmap(photo, centerCropSrc(photo, dst), dst, photoPaint)
            }
        }

        // drawable 폴더 이미지는 기기 밀도에 맞춰 자동 확대/축소되므로 inScaled=false 로 원본 1200x3600 유지
        val options = BitmapFactory.Options().apply { inScaled = false }
        val frame = BitmapFactory.decodeResource(context.resources, frameResId, options)
        if (frame != null) {
            canvas.drawBitmap(frame, null, Rect(0, 0, FrameSpecs.WIDTH, FrameSpecs.HEIGHT), photoPaint)
            frame.recycle()
        }
        return result
    }

    /** 사진 비율을 유지하면서 칸을 가득 채우도록 가운데를 잘라낼 원본 영역 계산 (centerCrop) */
    private fun centerCropSrc(photo: Bitmap, dst: Rect): Rect {
        val scale = maxOf(dst.width().toFloat() / photo.width, dst.height().toFloat() / photo.height)
        val srcW = (dst.width() / scale).toInt().coerceIn(1, photo.width)
        val srcH = (dst.height() / scale).toInt().coerceIn(1, photo.height)
        val left = (photo.width - srcW) / 2
        val top = (photo.height - srcH) / 2
        return Rect(left, top, left + srcW, top + srcH)
    }

    // 구멍(최대 약 1100x700)보다 충분히 크게만 읽어서 메모리를 아낀다.
    private const val MAX_PHOTO_SIDE = 2400

    /** 촬영된 사진 파일 경로 목록을 비트맵 4칸 리스트로 읽는다. 없거나 읽기 실패하면 null. */
    fun loadPhotos(paths: List<String>?): List<Bitmap?> =
        List(4) { i -> paths?.getOrNull(i)?.let { decodePhoto(it) } }

    private fun decodePhoto(path: String): Bitmap? {
        if (!File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return applyExifOrientation(path, decoded)
    }

    /** CameraX 는 회전/좌우반전을 JPEG EXIF 에만 기록하므로, 비트맵에도 그대로 적용해줘야 똑바로 보인다. */
    private fun applyExifOrientation(path: String, src: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            else -> return src
        }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (rotated !== src) src.recycle()
        return rotated
    }

    /** 합성 결과를 PNG로 저장한다. (QR/업로드 연동 시 이 파일을 사용) */
    fun saveToCache(context: Context, bitmap: Bitmap, fileName: String = "photobooth_result.png"): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
