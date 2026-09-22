package com.sidik.msgallery.media

import android.content.ContentResolver
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

data class StorageSummary(
    val itemCount: Int,
    val imageCount: Int,
    val videoCount: Int,
    val totalBytes: Long,
    val largest: MediaItem?,
    val deviceTotalBytes: Long,
    val deviceFreeBytes: Long
)

data class DuplicateGroup(val hash: String, val items: List<MediaItem>) {
    val wastedBytes: Long get() = items.drop(1).sumOf { it.sizeBytes }
}

class StorageAnalyzer(private val resolver: ContentResolver? = null) {
    fun summarize(items: List<MediaItem>): StorageSummary {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return StorageSummary(items.size, items.count { it.type == MediaType.IMAGE }, items.count { it.type == MediaType.VIDEO }, items.sumOf { it.sizeBytes }, items.maxByOrNull { it.sizeBytes }, stat.totalBytes, stat.availableBytes)
    }

    suspend fun exactDuplicates(items: List<MediaItem>): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val contentResolver = resolver ?: return@withContext emptyList()
        val groups = ArrayList<DuplicateGroup>()
        for ((_, candidates) in items.filter { it.sizeBytes > 0 }.groupBy { it.sizeBytes }.filterValues { it.size > 1 }) {
            val hashed = candidates.mapNotNull { item ->
                runCatching { item to sha256(contentResolver.openInputStream(item.uri) ?: return@runCatching null) }.getOrNull()
            }
            hashed.groupBy { it.second }.filterValues { it.size > 1 }.forEach { (hash, pairs) ->
                groups += DuplicateGroup(hash, pairs.map { it.first })
            }
        }
        groups.sortedByDescending { it.wastedBytes }
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}