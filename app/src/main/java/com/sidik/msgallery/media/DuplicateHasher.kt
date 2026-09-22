package com.sidik.msgallery.media

import android.content.ContentResolver
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DuplicateHasher(private val resolver: ContentResolver) {
    suspend fun sha256(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            } ?: return@withContext null
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
