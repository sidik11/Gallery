package com.sidik.msgallery.security

import android.content.Context
import android.util.Base64
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.KeyGenerator
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Persistent app-lock configuration.
 *
 * The PIN itself is never stored. A random salt and a PBKDF2-HMAC-SHA256
 * verifier are wrapped with an Android Keystore AES key. This file is
 * security configuration, not a media cache or database.
 */
class PinLockManager(private val context: Context) {
    private companion object {
        const val ALIAS = "ms_gallery_app_lock_v1"
        const val FILE_NAME = "app_lock.bin"
        const val MAGIC = "MSLP2"
        const val LEGACY_MAGIC = "MSLP1"
        const val SALT_BYTES = 16
        const val VERIFIER_BYTES = 32
        const val ITERATIONS = 210_000
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }

    private val file get() = File(context.filesDir, FILE_NAME)

    fun isEnabled(): Boolean = file.exists()

    fun enable(pin: CharArray) {
        validatePin(pin)
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val verifier = derive(pin, salt)
        val plain = ByteBuffer.allocate(1 + salt.size + verifier.size)
            .put(1)
            .put(salt)
            .put(verifier)
            .array()
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        val encrypted = cipher.doFinal(plain)
        file.outputStream().use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(iv)
            out.write(encrypted)
        }
        pin.fill('\u0000')
        verifier.fill(0)
        salt.fill(0)
        plain.fill(0)
    }

    fun verify(pin: CharArray): Boolean {
        if (!isEnabled()) return true
        return runCatching {
            val bytes = file.readBytes()
            require(bytes.size > 5 + IV_BYTES + 16)
            val magic = String(bytes, 0, 5, Charsets.US_ASCII)\n            require(magic == MAGIC || magic == LEGACY_MAGIC)
            val iv = bytes.copyOfRange(5, 5 + IV_BYTES)
            val encrypted = bytes.copyOfRange(5 + IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            }
            val plain = cipher.doFinal(encrypted)
            val version = plain[0].toInt()
            require(version == 1)
            val salt = plain.copyOfRange(1, 1 + SALT_BYTES)
            val expected = plain.copyOfRange(1 + SALT_BYTES, 1 + SALT_BYTES + VERIFIER_BYTES)
            val actual = if (magic == MAGIC) derive(pin, salt) else deriveLegacy(pin, salt)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false).also {
            pin.fill('\u0000')
        }
    }

    fun disable() {
        file.delete()
    }

    private fun validatePin(pin: CharArray) {
        require(pin.size in 4..12) { "PIN must contain 4 to 12 digits" }
        require(pin.all { it.isDigit() }) { "PIN must contain digits only" }
    }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        var keyBytes = pin.concatToString().toByteArray(Charsets.UTF_8)
        return try {
            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
            var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
            val out = u.copyOf()
            repeat(ITERATIONS - 1) {
                u = mac.doFinal(u)
                for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
            }
            out
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun key(): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
