package com.sidik.msgallery.security

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Persistent storage for encrypted vault containers.
 *
 * The vault itself is intentional app-private persistent storage.
 * Decrypted media is never written to cache/files; callers receive
 * plaintext only as a transient in-memory ByteArray.
 */
class VaultRepository(private val context: android.content.Context) {
    private val root: File
        get() = File(context.filesDir, "vault").also { it.mkdirs() }

    fun importEncrypted(source: InputStream, originalName: String, key: SecretKey): File {
        val target = File(root, UUID.randomUUID().toString() + ".msgv")
        target.outputStream().use { output ->
            CryptoEngine.encrypt(source, output, key)
        }
        return target
    }

    /**
     * Decrypts a vault item into RAM only.
     *
     * This intentionally does not use cacheDir, filesDir or any other
     * persistent/temp filesystem location. The returned ByteArray should
     * be released by the caller as soon as the viewer no longer needs it.
     */
    fun decryptToMemory(vaultFile: File, key: SecretKey): ByteArray {
        require(vaultFile.isFile && vaultFile.extension == "msgv") {
            "Invalid vault file"
        }

        val output = ByteArrayOutputStream()
        vaultFile.inputStream().use { input ->
            CryptoEngine.decrypt(input, output, key)
        }
        return output.toByteArray()
    }

    fun listEncrypted(): List<File> =
        root.listFiles { file -> file.isFile && file.extension == "msgv" }
            ?.sortedBy { it.name } ?: emptyList()

    fun deleteEncrypted(file: File): Boolean = file.delete()
}