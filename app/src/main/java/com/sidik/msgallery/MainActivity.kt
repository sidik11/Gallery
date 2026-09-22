package com.sidik.msgallery

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.sidik.msgallery.media.MediaItem
import com.sidik.msgallery.media.MediaRepository
import com.sidik.msgallery.media.MediaType
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { loadGallery() }
    private var items by mutableStateOf<List<MediaItem>>(emptyList())
    private var loading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestMediaAccess()
        setContent {
            GalleryTheme {
                Surface(Modifier.fillMaxSize()) {
                    GalleryScreen(items, loading)
                }
            }
        }
    }

    private fun requestMediaAccess() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    private fun loadGallery() {
        lifecycleScope.launch {
            loading = true
            items = MediaRepository(contentResolver).loadAll()
            loading = false
        }
    }
}

@Composable
private fun GalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryScreen(items: List<MediaItem>, loading: Boolean) {
    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MS Gallery", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (loading) "Scanning device…" else "Gallery ready",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(Icons.Default.Settings, "Settings", Modifier.size(28.dp))
            }
        }
    ) { padding ->
        when {
            loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            items.isEmpty() -> EmptyGallery(Modifier.fillMaxSize().padding(padding))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { MediaTile(it) }
            }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem) {
    Card(Modifier.fillMaxWidth().clip(RectangleShape).clickable { }) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (item.type == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                null,
                Modifier.size(56.dp)
            )
            Text(item.name, maxLines = 1, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, null, Modifier.size(72.dp))
            Text("No photos or videos found", style = MaterialTheme.typography.titleMedium)
            Text("Give MS Gallery access to your media.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
