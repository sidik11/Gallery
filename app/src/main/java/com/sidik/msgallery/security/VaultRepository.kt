package com.sidik.msgallery.security

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.crypto.SecretKey

class VaultRepository(private val context: Context) {
    private val root: File
        get() = File(context.filesDir, "vault").also { it.mkdirs() }

    fun importEncrypted(source: InputStream, originalName: String, key: SecretKey): File {
        val target = File(root, UUID.randomUUID().toString() + ".msgv")
        target.outputStream().use { output -> CryptoEngine.encrypt(source, output, key) }
        return target
    }

    fun decryptToCache(vaultFile: File, key: SecretKey): File {
        val temp = File.createTempFile("ms_gallery_", ".tmp", context.cacheDir)
        vaultFile.inputStream().use { input ->
            temp.outputStream().use { output -> CryptoEngine.decrypt(input, output, key) }
        }
        return temp
    }

    fun listEncrypted(): List<File> =
        root.listFiles { file -> file.isFile && file.extension == "msgv" }
            ?.sortedBy { it.name } ?: emptyList()

    fun deleteEncrypted(file: File): Boolean = file.delete()

    fun clearTemporaryFile(file: File): Boolean = file.delete()
}