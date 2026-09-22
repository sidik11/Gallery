package com.sidik.msgallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidik.msgallery.media.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable fun MediaDetailsScreen(item: MediaItem, resolver: android.content.ContentResolver, onBack: () -> Unit, onChanged: () -> Unit) {
 var details by remember { mutableStateOf<MediaDetails?>(null) }; var removing by remember { mutableStateOf(false) }; val scope=rememberCoroutineScope()
 LaunchedEffect(item.uri) { details=MediaDetailsReader(resolver).read(item) }
 Scaffold(topBar={ Row(Modifier.fillMaxWidth().padding(8.dp)){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}; Text("Media details",style=MaterialTheme.typography.titleLarge)} }) { p ->
  details?.let { d -> LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   item{Text(d.name,style=MaterialTheme.typography.titleLarge)}; item{Text("Type: "+d.mimeType)}; item{Text("Size: "+formatDetailsBytes(d.sizeBytes))}; item{Text("Resolution: "+d.width+" × "+d.height)}; if(d.durationMs>0)item{Text("Duration: "+d.durationMs/1000+"s")}; item{Text("Folder: "+d.folder)}; item{Text("Date: "+SimpleDateFormat("dd MMM yyyy, HH:mm",Locale.getDefault()).format(Date(d.dateTaken)))}
   d.cameraMake?.let { item{Text("Camera make: "+it)} }; d.cameraModel?.let { item{Text("Camera model: "+it)} }; d.latitude?.let { lat->d.longitude?.let { lon->item{Text("GPS: "+lat+", "+lon)} } }
   item{Button(enabled=!removing && item.type==MediaType.IMAGE,onClick={scope.launch{removing=true;val ok=MediaOperations(resolver).removeExif(item.uri);removing=false;if(ok){onChanged();onBack()}}}){Text(if(removing)"Removing metadata…" else "Remove GPS & camera metadata")}}
  }}
 }
}
private fun formatDetailsBytes(v:Long):String{if(v<1024)return "$v B";var n=v.toDouble();val u=arrayOf("KB","MB","GB","TB");var i=0;while(n>=1024&&i<u.lastIndex){n/=1024;i++};return "%.1f %s".format(n,u[i])}