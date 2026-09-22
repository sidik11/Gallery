package com.sidik.msgallery

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.rememberSaveable
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

private enum class Screen { GALLERY, SETTINGS, VAULT, TRASH, STORAGE, DETAILS, LOCK }
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

    private val trashLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == RESULT_OK) loadGallery()
        }

    private val vaultFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) encryptSelectedFile(uri)
        }

    private var items by mutableStateOf<List<MediaItem>>(emptyList())
    private var trashItems by mutableStateOf<List<MediaItem>>(emptyList())
    private var loading by mutableStateOf(true)
    private val pinLock by lazy { PinLockManager(this) }
    private var screen by mutableStateOf(Screen.GALLERY)
    private var detailItem by mutableStateOf<MediaItem?>(null)

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
                            onDelete = { requestDelete(it) },
                            onFavorite = { toggleFavorite(it) },
                            onTrash = { requestTrash(it) },
                            onRename = { item, name -> renameMedia(item, name) },
                            onCopy = { copyMedia(it) },
                            onCopyMany = { copyMediaMany(it) },
                            onShare = { shareMedia(it) },
                            onTrashScreen = { loadTrash(); screen = Screen.TRASH },
                            onDetails = { detailItem = it; screen = Screen.DETAILS }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            context = this,
                            onBack = { screen = Screen.GALLERY },
                            onVault = { screen = Screen.VAULT },
                            onStorage = { screen = Screen.STORAGE },
                            pinLock = pinLock
                        )
                        Screen.TRASH -> TrashScreen(trashItems, { screen = Screen.GALLERY }, { restoreFromTrash(it) }, { requestDelete(it) })
                        Screen.STORAGE -> StorageAnalyzerScreen(items, contentResolver) { screen = Screen.SETTINGS }
                        Screen.DETAILS -> detailItem?.let { MediaDetailsScreen(it, contentResolver, { screen = Screen.GALLERY }, { loadGallery() }) }
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

    private fun loadTrash() {
        lifecycleScope.launch { trashItems = runCatching { MediaRepository(contentResolver).loadTrashed() }.getOrDefault(emptyList()) }
    }

    private fun restoreFromTrash(selected: List<MediaItem>) {
        if (selected.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val request = MediaStore.createTrashRequest(contentResolver, selected.map { it.uri }, false)
        trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    private fun renameMedia(item: MediaItem, newName: String) {
        lifecycleScope.launch {
            val ok = MediaOperations(contentResolver).rename(item.uri, newName)
            Toast.makeText(this@MainActivity, if (ok) "Renamed" else "Rename failed", Toast.LENGTH_SHORT).show()
            loadGallery()
        }
    }

    private fun copyMediaMany(selected: List<MediaItem>) {
        if (selected.isEmpty()) return
        lifecycleScope.launch {
            val operations = MediaOperations(contentResolver)
            val count = withContext(Dispatchers.IO) {
                selected.count { runCatching { operations.copyToPictures(it.uri, it.name, it.mimeType) }.getOrNull() != null }
            }
            Toast.makeText(this@MainActivity, "Copied $count of ${selected.size}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareMedia(selected: List<MediaItem>) {
        if (selected.isEmpty()) return
        val uris = ArrayList<android.net.Uri>(selected.size)
        selected.forEach { uris.add(it.uri) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (selected.all { it.type == MediaType.IMAGE }) "image/*" else if (selected.all { it.type == MediaType.VIDEO }) "video/*" else "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share media"))
    }

    private fun copyMedia(item: MediaItem) {
        lifecycleScope.launch {
            val ok = runCatching { MediaOperations(contentResolver).copyToPictures(item.uri, item.name, item.mimeType) }.getOrNull() != null
            Toast.makeText(this@MainActivity, if (ok) "Copied to MS Gallery" else "Copy failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadGallery() {
        lifecycleScope.launch {
            loading = true
            items = runCatching { MediaRepository(contentResolver).loadAll() }.getOrDefault(emptyList())
            loading = false
        }
    }

    private fun toggleFavorite(selected: List<MediaItem>) {
        lifecycleScope.launch {
            val operations = MediaOperations(contentResolver)
            withContext(Dispatchers.IO) {
                selected.forEach { operations.setFavorite(it.uri, !it.isFavorite) }
            }
            loadGallery()
            Toast.makeText(this@MainActivity, "Favorites updated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestTrash(selected: List<MediaItem>) {
        if (selected.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createTrashRequest(contentResolver, selected.map { it.uri }, true)
            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                selected.forEach { contentResolver.delete(it.uri, null, null) }
                withContext(Dispatchers.Main) { loadGallery() }
            }
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
    onDelete: (List<MediaItem>) -> Unit,
    onFavorite: (List<MediaItem>) -> Unit,
    onTrash: (List<MediaItem>) -> Unit,
    onRename: (MediaItem, String) -> Unit,
    onCopy: (MediaItem) -> Unit,
    onCopyMany: (List<MediaItem>) -> Unit,
    onShare: (List<MediaItem>) -> Unit,
    onTrashScreen: () -> Unit,
    onDetails: (MediaItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var videosOnly by rememberSaveable { mutableStateOf(false) }
    var selectedViewer by remember { mutableStateOf<MediaItem?>(null) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(GalleryMode.PHOTOS) }
    var sortMode by rememberSaveable { mutableStateOf("newest") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var showAdvancedFilter by rememberSaveable { mutableStateOf(false) }
    var mediaFilter by remember { mutableStateOf(MediaFilter()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var renameItem by remember { mutableStateOf<MediaItem?>(null) }

    val effectiveFilter = remember(mediaFilter, videosOnly) {
        if (videosOnly) mediaFilter.copy(type = MediaType.VIDEO) else mediaFilter
    }
    val filtered = remember(items, query, effectiveFilter, favoritesOnly, sortMode) {
        val base = MediaSearch().filter(items, query, effectiveFilter).filter { !favoritesOnly || it.isFavorite }
        when (sortMode) {
            "oldest" -> base.sortedBy { it.dateTaken }
            "largest" -> base.sortedByDescending { it.sizeBytes }
            "smallest" -> base.sortedBy { it.sizeBytes }
            "name" -> base.sortedBy { it.name.lowercase(Locale.getDefault()) }
            else -> base.sortedByDescending { it.dateTaken }
        }
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
                    TextButton(onClick = { selectedIds = if (selectedIds.size == filtered.size) emptySet() else filtered.map { it.id }.toSet() }) { Text(if (selectedIds.size == filtered.size) "Clear all" else "Select all") }
                    IconButton(onClick = { if (selectedItems.size == 1) onDetails(selectedItems.first()) }) { Icon(Icons.Default.Info, "Details") }
                    IconButton(onClick = { if (selectedItems.size == 1) renameItem = selectedItems.first() }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton(onClick = { if (selectedItems.size == 1) onCopy(selectedItems.first()) else onCopyMany(selectedItems); selectedIds = emptySet() }) { Icon(Icons.Default.ContentCopy, "Copy") }
                    IconButton(onClick = { onShare(selectedItems) }) { Icon(Icons.Default.Share, "Share") }
                    IconButton(onClick = { onFavorite(selectedItems); selectedIds = emptySet() }) {
                        Icon(Icons.Default.Favorite, "Toggle favorites")
                    }
                    IconButton(onClick = { onTrash(selectedItems); selectedIds = emptySet() }) {
                        Icon(Icons.Default.Delete, "Move to trash")
                    }
                }
            } else if (showSearch) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        placeholder = { Text("Search photos and videos") }
                    )
                    IconButton(onClick = { showAdvancedFilter = true }) { Icon(Icons.Default.Tune, "Advanced filters") }
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
                    IconButton(onClick = { showAdvancedFilter = true }) { Icon(Icons.Default.Tune, "Advanced filters") }
                    IconButton(onClick = onTrashScreen) { Icon(Icons.Default.DeleteSweep, "Recently deleted") }
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
                    FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text("Favorites") })
                    FilterChip(sortMode == "newest", { sortMode = "newest" }, label = { Text("Newest") })
                FilterChip(sortMode == "oldest", { sortMode = "oldest" }, label = { Text("Oldest") })
                FilterChip(sortMode == "largest", { sortMode = "largest" }, label = { Text("Largest") })
                FilterChip(sortMode == "smallest", { sortMode = "smallest" }, label = { Text("Smallest") })
                FilterChip(sortMode == "name", { sortMode = "name" }, label = { Text("Name") })
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
                TimelineGrid(
                    items = filtered,
                    selectedIds = selectedIds,
                    onOpen = { selectedViewer = it },
                    onSelect = { item ->
                        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                    }
                )

            }
        }
    }

    renameItem?.let { item ->
        var name by remember(item) { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { renameItem = null },
            title = { Text("Rename media") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { onRename(item, name); renameItem = null; selectedIds = emptySet() }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameItem = null }) { Text("Cancel") } }
        )
    }

    if (showAdvancedFilter) {
        AdvancedFilterDialog(
            current = mediaFilter,
            folders = items.map { it.folderName }.filter { it.isNotBlank() }.distinct().sorted(),
            onApply = {
                mediaFilter = it
                videosOnly = false
                showAdvancedFilter = false
            },
            onDismiss = { showAdvancedFilter = false }
        )
    }

    selectedViewer?.let { item ->
        val viewerIndex = filtered.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        Dialog(onDismissRequest = { selectedViewer = null }) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                MediaPager(context, filtered, viewerIndex) { selectedViewer = null }
            }
        }
    }
}

@Composable
private fun TrashScreen(
    items: List<MediaItem>,
    onBack: () -> Unit,
    onRestore: (List<MediaItem>) -> Unit,
    onDelete: (List<MediaItem>) -> Unit
) {
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    Scaffold(topBar = {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Recently deleted", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                TextButton(onClick = { onRestore(items.filter { it.id in selected }); selected = emptySet() }) { Text("Restore") }
                TextButton(onClick = { onDelete(items.filter { it.id in selected }); selected = emptySet() }) { Text("Delete") }
            }
        }
    }) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Trash is empty") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    MediaTile(
                        item = item,
                        selected = item.id in selected,
                        onClick = { selected = if (item.id in selected) selected - item.id else selected + item.id },
                        onLongClick = { selected = selected + item.id }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineGrid(
    items: List<MediaItem>,
    selectedIds: Set<Long>,
    onOpen: (MediaItem) -> Unit,
    onSelect: (MediaItem) -> Unit
) {
    val groups = remember(items) { items.groupBy { dayKey(it.dateTaken) }.toList() }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        groups.forEach { (day, media) ->
            item(key = "header_$day") {
                Text(
                    day,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp)
                )
            }
            item(key = "grid_$day") {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(128.dp),
                    modifier = Modifier.fillMaxWidth().height(
                        (((media.size + 2) / 3) * 144).coerceAtLeast(144).dp
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(media, key = { it.id }) { item ->
                        MediaTile(
                            item = item,
                            selected = item.id in selectedIds,
                            onClick = { if (selectedIds.isNotEmpty()) onSelect(item) else onOpen(item) },
                            onLongClick = { onSelect(item) }
                        )
                    }
                }
            }
        }
    }
}

private fun dayKey(time: Long): String =
    SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(time))

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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(
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
