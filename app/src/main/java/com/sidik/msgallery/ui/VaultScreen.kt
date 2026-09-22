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
import com.sidik.msgallery.security.KeyManager
import com.sidik.msgallery.security.VaultRepository

@Composable
fun VaultScreen(
    context: Context,
    onBack: () -> Unit,
    onImport: () -> Unit
) {
    val repository = remember { VaultRepository(context) }
    val keyManager = remember { KeyManager() }
    var unlocked by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(repository.listEncrypted().size) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Secure Vault", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Encrypted vault storage uses AES-256-GCM. Files receive random identifiers and do not retain their original names.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (!unlocked) {
            Button(onClick = {
                if (context is androidx.fragment.app.FragmentActivity &&
                    BiometricAuth.canAuthenticate(context)
                ) {
                    BiometricAuth.prompt(context) { success -> unlocked = success }
                }
            }) { Text("Unlock Vault") }
        } else {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$count encrypted item(s)", style = MaterialTheme.typography.titleMedium)
                    Text("The vault directory contains only encrypted .msgv containers.")
                }
            }
            Button(onClick = {
                if (context is androidx.fragment.app.FragmentActivity &&
                    BiometricAuth.canAuthenticate(context)
                ) {
                    BiometricAuth.prompt(context) { success -> if (success) onImport() }
                }
            }) { Text("Import encrypted media") }

            Button(onClick = { count = repository.listEncrypted().size }) {
                Text("Refresh")
            }

            Button(onClick = { unlocked = false }) {
                Text("Lock Vault")
            }
        }

        Button(onClick = onBack) { Text("Back") }
    }
}