package com.sidik.msgallery.security

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val MAGIC = "MSGV1"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    fun randomKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun encrypt(input: InputStream, output: OutputStream, key: ByteArray) = encrypt(input, output, SecretKeySpec(key, "AES"))

    fun encrypt(input: InputStream, output: OutputStream, key: SecretKey) {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        output.write(MAGIC.toByteArray(Charsets.US_ASCII))
        output.write(byteArrayOf(1))
        output.write(iv.size)
        output.write(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        CipherOutputStream(output, cipher).use { encrypted ->
            input.copyTo(encrypted, DEFAULT_BUFFER)
        }
    }

    fun decrypt(input: InputStream, output: OutputStream, key: ByteArray) = decrypt(input, output, SecretKeySpec(key, "AES"))

    fun decrypt(input: InputStream, output: OutputStream, key: SecretKey) {
        require(key.encoded?.size == 32) { "Vault key must be 256-bit AES" }
        val magic = ByteArray(5)
        if (input.readNBytes(magic) != 5 || String(magic, Charsets.US_ASCII) != MAGIC) {
            error("Invalid MS Gallery vault file")
        }
        val version = input.read()
        if (version != 1) error("Unsupported vault version")
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
    }

    private const val DEFAULT_BUFFER = 64 * 1024
}
