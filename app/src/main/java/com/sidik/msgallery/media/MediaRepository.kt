package com.sidik.msgallery.media

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val resolver: ContentResolver) {
    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<MediaItem>(256)
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.IS_FAVORITE
        )
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        resolver.query(
            collection, projection, selection, args,
            MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val added = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val modified = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val type = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val width = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val height = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
            val bucket = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val favorite = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)

            while (cursor.moveToNext()) {
                val video = cursor.getInt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val dateSeconds = if (cursor.getLong(added) > 0) cursor.getLong(added)
                    else cursor.getLong(modified)
                result += MediaItem(
                    id = cursor.getLong(id),
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(id)),
                    name = cursor.getString(name) ?: "Unnamed",
                    mimeType = cursor.getString(mime) ?: "application/octet-stream",
                    dateTaken = dateSeconds * 1000L,
                    sizeBytes = cursor.getLong(size),
                    type = if (video) MediaType.VIDEO else MediaType.IMAGE,
                    width = cursor.getInt(width),
                    height = cursor.getInt(height),
                    durationMs = if (video) cursor.getLong(duration) else 0L,
                    folderName = cursor.getString(bucket) ?: "Unknown",
                    isFavorite = cursor.getInt(favorite) != 0
                )
            }
        }
        result
    }
}
