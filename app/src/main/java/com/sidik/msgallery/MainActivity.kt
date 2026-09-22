package com.sidik.msgallery

import android.Manifest
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sidik.msgallery.media.*
import com.sidik.msgallery.security.*
import com.sidik.msgallery.ui.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen { GALLERY, SETTINGS, VAULT, LOCK }
private enum class GalleryMode { PHOTOS, ALBUMS }

class MainActivity : FragmentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { loadGallery() }

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == RESULT_OK) {
                loadGallery()
                Toast.makeText(this, "Selected media deleted", Toast.LENGTH_SHORT).show()
            }
        }

    private val vaultFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) encryptSelectedFile(uri)
        }

    private var items by mutableStateOf<List<MediaItem>>(emptyList())
    private var loading by mutableStateOf(true)
    private val pinLock by lazy { PinLockManager(this) }
    private var screen by mutableStateOf(Screen.GALLERY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureWindow.protect(window)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestMediaAccess()
        if (pinLock.isEnabled()) screen = Screen.LOCK
        setContent {
            GalleryTheme {
                Surface(Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.LOCK -> LockScreen(pinLock) { screen = Screen.GALLERY }
                        Screen.GALLERY -> GalleryScreen(
                            items = items,
                            loading = loading,
                            onSettings = { screen = Screen.SETTINGS },
                            onDelete = { requestDelete(it) }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            context = this,
                            onBack = { screen = Screen.GALLERY },
                            onVault = { screen = Screen.VAULT },
                            pinLock = pinLock
                        )
                        Screen.VAULT -> VaultScreen(
                            context = this,
                            onBack = { screen = Screen.SETTINGS },
                            onImport = { vaultFileLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pinLock.isEnabled() && screen != Screen.LOCK) screen = Screen.LOCK
        if (!loading && screen == Screen.GALLERY) loadGallery()
    }

    private fun requestMediaAccess() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionLauncher.launch(permissions)
    }

    private fun loadGallery() {
        lifecycleScope.launch {
            loading = true
            items = runCatching { MediaRepository(contentResolver).loadAll() }.getOrDefault(emptyList())
            loading = false
        }
    }

    private fun requestDelete(selected: List<MediaItem>) {
        if (selected.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createDeleteRequest(contentResolver, selected.map { it.uri })
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                selected.forEach { contentResolver.delete(it.uri, null, null) }
                withContext(Dispatchers.Main) {
                    loadGallery()
                    Toast.makeText(this@MainActivity, "Selected media deleted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun encryptSelectedFile(uri: android.net.Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        VaultRepository(this@MainActivity).importEncrypted(
                            input, "media", KeyManager().getOrCreateVaultKey()
                        )
                    } ?: error("Unable to read selected file")
                }
            }
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) "Encrypted file added to vault" else "Vault import failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Composable
private fun GalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryScreen(
    items: List<MediaItem>,
    loading: Boolean,
    onSettings: () -> Unit,
    onDelete: (List<MediaItem>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var videosOnly by rememberSaveable { mutableStateOf(false) }
    var selectedViewer by remember { mutableStateOf<MediaItem?>(null) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(GalleryMode.PHOTOS) }
    var sortNewest by rememberSaveable { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val filtered = remember(items, query, videosOnly, sortNewest) {
        val base = MediaSearch().filter(items, query, videosOnly)
        if (sortNewest) base.sortedByDescending { it.dateTaken } else base.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }
    val selectedItems = remember(selectedIds, items) { items.filter { it.id in selectedIds } }

    Scaffold(
        topBar = {
            if (selectedIds.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Default.Close, "Cancel selection") }
                    Text("${selectedIds.size} selected", modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDelete(selectedItems); selectedIds = emptySet() }) {
                        Icon(Icons.Default.Delete, "Delete selected")
                    }
                }
            } else if (showSearch) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        placeholder = { Text("Search photos and videos") }
                    )
                    IconButton(onClick = { showSearch = false; query = "" }) {
                        Icon(Icons.Default.Close, "Close search")
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("MS Gallery", style = MaterialTheme.typography.headlineSmall)
                        Text(if (loading) "Scanning device…" else "${filtered.size} items")
                    }
                    IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!loading) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(mode == GalleryMode.PHOTOS, { mode = GalleryMode.PHOTOS }, label = { Text("Photos") })
                    FilterChip(mode == GalleryMode.ALBUMS, { mode = GalleryMode.ALBUMS }, label = { Text("Albums") })
                    FilterChip(videosOnly, { videosOnly = !videosOnly }, label = { Text("Videos") })
                    FilterChip(sortNewest, { sortNewest = !sortNewest }, label = { Text(if (sortNewest) "Newest" else "Name") })
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (mode == GalleryMode.ALBUMS) {
                AlbumGrid(items = filtered, onOpen = { folder ->
                    query = folder
                    mode = GalleryMode.PHOTOS
                })
            } else if (filtered.isEmpty()) {
                EmptyGallery(Modifier.fillMaxSize())
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(128.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        MediaTile(
                            item = item,
                            selected = item.id in selectedIds,
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                                } else selectedViewer = item
                            },
                            onLongClick = { selectedIds = selectedIds + item.id }
                        )
                    }
                }
            }
        }
    }

    selectedViewer?.let { item ->
        Dialog(onDismissRequest = { selectedViewer = null }) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.fillMaxSize()) {
                    if (item.type == MediaType.IMAGE) ImageViewer(context, item.uri)
                    else VideoViewer(context, item.uri)
                    IconButton(
                        onClick = { selectedViewer = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    ) { Icon(Icons.Default.Close, "Close viewer") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGrid(items: List<MediaItem>, onOpen: (String) -> Unit) {
    val groups = remember(items) { items.groupBy { it.folderName }.toList().sortedBy { it.first } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(groups, key = { it.first }) { (folder, media) ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(folder) }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Folder, null, Modifier.size(42.dp))
                    Text(folder, style = MaterialTheme.typography.titleMedium)
                    Text("${media.size} items", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    item: MediaItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var bitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(item.uri) {
        bitmap = ThumbnailEngine(context.contentResolver).load(item.uri, 512, 512)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = item.name, modifier = Modifier.fillMaxSize())
            } ?: Icon(
                if (item.type == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                item.name, Modifier.size(48.dp)
            )
            if (selected) {
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    shape = RoundedCornerShape(50),
                    tonalElevation = 6.dp
                ) {
                    Icon(Icons.Default.CheckCircle, "Selected", Modifier.padding(4.dp).size(24.dp))
                }
            }
            if (item.type == MediaType.VIDEO) {
                Surface(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    tonalElevation = 4.dp
                ) { Icon(Icons.Default.Videocam, null, Modifier.padding(5.dp).size(18.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Folder, null, Modifier.size(72.dp))
            Text("No photos or videos found", style = MaterialTheme.typography.titleMedium)
            Text("Allow media access to scan this device.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
