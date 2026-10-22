package com.fuhbe.ytdlpg

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(27, 57, 88)
        window.navigationBarColor = android.graphics.Color.rgb(27, 57, 88)
        val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        setContent { App(shared) }
    }
}

private val white = Color(0xFFF4F8FF)
private val muted = Color(0xFFD3E3F5)
private val blue = Color(0xFF0878F9)
private val border = Color.White.copy(alpha = 0.36f)
private val panel = Color(0xFF315A83).copy(alpha = 0.86f)

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
    val status by DownloadState.status.collectAsState()
    val progress by DownloadState.progress.collectAsState()
    val running by DownloadState.running.collectAsState()
    val errors by DownloadState.errors.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { chosen ->
        if (chosen != null) {
            context.contentResolver.takePersistableUriPermission(
                chosen, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            folder = chosen
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = blue, onPrimary = Color.White, onSurface = white,
            surface = Color(0xFF315A83), background = Color(0xFF1B3958)
        )
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0xFF6289B0), Color(0xFF315D87), Color(0xFF183B60))
        ))) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.foundation.Image(
                        painterResource(R.drawable.ic_launcher), null,
                        Modifier.size(42.dp).clip(RoundedCornerShape(9.dp))
                    )
                    Text("Ytdlp-G", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                GlassCard {
                    Text(stringResource(R.string.headline), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        url, { url = it; inspection = null; selected = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.link_hint)) },
                        singleLine = true
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (pasted.isNotBlank()) { url = pasted; inspection = null; selected = null }
                        }) { Text(stringResource(R.string.paste), color = white) }
                        Button(onClick = {
                            val valid = try { URI(url.trim()).scheme in listOf("http", "https") } catch (_: Exception) { false }
                            if (!valid) { message = context.getString(R.string.invalid_link); return@Button }
                            busy = true
                            message = context.getString(R.string.analyzing)
                            scope.launch {
                                try {
                                    val found = withContext(Dispatchers.IO) {
                                        MediaEngine(context).apply { initialize() }.inspect(url.trim()) { current, total ->
                                            scope.launch { message = context.getString(R.string.inspect_progress, current, total) }
                                        }
                                    }
                                    inspection = found
                                    selected = preferredQuality(found.qualities, "video")
                                    kind = selected?.kind ?: "video"
                                    message = if (found.items.size == 1) context.getString(R.string.single_video)
                                    else context.resources.getQuantityString(R.plurals.playlist_count, found.items.size, found.items.size)
                                } catch (e: Exception) {
                                    message = e.message ?: context.getString(R.string.nothing_available)
                                } finally { busy = false }
                            }
                        }, enabled = !busy && !running) { Text(stringResource(R.string.analyze)) }
                    }
                    Text(message, color = muted)
                }

                inspection?.let { found ->
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (found.thumbnail.isNotBlank()) AsyncImage(
                                model = found.thumbnail, contentDescription = null,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(9.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column {
                                Text(found.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (found.items.size == 1) stringResource(R.string.single_video)
                                    else pluralStringResource(R.plurals.playlist_count, found.items.size, found.items.size),
                                    color = muted
                                )
                            }
                        }
                    }

                    GlassCard {
                        SegmentedChoice(
                            listOf("video" to stringResource(R.string.video_audio), "audio" to stringResource(R.string.audio_only)),
                            kind, { chosen ->
                                kind = chosen
                                selected = preferredQuality(found.qualities, chosen)
                            }
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.quality), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.available_only), color = muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        found.qualities.filter { it.kind == kind }.forEach { quality ->
                            val active = selected == quality
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) Color(0xFF246AAF) else Color(0xFF25496F))
                                    .clickable { selected = quality }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (active) "◉" else "○", color = white)
                                Text(quality.label, color = white, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }

                    GlassCard {
                        Text(stringResource(R.string.output), fontWeight = FontWeight.SemiBold)
                        SegmentedChoice(
                            listOf("files" to stringResource(R.string.normal_files), "zip" to stringResource(R.string.zip_file)),
                            if (zip) "zip" else "files", { zip = it == "zip" }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.save_to), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { picker.launch(null) }) {
                            Text(folder?.lastPathSegment ?: stringResource(R.string.choose_folder), color = white)
                        }
                        Button(
                            onClick = {
                                val destination = folder
                                val quality = selected
                                if (destination == null) { message = context.getString(R.string.folder_required); return@Button }
                                if (quality == null) return@Button
                                DownloadState.task = DownloadState.Task(found, quality, destination, zip)
                                DownloadState.progress.value = 0f
                                DownloadState.errors.value = emptyList()
                                DownloadState.status.value = ""
                                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
                            },
                            enabled = !running && selected != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.download)) }
                    }
                }

                if (running || status.isNotEmpty() || errors.isNotEmpty()) GlassCard {
                    Text(status, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = Color(0xFF52ADFF)
                    )
                    if (running) TextButton(onClick = {
                        context.startService(Intent(context, DownloadService::class.java).setAction("cancel"))
                    }) { Text(stringResource(R.string.cancel), color = white) }
                    errors.forEach { Text(it, color = Color(0xFFFFD4D4), modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
}

private fun preferredQuality(qualities: List<Quality>, kind: String): Quality? {
    val options = qualities.filter { it.kind == kind }
    return if (kind == "video") options.firstOrNull { it.key == "v:720" }
        ?: options.firstOrNull { it.key == "v:1080" }
        ?: options.lastOrNull()
    else options.firstOrNull()
}

@Composable
private fun SegmentedChoice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label) ->
            val active = selected == key
            Text(
                label,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                    .background(if (active) blue else Color(0xFF5279A0))
                    .clickable { onSelect(key) }.padding(vertical = 12.dp, horizontal = 8.dp),
                color = white,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = panel),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, border),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(15.dp), content = { content() })
    }
}
