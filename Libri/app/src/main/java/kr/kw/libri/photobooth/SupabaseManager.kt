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

    private const val SUPABASE_URL = "https://oojkigtwasjigzlmsqas.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_OfTOFNmrb-dIcp7uNqHCiA_rPPwWpkJ"

    private const val BUCKET_NAME = "temi_photo"
    private const val TABLE_NAME = "temi_photo"

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