package com.sidik.msgallery.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeyManager {
    private val alias = "ms_gallery_vault_v1"
    private val storeType = "AndroidKeyStore"

    fun getOrCreateVaultKey(): SecretKey {
        val store = KeyStore.getInstance(storeType).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, storeType)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun deleteVaultKey() {
        KeyStore.getInstance(storeType).apply {
            load(null)
            deleteEntry(alias)
        }
    }
}
