package com.sidik.msgallery.media

data class MediaFilter(
    val type: MediaType? = null,
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val minWidth: Int? = null,
    val minHeight: Int? = null,
    val minDurationMs: Long? = null,
    val maxDurationMs: Long? = null,
    val afterDateMs: Long? = null,
    val beforeDateMs: Long? = null,
    val folder: String? = null
)

class MediaSearch {
    fun filter(items: List<MediaItem>, query: String, videosOnly: Boolean = false): List<MediaItem> =
        filter(items, query, MediaFilter(type = if (videosOnly) MediaType.VIDEO else null))

    fun filter(items: List<MediaItem>, query: String, filter: MediaFilter): List<MediaItem> {
        val q = query.trim().lowercase()
        return items.asSequence()
            .filter { filter.type == null || it.type == filter.type }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.mimeType.lowercase().contains(q) || it.folderName.lowercase().contains(q) }
            .filter { filter.minSizeBytes == null || it.sizeBytes >= filter.minSizeBytes }
            .filter { filter.maxSizeBytes == null || it.sizeBytes <= filter.maxSizeBytes }
            .filter { filter.minWidth == null || it.width >= filter.minWidth }
            .filter { filter.minHeight == null || it.height >= filter.minHeight }
            .filter { filter.minDurationMs == null || it.durationMs >= filter.minDurationMs }
            .filter { filter.maxDurationMs == null || it.durationMs <= filter.maxDurationMs }
            .filter { filter.afterDateMs == null || it.dateTaken >= filter.afterDateMs }
            .filter { filter.beforeDateMs == null || it.dateTaken <= filter.beforeDateMs }
            .filter { filter.folder.isNullOrBlank() || it.folderName.equals(filter.folder.trim(), ignoreCase = true) }
            .toList()
    }
}