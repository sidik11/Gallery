package com.sidik.msgallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidik.msgallery.media.*
import kotlinx.coroutines.launch

@Composable
fun StorageAnalyzerScreen(items: List<MediaItem>, resolver: android.content.ContentResolver, onBack: () -> Unit) {
    var summary by remember { mutableStateOf<StorageSummary?>(null) }
    var duplicates by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(items) { summary = StorageAnalyzer().summarize(items) }
    Scaffold(topBar = { Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        Text("Storage analyzer", style = MaterialTheme.typography.titleLarge)
    }}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { summary?.let { s -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.Storage, null)
                Text("Device storage", style = MaterialTheme.typography.titleMedium)
                Text("Used: ${formatBytes((s.deviceTotalBytes - s.deviceFreeBytes).coerceAtLeast(0))} / ${formatBytes(s.deviceTotalBytes)}")
                Text("Free: ${formatBytes(s.deviceFreeBytes)}")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { if (s.deviceTotalBytes == 0L) 0f else (s.deviceTotalBytes - s.deviceFreeBytes).toFloat() / s.deviceTotalBytes })
                HorizontalDivider()
                Text("Gallery media: ${formatBytes(s.totalBytes)}")
                Text("Photos: ${s.imageCount}")
                Text("Videos: ${s.videoCount}")
                Text("Largest: ${s.largest?.name ?: "None"}${if (s.largest != null) " • " + formatBytes(s.largest.sizeBytes) else ""}")
            }}} }
            item { Button(enabled = !scanning && items.isNotEmpty(), onClick = { scope.launch { scanning = true; duplicates = StorageAnalyzer(resolver).exactDuplicates(items); scanning = false } }, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "Scanning…" else "Find exact duplicates") } }
            items(duplicates.size) { i -> val g = duplicates[i]; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("${g.items.size} identical files", style = MaterialTheme.typography.titleMedium); Text("Wasted space: ${formatBytes(g.wastedBytes)}"); g.items.forEach { Text("• ${it.name} — ${formatBytes(it.sizeBytes)}") } } } }
        }
    }
}

private fun formatBytes(v: Long): String { if (v < 1024) return "$v B"; var n=v.toDouble(); val u=arrayOf("KB","MB","GB","TB"); var i=0; while(n>=1024 && i<u.lastIndex){n/=1024;i++}; return "%.1f %s".format(n,u[i]) }