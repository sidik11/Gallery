package com.sidik.msgallery.media

data class StorageSummary(
    val itemCount: Int,
    val imageCount: Int,
    val videoCount: Int,
    val totalBytes: Long,
    val largest: MediaItem?
)

class StorageAnalyzer {
    fun summarize(items: List<MediaItem>): StorageSummary =
        StorageSummary(
            itemCount = items.size,
            imageCount = items.count { it.type == MediaType.IMAGE },
            videoCount = items.count { it.type == MediaType.VIDEO },
            totalBytes = items.sumOf { it.sizeBytes },
            largest = items.maxByOrNull { it.sizeBytes }
        )
}
