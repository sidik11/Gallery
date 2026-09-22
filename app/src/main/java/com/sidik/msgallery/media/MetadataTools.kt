package com.sidik.msgallery.media

import android.content.ContentResolver
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetadataTools(private val resolver: ContentResolver) {
    suspend fun readImageMetadata(uri: android.net.Uri): Map<String, String> =
        withContext(Dispatchers.IO) {
            runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { fd ->
                    val exif = ExifInterface(fd.fileDescriptor)
                    buildMap {
                        exif.getAttribute(ExifInterface.TAG_DATETIME)?.let { put("Date", it) }
                        exif.getAttribute(ExifInterface.TAG_MAKE)?.let { put("Camera", it) }
                        exif.getAttribute(ExifInterface.TAG_MODEL)?.let { put("Model", it) }
                        exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)?.let { put("GPS latitude", it) }
                        exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)?.let { put("GPS longitude", it) }
                        exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)?.let { put("Width", it) }
                        exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)?.let { put("Height", it) }
                    }
                } ?: emptyMap()
            }.getOrDefault(emptyMap())
        }
}
