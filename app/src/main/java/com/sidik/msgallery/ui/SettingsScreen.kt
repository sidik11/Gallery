package com.sidik.msgallery.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sidik.msgallery.security.BiometricAuth

@Composable
fun SettingsScreen(
    context: Context,
    onBack: () -> Unit,
    onVault: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Security, null)
                Text("Privacy & Security", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Screenshots are blocked for the entire app. No network permission is used.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        if (context is FragmentActivity && BiometricAuth.canAuthenticate(context)) {
                            BiometricAuth.prompt(context)
                        }
                    }
                ) {
                    Icon(Icons.Default.Fingerprint, null)
                    Text("Test biometric / device unlock", Modifier.padding(start = 8.dp))
                }
            }
        }

        Button(onClick = onVault) {
            Text("Open Secure Vault")
        }

        Button(onClick = onBack) {
            Text("Back to Gallery")
        }
    }
}
