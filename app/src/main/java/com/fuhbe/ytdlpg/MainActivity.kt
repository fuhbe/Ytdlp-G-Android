package com.fuhbe.ytdlpg

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 40, 68)
        window.navigationBarColor = android.graphics.Color.rgb(16, 40, 68)
        window.decorView.systemUiVisibility = 0
        val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        setContent { App(shared) }
    }
}

private val ink = Color(0xFFF3F8FF)
private val aqua = Color(0xFF1685FA)

@Composable
private fun App(shared: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(shared) }
    var inspection by remember { mutableStateOf<Inspection?>(null) }
    var selected by remember { mutableStateOf<Quality?>(null) }
    var kind by remember { mutableStateOf("video") }
    var folder by remember { mutableStateOf<android.net.Uri?>(null) }
    var zip by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(context.getString(R.string.ready)) }
    var menu by remember { mutableStateOf(false) }
    val status by DownloadState.status.collectAsState()
    val progress by DownloadState.progress.collectAsState()
    val running by DownloadState.running.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { chosen ->
        if (chosen != null) {
            context.contentResolver.takePersistableUriPermission(chosen, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            folder = chosen
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = aqua, onPrimary = Color.White, onSurface = ink, surface = Color(0xFF24466C), background = Color(0xFF102844))) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3D6C9C), Color(0xFF244C77), Color(0xFF0F2948))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("Ytdlp-G", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ink)
                Text(stringResource(R.string.headline), style = MaterialTheme.typography.headlineMedium, color = ink)
                GlassCard {
                    OutlinedTextField(url, { url = it; inspection = null; selected = null }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.link_hint)) }, minLines = 2)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        val valid = try { URI(url).scheme in listOf("http", "https") } catch (_: Exception) { false }
                        if (!valid) { message = context.getString(R.string.invalid_link); return@Button }
                        busy = true
                        message = context.getString(R.string.analyzing)
                        scope.launch {
                            try {
                                val found = withContext(Dispatchers.IO) { MediaEngine(context).apply { initialize() }.inspect(url.trim()) }
                                inspection = found
                                selected = found.qualities.firstOrNull()
                                kind = selected?.kind ?: "video"
                                message = if (found.items.size == 1) context.getString(R.string.single_video) else context.resources.getQuantityString(R.plurals.playlist_count, found.items.size, found.items.size)
                            } catch (e: Exception) { message = e.message ?: context.getString(R.string.nothing_available) }
                            finally { busy = false }
                        }
                    }, enabled = !busy && !running) { Text(stringResource(R.string.analyze)) }
                    Text(message, color = ink, modifier = Modifier.padding(top = 8.dp))
                }
                inspection?.let { found ->
                    GlassCard {
                        Text(found.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (found.items.size == 1) stringResource(R.string.single_video) else pluralStringResource(R.plurals.playlist_count, found.items.size, found.items.size))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.content), fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("video" to R.string.video_audio, "audio" to R.string.audio_only).forEach { (option, label) ->
                                if (found.qualities.any { it.kind == option }) TextButton(onClick = {
                                    kind = option
                                    selected = found.qualities.firstOrNull { it.kind == option }
                                }) { Text(stringResource(label), color = if (kind == option) aqua else ink, fontWeight = if (kind == option) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                        Text(stringResource(R.string.quality), fontWeight = FontWeight.SemiBold)
                        Box {
                            TextButton(onClick = { menu = true }) { Text(selected?.label ?: stringResource(R.string.nothing_available), color = ink) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(Color(0xFF24466C))) {
                                found.qualities.filter { it.kind == kind }.forEach { quality ->
                                    DropdownMenuItem(text = { Text(quality.label, color = ink) }, onClick = { selected = quality; menu = false })
                                }
                            }
                        }
                        Text(stringResource(R.string.all_qualities), style = MaterialTheme.typography.bodySmall)
                    }
                    GlassCard {
                        Text(stringResource(R.string.save_to), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { picker.launch(null) }) { Text(folder?.lastPathSegment ?: stringResource(R.string.choose_folder), color = ink) }
                        Text(stringResource(R.string.output), fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { zip = false }) { Text(stringResource(R.string.normal_files), color = if (zip) ink else aqua, fontWeight = if (zip) FontWeight.Normal else FontWeight.Bold) }
                            TextButton(onClick = { zip = true }) { Text(stringResource(R.string.zip_file), color = if (zip) aqua else ink, fontWeight = if (zip) FontWeight.Bold else FontWeight.Normal) }
                        }
                        Button(onClick = {
                            val destination = folder
                            val quality = selected
                            if (destination == null) { message = context.getString(R.string.folder_required); return@Button }
                            if (quality == null) return@Button
                            DownloadState.task = DownloadState.Task(found, quality, destination, zip)
                            DownloadState.progress.value = 0f
                            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
                        }, enabled = !running && selected != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.download)) }
                    }
                }
                if (running || status.isNotEmpty()) GlassCard {
                    Text(status, color = ink)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    if (running) TextButton(onClick = { context.startService(Intent(context, DownloadService::class.java).setAction("cancel")) }) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF6E9CCB).copy(alpha = 0.37f)), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)), elevation = CardDefaults.cardElevation(defaultElevation = 5.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = { content() })
    }
}
