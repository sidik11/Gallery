package com.sidik.msgallery.media

class AlbumRepository {
    fun groupByFolder(items: List<MediaItem>): Map<String, List<MediaItem>> =
        items.groupBy { item ->
            item.uri.pathSegments.dropLast(1).lastOrNull() ?: "Unknown"
        }
}
