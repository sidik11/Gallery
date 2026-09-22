package com.sidik.msgallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sidik.msgallery.security.PinLockManager
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    manager: PinLockManager,
    onUnlocked: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("MS Gallery is locked")
        Text("Enter your PIN to continue.")
        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 12 && it.all(Char::isDigit)) {
                    pin = it
                    error = false
                }
            },
            singleLine = true,
            enabled = !checking,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("PIN") }
        )
        if (error) Text("Incorrect PIN. Please wait before trying again.")
        Button(
            enabled = pin.length >= 4 && !checking,
            onClick = {
                val entered = pin
                checking = true
                error = false
                scope.launch {
                    val valid = manager.verifyWithThrottle(entered.toCharArray())
                    checking = false
                    if (valid) {
                        pin = ""
                        onUnlocked()
                    } else {
                        pin = ""
                        error = true
                    }
                }
            }
        ) {
            Text(if (checking) "Checking…" else "Unlock")
        }
    }
}
