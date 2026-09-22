package com.sidik.msgallery.media

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaOperations(private val resolver: ContentResolver) {
    suspend fun copyToPictures(uri: android.net.Uri, displayName: String, mimeType: String): android.net.Uri? =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MS Gallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null
            try {
                resolver.openInputStream(uri)?.use { input ->
                    resolver.openOutputStream(target)?.use { output -> input.copyTo(output, 64 * 1024) }
                        ?: throw IOException("Unable to open destination")
                } ?: throw IOException("Unable to open source")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(target, values, null, null)
                target
            } catch (t: Throwable) {
                resolver.delete(target, null, null)
                throw t
            }
        }
}
