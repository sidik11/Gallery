package com.sidik.msgallery.security

import android.content.Context
import java.io.File
import java.util.UUID

class VaultRepository(private val context: Context) {
    private val root: File
        get() = File(context.filesDir, "vault").also { it.mkdirs() }

    fun importEncrypted(source: java.io.InputStream, originalName: String, key: ByteArray): File {
        val safeName = originalName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "media.bin" }
        val target = File(root, UUID.randomUUID().toString() + ".msgv")
        target.outputStream().use { output -> CryptoEngine.encrypt(source, output, key) }
        return target
    }

    fun decryptToTemporaryFile(vaultFile: File, key: ByteArray): File {
        val temp = File.createTempFile("msgallery_", ".tmp", context.cacheDir)
        vaultFile.inputStream().use { input ->
            temp.outputStream().use { output -> CryptoEngine.decrypt(input, output, key) }
        }
        return temp
    }

    fun listEncrypted(): List<File> = root.listFiles { file -> file.extension == "msgv" }
        ?.sortedBy { it.name } ?: emptyList()

    fun deleteEncrypted(file: File): Boolean = file.delete()
}
