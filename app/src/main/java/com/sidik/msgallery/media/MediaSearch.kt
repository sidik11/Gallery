package com.sidik.msgallery.media

class MediaSearch {
    fun filter(items: List<MediaItem>, query: String, videosOnly: Boolean = false): List<MediaItem> {
        val q = query.trim().lowercase()
        return items.asSequence()
            .filter { !videosOnly || it.type == MediaType.VIDEO }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.mimeType.lowercase().contains(q) }
            .toList()
    }
}
