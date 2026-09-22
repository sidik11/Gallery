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
}
