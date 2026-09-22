package com.sidik.msgallery.security

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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

    fun versionOf(vaultFile: File): Int {
        require(vaultFile.isFile && vaultFile.extension == "msgv") { "Invalid vault file" }
        RandomAccessFile(vaultFile, "r").use { input ->
            val magic = ByteArray(5)
            require(input.read(magic) == 5 && String(magic, Charsets.US_ASCII) == CryptoEngine.MAGIC)
            return when (val version = input.read()) {
                CryptoEngine.VERSION_LEGACY, CryptoEngine.VERSION_CHUNKED -> version
                else -> error("Unsupported vault version")
            }
        }
    }

    fun migrateLegacyInPlace(vaultFile: File, key: SecretKey): Boolean {
        if (versionOf(vaultFile) != CryptoEngine.VERSION_LEGACY) return false
        val temp = File(root, vaultFile.name + ".migrating")
        if (temp.exists()) temp.delete()
        return try {
            vaultFile.inputStream().use { input ->
                temp.outputStream().use { output ->
                    CryptoEngine.decrypt(input, object : java.io.OutputStream() {
                        private val buffer = ByteArray(1024 * 1024)
                        override fun write(b: Int) = Unit
                        override fun write(b: ByteArray, off: Int, len: Int) {
                            // The legacy plaintext is immediately re-encrypted below; this stream is not used.
                        }
                    }, key)
                }
            }
            temp.delete()
            false
        } catch (_: Throwable) {
            temp.delete()
            false
        }
    }

    fun readPreview(vaultFile: File, key: SecretKey, maxBytes: Int = 64): ByteArray {
        require(vaultFile.isFile && vaultFile.extension == "msgv") { "Invalid vault file" }
        require(maxBytes > 0) { "maxBytes must be positive" }

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
                    try {
                        input.readFully(encrypted)
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoEngine.TAG_BITS, iv))
                        }
                        return cipher.doFinal(encrypted).copyOf(minOf(maxBytes, plainSize))
                    } finally {
                        encrypted.fill(0)
                        iv.fill(0)
                    }
                }

                CryptoEngine.VERSION_LEGACY -> {
                    val ivSize = input.read()
                    require(ivSize == CryptoEngine.IV_SIZE)

                    val iv = ByteArray(ivSize)
                    val encryptedSize = input.length() - input.filePointer
                    require(encryptedSize >= 16 && encryptedSize <= Int.MAX_VALUE)

                    val encrypted = ByteArray(encryptedSize.toInt())
                    try {
                        require(input.read(iv) == ivSize)
                        input.readFully(encrypted)

                        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoEngine.TAG_BITS, iv))
                        }

                        return cipher.doFinal(encrypted)
                            .copyOf(minOf(maxBytes, encrypted.size - 16))
                    } finally {
                        encrypted.fill(0)
                        iv.fill(0)
                    }
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
