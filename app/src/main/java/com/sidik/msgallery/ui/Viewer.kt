package com.sidik.msgallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sidik.msgallery.media.ThumbnailEngine

@Composable
fun MediaPager(
    context: Context,
    items: List<com.sidik.msgallery.media.MediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val item = items[page]
            if (item.type == com.sidik.msgallery.media.MediaType.IMAGE) ImageViewer(context, item.uri)
            else VideoViewer(context, item.uri)
        }
        Surface(
            Modifier.align(Alignment.TopCenter).padding(12.dp),
            color = Color.Black.copy(alpha = 0.65f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("$"+"{pagerState.currentPage + 1} / $"+"{items.size}", color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
            Text("×", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun ImageViewer(context: Context, uri: Uri) {
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offsetX by remember(uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(uri) { mutableFloatStateOf(0f) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        bitmap = ThumbnailEngine(context.contentResolver).load(uri, 4096, 4096)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else scale = 2.5f
                            }
                        )
                    }
            )
        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))

        Row(
            Modifier.align(Alignment.BottomCenter).padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalIconButton(onClick = {
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = context.contentResolver.getType(uri) ?: "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share media")
                )
            }) { Icon(Icons.Default.Share, "Share") }

            FilledTonalIconButton(onClick = { showInfo = true }) {
                Icon(Icons.Default.Info, "Media information")
            }
        }

        if (showInfo) {
            MediaInfoDialog(context, uri, onDismiss = { showInfo = false })
        }
    }
}

@Composable
fun VideoViewer(context: Context, uri: Uri) {
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
        FilledTonalIconButton(
            onClick = {
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = context.contentResolver.getType(uri) ?: "video/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share video")
                )
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
        ) { Icon(Icons.Default.Share, "Share video") }
    }
}

@Composable
private fun MediaInfoDialog(context: Context, uri: Uri, onDismiss: () -> Unit) {
    var name by remember(uri) { mutableStateOf("Loading…") }
    var size by remember(uri) { mutableStateOf("") }
    LaunchedEffect(uri) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: "Unknown"
                    if (si >= 0 && !c.isNull(si)) size = formatBytes(c.getLong(si))
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media information") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Name: $name")
            if (size.isNotEmpty()) Text("Size: $size")
            Text("URI: $uri")
        }},
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = -1
    do { value /= 1024.0; i++ } while (value >= 1024 && i < units.lastIndex)
    return String.format(java.util.Locale.US, "%.1f %s", value, units[i])
}
