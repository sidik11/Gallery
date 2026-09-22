package com.sidik.msgallery.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaDetails(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val dateTaken: Long,
    val folder: String,
    val uri: Uri,
    val latitude: Double?,
    val longitude: Double?,
    val cameraMake: String?,
    val cameraModel: String?,
    val orientation: String?
)

class MediaDetailsReader(private val resolver: ContentResolver) {
    suspend fun read(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        var lat: Double? = null
        var lon: Double? = null
        var make: String? = null
        var model: String? = null
        var orientation: String? = null
        if (item.type == MediaType.IMAGE) {
            runCatching {
                resolver.openInputStream(item.uri)?.use { input ->
                    val exif = ExifInterface(input)
                    lat = exif.latLong?.getOrNull(0)
                    lon = exif.latLong?.getOrNull(1)
                    make = exif.getAttribute(ExifInterface.TAG_MAKE)
                    model = exif.getAttribute(ExifInterface.TAG_MODEL)
                    orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION)
                }
            }
        }
        MediaDetails(item.name, item.mimeType, item.sizeBytes, item.width, item.height, item.durationMs, item.dateTaken, item.folderName, item.uri, lat, lon, make, model, orientation)
    }
}
