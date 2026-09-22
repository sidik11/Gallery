package com.sidik.msgallery.security

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
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
    fun readPreview(vaultFile: File, key: SecretKey, maxBytes: Int = 64): ByteArray {
        require(vaultFile.isFile && vaultFile.extension == "msgv") { "Invalid vault file" }
        RandomAccessFile(vaultFile, "r").use { input ->
            val magic = ByteArray(5)
            require(input.read(magic) == 5 && String(magic, Charsets.US_ASCII) == CryptoEngine.MAGIC)
            when (input.read()) {
                CryptoEngine.VERSION_CHUNKED -> {
                    val chunkSize = input.readInt()
                    require(chunkSize in 64 * 1024..8 * 1024 * 1024)
                    val plainSize = input.readInt()
                    require(plainSize in 1..chunkSize)
                    val iv = ByteArray(CryptoEngine.IV_SIZE)
                    require(input.read(iv) == iv.size)
                    val encrypted = ByteArray(plainSize + 16)
                    require(input.read(encrypted) == encrypted.size)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoEngine.TAG_BITS, iv))
                    }
                    return cipher.doFinal(encrypted).copyOf(minOf(maxBytes, plainSize))
                }
                CryptoEngine.VERSION_LEGACY -> {
                    val ivSize = input.read()
                    require(ivSize == CryptoEngine.IV_SIZE)
                    val iv = ByteArray(ivSize)
                    require(input.read(iv) == ivSize)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoEngine.TAG_BITS, iv))
                    }
                    CipherInputStream(input, cipher).use { it.readNBytes(maxBytes) }
                }
                else -> error("Unsupported vault version")
            }
        }
    }

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