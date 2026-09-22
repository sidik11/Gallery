package com.sidik.msgallery.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThumbnailEngine(private val resolver: ContentResolver) {
    suspend fun load(uri: android.net.Uri, width: Int = 512, height: Int = 512): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                resolver.loadThumbnail(uri, Size(width, height), null)
            }.getOrNull()
        }
}
