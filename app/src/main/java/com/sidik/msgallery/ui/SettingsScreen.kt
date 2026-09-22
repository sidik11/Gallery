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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sidik.msgallery.security.BiometricAuth
import com.sidik.msgallery.security.PinLockManager

@Composable
fun SettingsScreen(
    context: Context,
    onBack: () -> Unit,
    onVault: () -> Unit,
    onStorage: () -> Unit,
    pinLock: PinLockManager
) {
    var showPinSetup by remember { mutableStateOf(false) }
    var pinEnabled by remember { mutableStateOf(pinLock.isEnabled()) }

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

        Button(onClick = { showPinSetup = true }) {
            Text(if (pinEnabled) "Change App PIN" else "Enable App PIN")
        }

        if (pinEnabled) {
            Button(onClick = {
                pinLock.disable()
                pinEnabled = false
            }) {
                Text("Disable App PIN")
            }
        }

        Button(onClick = onStorage) {
            Text("Storage & Duplicate Analyzer")
        }

        Button(onClick = onVault) {
            Text("Open Secure Vault")
        }

        Button(onClick = onBack) {
            Text("Back to Gallery")
        }
    }

    if (showPinSetup) {
        PinSetupDialog(
            manager = pinLock,
            onDismiss = { showPinSetup = false },
            onSaved = { showPinSetup = false; pinEnabled = true }
        )
    }
}


@Composable
private fun PinSetupDialog(
    manager: PinLockManager,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set App PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = first,
                    onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) { first = it; error = null } },
                    label = { Text("New PIN") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = second,
                    onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) { second = it; error = null } },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    first.length !in 4..12 -> error = "PIN must contain 4 to 12 digits"
                    first != second -> error = "PINs do not match"
                    else -> {
                        manager.enable(first.toCharArray())
                        onSaved()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
