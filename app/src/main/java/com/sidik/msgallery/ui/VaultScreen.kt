package com.sidik.msgallery.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidik.msgallery.security.BiometricAuth
import com.sidik.msgallery.security.VaultRepository

@Composable
fun VaultScreen(
    context: Context,
    onBack: () -> Unit,
    onImport: () -> Unit
) {
    val repository = remember { VaultRepository(context) }
    var unlocked by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(repository.listEncrypted().size) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Secure Vault", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Vault files are encrypted with AES-256-GCM. The encryption key is protected by Android Keystore.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (!unlocked) {
            Button(
                onClick = {
                    if (BiometricAuth.canAuthenticate(context) && context is androidx.fragment.app.FragmentActivity) {
                        BiometricAuth.prompt(context, onResult = { success -> unlocked = success })
                    }
                }
            ) {
                Text("Unlock Vault")
            }
        } else {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$count encrypted item(s)", style = MaterialTheme.typography.titleMedium)
                    Text("Encrypted files never expose their original filename in the vault directory.")
                }
            }
            Button(onClick = {
                if (BiometricAuth.canAuthenticate(context) && context is androidx.fragment.app.FragmentActivity) {
                    BiometricAuth.prompt(context, onResult = { success -> if (success) onImport() })
                }
            }) {
                Text("Import encrypted file")
            }
            Button(onClick = {
                count = repository.listEncrypted().size
            }) {
                Text("Refresh")
            }
        }

        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
