package com.sidik.msgallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidik.msgallery.media.MediaFilter
import com.sidik.msgallery.media.MediaType
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AdvancedFilterDialog(
    current: MediaFilter,
    folders: List<String>,
    onApply: (MediaFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(current.type) }
    var minSize by remember { mutableStateOf(current.minSizeBytes?.let(::bytesToMb) ?: "") }
    var maxSize by remember { mutableStateOf(current.maxSizeBytes?.let(::bytesToMb) ?: "") }
    var minWidth by remember { mutableStateOf(current.minWidth?.toString() ?: "") }
    var minHeight by remember { mutableStateOf(current.minHeight?.toString() ?: "") }
    var minDuration by remember { mutableStateOf(current.minDurationMs?.div(1000)?.toString() ?: "") }
    var maxDuration by remember { mutableStateOf(current.maxDurationMs?.div(1000)?.toString() ?: "") }
    var afterDate by remember { mutableStateOf(current.afterDateMs?.let(::formatDate) ?: "") }
    var beforeDate by remember { mutableStateOf(current.beforeDateMs?.let(::formatDate) ?: "") }
    var folder by remember { mutableStateOf(current.folder ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Advanced filters") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Media type")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(type == null, { type = null }, label = { Text("All") })
                    FilterChip(type == MediaType.IMAGE, { type = MediaType.IMAGE }, label = { Text("Photos") })
                    FilterChip(type == MediaType.VIDEO, { type = MediaType.VIDEO }, label = { Text("Videos") })
                }
                OutlinedTextField(minSize, { minSize = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Minimum size (MB)") }, singleLine = true)
                OutlinedTextField(maxSize, { maxSize = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Maximum size (MB)") }, singleLine = true)
                OutlinedTextField(minWidth, { minWidth = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Minimum width (px)") }, singleLine = true)
                OutlinedTextField(minHeight, { minHeight = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Minimum height (px)") }, singleLine = true)
                OutlinedTextField(minDuration, { minDuration = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Minimum video duration (seconds)") }, singleLine = true)
                OutlinedTextField(maxDuration, { maxDuration = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Maximum video duration (seconds)") }, singleLine = true)
                OutlinedTextField(afterDate, { afterDate = it.take(10) }, Modifier.fillMaxWidth(), label = { Text("From date (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(beforeDate, { beforeDate = it.take(10) }, Modifier.fillMaxWidth(), label = { Text("To date (YYYY-MM-DD)") }, singleLine = true)
                if (folders.isNotEmpty()) {
                    Text("Folder")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(folder.isBlank(), { folder = "" }, label = { Text("All") })
                        folders.take(8).forEach { name ->
                            FilterChip(folder.equals(name, true), { folder = name }, label = { Text(name) })
                        }
                    }
                }
                Text("Dates use your device's local timezone.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(
                    MediaFilter(
                        type = type,
                        minSizeBytes = mb(minSize),
                        maxSizeBytes = mb(maxSize),
                        minWidth = minWidth.toIntOrNull(),
                        minHeight = minHeight.toIntOrNull(),
                        minDurationMs = sec(minDuration),
                        maxDurationMs = sec(maxDuration),
                        afterDateMs = parseDate(afterDate, false),
                        beforeDateMs = parseDate(beforeDate, true),
                        folder = folder.takeIf { it.isNotBlank() }
                    )
                )
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun mb(value: String): Long? = value.toLongOrNull()?.takeIf { it >= 0 }?.times(1024L * 1024L)
private fun sec(value: String): Long? = value.toLongOrNull()?.takeIf { it >= 0 }?.times(1000L)
private fun bytesToMb(value: Long): String = (value / (1024L * 1024L)).toString()

private fun parseDate(value: String, endOfDay: Boolean): Long? = runCatching {
    if (value.length != 10) return null
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value) ?: return null
    val cal = java.util.Calendar.getInstance().apply { time = date }
    if (endOfDay) {
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
    }
    cal.timeInMillis
}.getOrNull()

private fun formatDate(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(value))
