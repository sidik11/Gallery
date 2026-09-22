package com.sidik.msgallery.media

import android.net.Uri

enum class MediaType { IMAGE, VIDEO }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateTaken: Long,
    val sizeBytes: Long,
    val type: MediaType,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val folderName: String = "Unknown"
)
