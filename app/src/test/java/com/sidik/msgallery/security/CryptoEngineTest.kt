package com.sidik.msgallery.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CryptoEngineTest {
    @Test fun roundTripPreservesData() {
        val key = CryptoEngine.randomKey()
        val original = "MS Gallery encrypted offline vault".repeat(100).toByteArray()
        val encrypted = ByteArrayOutputStream()

        CryptoEngine.encrypt(ByteArrayInputStream(original), encrypted, key)

        val decrypted = ByteArrayOutputStream()
        CryptoEngine.decrypt(ByteArrayInputStream(encrypted.toByteArray()), decrypted, key)

        assertArrayEquals(original, decrypted.toByteArray())
    }

    @Test fun roundTripAcrossMultipleChunks() {
        val key = CryptoEngine.randomKey()
        val original = ByteArray(CryptoEngine.CHUNK_SIZE * 2 + 123) { (it * 31).toByte() }
        val encrypted = ByteArrayOutputStream()

        CryptoEngine.encrypt(ByteArrayInputStream(original), encrypted, key)

        val decrypted = ByteArrayOutputStream()
        CryptoEngine.decrypt(ByteArrayInputStream(encrypted.toByteArray()), decrypted, key)

        assertArrayEquals(original, decrypted.toByteArray())
    }

    @Test fun encryptedContainerUsesChunkedVersion() {
        val key = CryptoEngine.randomKey()
        val encrypted = ByteArrayOutputStream()

        CryptoEngine.encrypt(ByteArrayInputStream(byteArrayOf(1, 2, 3)), encrypted, key)

        val bytes = encrypted.toByteArray()
        assertArrayEquals(CryptoEngine.MAGIC.toByteArray(Charsets.US_ASCII), bytes.copyOfRange(0, 5))
        org.junit.Assert.assertEquals(CryptoEngine.VERSION_CHUNKED, bytes[5].toInt())
    }
}
