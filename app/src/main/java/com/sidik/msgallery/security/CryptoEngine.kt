package com.sidik.msgallery.security

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    const val MAGIC = "MSGV1"
    const val VERSION_LEGACY = 1
    const val VERSION_CHUNKED = 2
    const val IV_SIZE = 12
    const val TAG_BITS = 128
    const val CHUNK_SIZE = 1024 * 1024

    fun randomKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun encrypt(input: InputStream, output: OutputStream, key: ByteArray) =
        encrypt(input, output, SecretKeySpec(key, "AES"))

    fun encrypt(input: InputStream, output: OutputStream, key: SecretKey) {
        require(key.encoded?.size == 32) { "Vault key must be 256-bit AES" }
        output.write(MAGIC.toByteArray(Charsets.US_ASCII))
        output.write(VERSION_CHUNKED)
        output.write(ByteBuffer.allocate(4).putInt(CHUNK_SIZE).array())

        val buffer = ByteArray(CHUNK_SIZE)
        try {
            while (true) {
                val count = input.readFullyUpTo(buffer)
                if (count <= 0) break

                val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                }
                val encrypted = cipher.doFinal(buffer, 0, count)

                output.write(ByteBuffer.allocate(4).putInt(count).array())
                output.write(iv)
                output.write(encrypted)
                iv.fill(0)
                encrypted.fill(0)
            }
        } finally {
            buffer.fill(0)
        }
    }

    fun decrypt(input: InputStream, output: OutputStream, key: ByteArray) =
        decrypt(input, output, SecretKeySpec(key, "AES"))

    fun decrypt(input: InputStream, output: OutputStream, key: SecretKey) {
        require(key.encoded?.size == 32) { "Vault key must be 256-bit AES" }
        val magic = input.readNBytes(5)
        if (magic.size != 5 || String(magic, Charsets.US_ASCII) != MAGIC) {
            error("Invalid MS Gallery vault file")
        }
        when (input.read()) {
            VERSION_LEGACY -> decryptLegacy(input, output, key)
            VERSION_CHUNKED -> decryptChunked(input, output, key)
            else -> error("Unsupported vault version")
        }
    }

    private fun decryptLegacy(input: InputStream, output: OutputStream, key: SecretKey) {
        val ivSize = input.read()
        if (ivSize != IV_SIZE) error("Invalid vault IV")
        val iv = input.readNBytes(ivSize)
        if (iv.size != ivSize) error("Truncated vault header")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        CipherInputStream(input, cipher).use { decrypted ->
            decrypted.copyTo(output, DEFAULT_BUFFER)
        }
        iv.fill(0)
    }

    private fun decryptChunked(input: InputStream, output: OutputStream, key: SecretKey) {
        val chunkSizeBytes = input.readNBytes(4)
        if (chunkSizeBytes.size != 4) error("Truncated vault chunk header")
        val chunkSize = ByteBuffer.wrap(chunkSizeBytes).int
        require(chunkSize in 64 * 1024..8 * 1024 * 1024) { "Invalid vault chunk size" }

        val header = ByteArray(4)
        val iv = ByteArray(IV_SIZE)
        try {
            while (true) {
                val first = input.read()
                if (first < 0) break
                header[0] = first.toByte()
                if (input.readNBytesInto(header, 1, 3) != 3) error("Truncated vault chunk")
                val plainSize = ByteBuffer.wrap(header).int
                require(plainSize in 1..chunkSize) { "Invalid vault plaintext size" }
                if (input.readNBytesInto(iv, 0, IV_SIZE) != IV_SIZE) error("Truncated vault chunk IV")
                val encrypted = input.readNBytes(plainSize + 16)
                if (encrypted.size != plainSize + 16) error("Truncated vault chunk data")

                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                }
                output.write(cipher.doFinal(encrypted))
                encrypted.fill(0)
            }
        } finally {
            header.fill(0)
            iv.fill(0)
        }
    }

    private fun InputStream.readFullyUpTo(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            if (read == 0) continue
            total += read
        }
        return total
    }

    private fun InputStream.readNBytesInto(buffer: ByteArray, offset: Int, length: Int): Int {
        var total = 0
        while (total < length) {
            val n = read(buffer, offset + total, length - total)
            if (n < 0) break
            if (n == 0) continue
            total += n
        }
        return total
    }

    private const val DEFAULT_BUFFER = 64 * 1024
}
