package com.sidik.msgallery.media

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaOperations(private val resolver: ContentResolver) {

    suspend fun setFavorite(uri: Uri, favorite: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext false
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
            }
            resolver.update(uri, values, null, null) > 0
        }

    suspend fun rename(uri: Uri, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            val clean = newName.trim()
            if (clean.isEmpty() || clean.contains('/') || clean.contains('\\')) return@withContext false
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, clean) },
                null,
                null
            ) > 0
        }

    suspend fun setTrashed(uri: Uri, trashed: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext false
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, if (trashed) 1 else 0) },
                null,
                null
            ) > 0
        }

    suspend fun copyToPictures(uri: Uri, displayName: String, mimeType: String): Uri? =
        withContext(Dispatchers.IO) {
            val isVideo = mimeType.startsWith("video/")
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/MS Gallery" else "Pictures/MS Gallery")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val target = resolver.insert(collection, values) ?: return@withContext null
            try {
                resolver.openInputStream(uri)?.use { input ->
                    resolver.openOutputStream(target)?.use { output -> input.copyTo(output, 64 * 1024) }
                        ?: error("Unable to open destination")
                } ?: error("Unable to open source")
                resolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null, null
                )
                target
            } catch (t: Throwable) {
                resolver.delete(target, null, null)
                throw t
            }
        }
}
