package com.sidik.msgallery.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.sidik.msgallery.security.BiometricAuth
import com.sidik.msgallery.security.KeyManager
import com.sidik.msgallery.security.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class VaultType { IMAGE, VIDEO, UNKNOWN }

@Composable
fun VaultScreen(
    context: Context,
    onBack: () -> Unit,
    onImport: () -> Unit
) {
    val repository = remember { VaultRepository(context) }
    val keyManager = remember { KeyManager() }
    var unlocked by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(repository.listEncrypted()) }
    var selected by remember { mutableStateOf<File?>(null) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Secure Vault", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }

        Text(
            "Encrypted containers only. Decrypted media is held in RAM and is never written to cache or temporary files.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (!unlocked) {
            Button(
                enabled = context is androidx.fragment.app.FragmentActivity &&
                    BiometricAuth.canAuthenticate(context),
                onClick = {
                    if (context is androidx.fragment.app.FragmentActivity) {
                        BiometricAuth.prompt(context, "Unlock Secure Vault") { success ->
                            if (success) unlocked = true
                        }
                    }
                }
            ) { Text("Unlock Vault") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (context is androidx.fragment.app.FragmentActivity) {
                        BiometricAuth.prompt(context, "Authorize Vault Import") { success ->
                            if (success) onImport()
                        }
                    }
                }) { Text("Import") }
                OutlinedButton(onClick = { files = repository.listEncrypted() }) { Text("Refresh") }
                OutlinedButton(onClick = {
                    selected = null
                    unlocked = false
                }) { Text("Lock") }
            }

            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Vault is empty")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(files, key = { it.name }) { file ->
                        Card(Modifier.fillMaxWidth().clickable { selected = file }) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔐", style = MaterialTheme.typography.headlineSmall)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text("Encrypted item", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        file.length().toString() + " bytes • " + file.name.take(8) + "…",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { file ->
        VaultItemViewer(
            repository = repository,
            keyManager = keyManager,
            file = file,
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun VaultItemViewer(
    repository: VaultRepository,
    keyManager: KeyManager,
    file: File,
    onDismiss: () -> Unit
) {
    var data by remember(file) { mutableStateOf<ByteArray?>(null) }
    var type by remember(file) { mutableStateOf(VaultType.UNKNOWN) }
    var error by remember(file) { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        runCatching {
            withContext(Dispatchers.IO) {
                repository.decryptToMemory(file, keyManager.getOrCreateVaultKey())
            }
        }.onSuccess {
            data = it
            type = detectType(it)
        }.onFailure {
            error = "Unable to decrypt vault item"
        }
    }

    DisposableEffect(file) {
        onDispose { data?.fill(0) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                when {
                    error != null -> Text(
                        error ?: "Unable to decrypt vault item",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    data == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    type == VaultType.IMAGE -> VaultImageViewer(data!!)
                    type == VaultType.VIDEO -> VaultVideoViewer(data!!)
                    else -> Text(
                        "Unsupported or unknown media format",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) { Text("Close", color = Color.White) }
            }
        }
    }
}

@Composable
private fun VaultImageViewer(bytes: ByteArray) {
    var bitmap by remember(bytes) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(bytes) {
        bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Vault image",
                modifier = Modifier.fillMaxSize()
            )
        } ?: CircularProgressIndicator()
    }
}

@Composable
private fun VaultVideoViewer(bytes: ByteArray) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(bytes) {
        val factory = object : DataSource.Factory {
            override fun createDataSource(): DataSource = ByteArrayDataSource(bytes)
        }
        val mediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(PlayerMediaItem.fromUri("memory://ms-gallery-vault"))
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                this.player = player
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun detectType(bytes: ByteArray): VaultType {
    if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        )
    ) return VaultType.IMAGE

    if (bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
    ) return VaultType.IMAGE

    if (bytes.size >= 6) {
        val gif = String(bytes, 0, 6, Charsets.US_ASCII)
        if (gif == "GIF89a" || gif == "GIF87a") return VaultType.IMAGE
    }

    if (bytes.size >= 12 &&
        String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp"
    ) return VaultType.VIDEO

    if (bytes.size >= 4 &&
        bytes[0] == 0x1A.toByte() &&
        bytes[1] == 0x45.toByte() &&
        bytes[2] == 0xDF.toByte() &&
        bytes[3] == 0xA3.toByte()
    ) return VaultType.VIDEO

    return VaultType.UNKNOWN
}