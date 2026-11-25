package kr.kw.libri.photobooth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream

@Serializable
data class PhotoRecord(
    @SerialName("image_url")
    val imageUrl: String
)

object SupabaseManager {

    private const val SUPABASE_URL = "https://tyakhgrlmaqckoltlidu.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_dpQiY-06gNnG4QoWK4zs-Q_XxuZdjro"

    private const val BUCKET_NAME = "photos"
    private const val TABLE_NAME = "temi_photos"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Storage)
        install(Postgrest)
    }

    // 1. 원본 이미지 압축 (JPEG 80%) 후 Storage 업로드 및 DB 기록
    suspend fun uploadAndRecordPhoto(context: Context, originalFile: File): Result<String> = withContext(Dispatchers.IO) {
        val compressedFile = File(context.cacheDir, "compressed_${originalFile.name}")
        try {
            val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
                ?: return@withContext Result.failure(IllegalStateException("비트맵 디코딩에 실패했습니다."))

            FileOutputStream(compressedFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
            }
            bitmap.recycle()

            val bucket = client.storage.from(BUCKET_NAME)
            val bytes = compressedFile.readBytes()
            bucket.upload(
                path = originalFile.name,
                data = bytes,
                upsert = true
            )

            val publicUrl = bucket.publicUrl(originalFile.name)

            client.postgrest[TABLE_NAME].insert(PhotoRecord(imageUrl = publicUrl))
            Log.d("Supabase", "DB 기록 및 업로드 완료: $publicUrl")

            Result.success(publicUrl)
        } catch (e: Exception) {
            Log.e("Supabase", "업로드/DB 저장 실패: ${e.message}", e)
            Result.failure(e)
        } finally {
            if (compressedFile.exists()) {
                compressedFile.delete()
            }
        }
    }

    /** 사진 보관 시간 — 이보다 오래된 네컷 사진은 Storage 에서 지운다 */
    const val PHOTO_KEEP_MS = 60 * 60 * 1000L
    private val STAMPED = Regex("""^libri4cut_(\d{13})\.png$""")

    /**
     * 1시간이 지난 네컷 사진을 Storage 에서 지운다. 파일 이름의 찍은 시각(libri4cut_<ms>.png)으로 판단한다.
     * 앱 안의 타이머 하나에 기대지 않고, 앱이 켜져 있는 동안 주기적으로 · 새 사진을 올릴 때마다 부른다
     * (앱이 꺼졌다 켜져도 밀린 사진을 다음 정리 때 지운다).
     * @return 지운 파일 수 (실패하면 0)
     */
    suspend fun cleanupExpiredPhotos(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        try {
            val bucket = client.storage.from(BUCKET_NAME)
            val expired = bucket.list().map { it.name }.filter { name ->
                val taken = STAMPED.find(name)?.groupValues?.get(1)?.toLongOrNull() ?: return@filter false
                now - taken >= PHOTO_KEEP_MS
            }
            if (expired.isNotEmpty()) {
                bucket.delete(expired)
                Log.d("Supabase", "오래된 네컷 사진 ${expired.size}장 삭제")
            }
            expired.size
        } catch (e: Exception) {
            Log.e("Supabase", "오래된 사진 정리 실패: ${e.message}", e)
            0
        }
    }

    // 2. Storage의 원격 사진 삭제
    suspend fun deleteRemotePhoto(fileName: String) = withContext(Dispatchers.IO) {
        try {
            val bucket = client.storage.from(BUCKET_NAME)
            bucket.delete(listOf(fileName))
            Log.d("Supabase", "Storage 사진 삭제 완료: $fileName")
        } catch (e: Exception) {
            Log.e("Supabase", "Storage 사진 삭제 실패: ${e.message}", e)
        }
    }
}