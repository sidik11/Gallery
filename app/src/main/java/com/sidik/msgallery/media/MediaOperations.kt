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
        if (mime.equals("image/gif", ignoreCase = true) || mime.equals("image/svg+xml", ignoreCase = true)) {
            return@withContext false
        }

        val descriptor = runCatching {
            resolver.openFileDescriptor(uri, "rw")
        }.getOrNull() ?: return@withContext false

        descriptor.use { pfd ->
            runCatching {
                val exif = ExifInterface(pfd.fileDescriptor)
                val tags = arrayOf(
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_DEST_LATITUDE,
                    ExifInterface.TAG_GPS_DEST_LONGITUDE,
                    ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
                    ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_SOFTWARE,
                    ExifInterface.TAG_ARTIST,
                    ExifInterface.TAG_COPYRIGHT,
                    ExifInterface.TAG_USER_COMMENT,
                    ExifInterface.TAG_IMAGE_DESCRIPTION,
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED
                )
                tags.forEach { exif.setAttribute(it, null) }
                exif.saveAttributes()
                true
            }.getOrDefault(false)
        }
    }
}
