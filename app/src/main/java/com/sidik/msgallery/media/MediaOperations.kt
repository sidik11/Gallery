package com.sidik.msgallery.media

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaOperations(private val resolver: ContentResolver) {
    suspend fun setFavorite(uri: Uri, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext false
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0) }, null, null) > 0
    }
    suspend fun rename(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains('/') || clean.contains('\\')) return@withContext false
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, clean) }, null, null) > 0
    }
    suspend fun setTrashed(uri: Uri, trashed: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext false
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, if (trashed) 1 else 0) }, null, null) > 0
    }
    suspend fun copyToPictures(uri: Uri, displayName: String, mimeType: String): Uri? = withContext(Dispatchers.IO) {
        val isVideo = mimeType.startsWith("video/")
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val target = resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/MS Gallery" else "Pictures/MS Gallery")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: return@withContext null
        try {
            resolver.openInputStream(uri)?.use { input -> resolver.openOutputStream(target)?.use { output -> input.copyTo(output, 64 * 1024) } ?: error("Destination unavailable") } ?: error("Source unavailable")
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            target
        } catch (t: Throwable) { resolver.delete(target, null, null); throw t }
    }
    suspend fun removeExif(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val mime = resolver.getType(uri) ?: return@withContext false
        if (!mime.startsWith("image/")) return@withContext false
        val temp = kotlin.io.path.createTempFile("ms_exif_", ".jpg").toFile()
        try {
            resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: return@withContext false
            val exif = ExifInterface(temp)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_MAKE, null)
            exif.setAttribute(ExifInterface.TAG_MODEL, null)
            exif.saveAttributes()
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) }
            resolver.update(uri, values, null, null)
            resolver.openOutputStream(uri, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } } ?: return@withContext false
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (_: Throwable) { false } finally { temp.delete() }
    }
}
