package com.fuhbe.ytdlpg.simple

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf("")
    private var showDownloads by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        readIntent(intent)
        setContent {
            val model: AppModel = viewModel()
            LaunchedEffect(incoming, showDownloads) {
                if (incoming.isNotBlank()) { model.url = incoming; model.area = "home" }
                if (showDownloads) model.area = "downloads"
            }
            AppContent(model)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readIntent(intent) }
    private fun readIntent(value: Intent) {
        incoming = value.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        showDownloads = value.getBooleanExtra("downloads", false)
    }
}

private val Blue = Color(0xFF086DE0)
private val Shape = RoundedCornerShape(23.dp)

@Composable fun GlassTheme(dark: Boolean, content: @Composable () -> Unit) = LiquidGlassTheme(dark, content)

@Composable fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.liquidGlass(Shape).padding(12.dp).animateContentSize(tween(160)), content = content)
}

@Composable fun AppContent(model: AppModel) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (model.theme) { "dark" -> true; "light" -> false; else -> systemDark }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    var dockHeight by remember { mutableIntStateOf(0) }
    SideEffect {
        (context as? android.app.Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val jobs by Store.jobs.collectAsState()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            model.setFolder(uri)
        } catch (_: Exception) { model.error = "Cannot keep access to this folder. Choose another folder." }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val chooseFolder = { folderPicker.launch(model.folder.takeIf { it.isNotBlank() }?.let(Uri::parse)) }
    BackHandler(model.area != "home" || model.inspection?.playlist == true) {
        if (model.area != "home") model.area = "home" else model.clear()
    }
    GlassTheme(dark) {
        GlassContentLayer { capture, dockBackdrop ->
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = with(density) { dockHeight.toDp() }).then(capture)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (model.area == "home" && model.inspection?.playlist == true) {
                    IconButton(onClick = { if (model.area != "home") model.area = "home" else model.clear() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                } else {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Color(0xFF56B7FF), Blue))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(25.dp)) }
                    Spacer(Modifier.width(10.dp))
                }
                Text("Ytdlp-G", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Box(Modifier.weight(1f)) {
            if (model.area == "downloads") DownloadsScreen(jobs, model::control)
            else if (model.area == "settings") SettingsScreen(model, chooseFolder)
            else HomeScreen(model, chooseFolder) {
                focus.clearFocus()
                if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                model.enqueue()
            }
            }
        }
        GlassNavigationDock(model.area,
            onBack = { if (model.area != "home") model.area = "home" else model.clear() },
            onDownloads = { model.area = "downloads" }, onSettings = { model.area = "settings" },
            source = dockBackdrop,
            modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).fillMaxWidth().onSizeChanged { dockHeight = it.height }.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        }
    }
}

@Composable private fun Heading(text: String, secondary: String = "") {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (secondary.isNotBlank()) Text(secondary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 14.sp)
        if (secondary.isNotBlank()) Text(secondary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ActionButton(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    GlassButton(onClick=onClick,enabled=enabled,emphasized=true,
        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),contentPadding=PaddingValues(13.dp)) {
        val foreground=MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled) 1f else .65f)
        Icon(icon,null,tint=foreground,modifier=Modifier.size(24.dp));Spacer(Modifier.width(10.dp))
        Text(label,color=foreground,fontWeight=FontWeight.SemiBold,fontSize=16.sp)
    }
}
@Composable private fun Segments(labels: List<String>, icons: List<ImageVector>, selected: Int, onSelected: (Int) -> Unit) {
    GlassSelectionTrack(selected, labels.size, onSelected, sourceKey = labels) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Surface(onClick = { onSelected(index) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).glassSegmentItem(index).semantics { this.selected = active; role = Role.Tab }, shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(icons[index], null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable private fun Preview(media: Media, subtitle: String = "", large: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (large) 98.dp else 64.dp, if (large) 76.dp else 60.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Icon(Icons.Default.Landscape, null, Modifier.align(Alignment.Center).size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(media.thumb, "Miniatura: ${media.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (media.duration > 0) Text(duration(media.duration), color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .68f), RoundedCornerShape(topStart = 5.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(media.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (media.author.isNotBlank()) Text(media.author, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable private fun HomeScreen(model: AppModel, chooseFolder: () -> Unit, download: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var showAll by rememberSaveable { mutableStateOf(false) }
    val inspection = model.inspection
    val playlist = inspection?.playlist == true
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (!playlist) item {
            GlassCard {
                Heading("Video link")
                OutlinedTextField(model.url, { model.url = it; if (inspection != null) model.clear() },
                    placeholder = { Text("Paste one or more YouTube links", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(20.dp)) },
                    trailingIcon = { GlassIconButton(onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        model.clear(); model.url = clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    }) { Icon(Icons.Default.ContentPaste, "Paste link", Modifier.size(20.dp)) } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(16.dp), maxLines = 4,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); model.analyze() }))
                if (inspection == null) ActionButton(if (model.busy) "Analyzing…" else "Analyze link", Icons.Default.AutoAwesome, !model.busy && model.url.isNotBlank()) { focus.clearFocus(); model.analyze() }
                if (model.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    Text(model.status, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { model.clear() }) { Text("Cancel analysis") }
                }
            }
        }
        if (model.error.isNotBlank()) item { ErrorCard(model.error) }
        if (inspection != null) {
            item {
                GlassCard {
                    if (playlist) Text("Playlist detected", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    else Heading("Video preview")
                    val preview = inspection.items.first().let { if (playlist) it.copy(title = inspection.title) else it }
                    Preview(preview, if (playlist) "${inspection.items.size} videos • ${duration(inspection.items.sumOf { it.duration })}" else duration(preview.duration), true)
                }
            }
            if (playlist) {
                item {
                    Heading("Playlist videos", "${model.selected.size} of ${inspection.items.size} selected")
                    TextButton(onClick = {
                        model.selected = if (model.selected.size == inspection.items.count { it.available }) emptySet() else inspection.items.filter { it.available }.map { it.id }.toSet()
                        model.choice = Policy.choice(model.choice, model.selected.size)
                    }) { Text(if (model.selected.size == inspection.items.count { it.available }) "Clear selection" else "Select all") }
                }
                if (!showAll) item {
                    GlassCard {
                        inspection.items.take(4).forEachIndexed { index, media ->
                            PlaylistRow(media, model)
                            if (index < minOf(3, inspection.items.lastIndex)) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                        }
                    }
                } else items(inspection.items, key = { it.id }) { media ->
                    GlassCard { PlaylistRow(media, model) }
                }
                if (inspection.items.size > 4) item { TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show less" else "Show all ${inspection.items.size} videos") } }
            }
            item {
                GlassCard {
                    Heading("Format")
                    Segments(listOf("MP3", "MP4"), listOf(Icons.Default.MusicNote, Icons.Default.SmartDisplay), if (model.choice.mp3) 0 else 1) { model.choice = model.choice.copy(mp3 = it == 0) }
                    if (model.choice.mp3) Text("Converts the best audio source to high-quality VBR MP3. Quality depends on the source.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 9.dp))
                }
            }
            if (!model.choice.mp3) item { QualityCard(inspection, model.choice.height) { model.choice = model.choice.copy(height = it) } }
            item { DestinationCard(model.folder, chooseFolder) }
            if (Policy.zipAllowed(model.selected.size)) item {
                GlassCard {
                    Heading("Download mode")
                    Segments(listOf("Normal", "ZIP"), listOf(Icons.Default.Download, Icons.Default.FolderZip), if (model.choice.zip) 1 else 0) { model.choice = model.choice.copy(zip = it == 1) }
                    Text("ZIP combines the selected files. Temporary originals remain until completed copies are cleared.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item { ActionButton(if (model.queuing) "Starting…" else if (playlist) "Download playlist" else "Download", Icons.Default.Download, model.selected.isNotEmpty() && !model.queuing, download) }
        }
        if (inspection == null && !model.busy) item {
            Text("Videos and playlists as MP3 or MP4. Paste multiple links, one per line.\n\nDownload only content you have permission to save.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable private fun PlaylistRow(media: Media, model: AppModel) {
    Row(Modifier.fillMaxWidth().clickable(enabled = media.available) { model.select(media.id) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(media.id in model.selected, { model.select(media.id) }, enabled = media.available,
            modifier = Modifier.semantics { contentDescription = "Select ${media.title}" })
        Box(Modifier.size(62.dp, 55.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Icon(Icons.Default.Landscape, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(media.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (media.duration > 0) Text(duration(media.duration), color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .7f), RoundedCornerShape(topStart = 4.dp)).padding(horizontal = 3.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(media.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(if (media.available) media.author else "Unavailable", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun QualityCard(inspection: Inspection, selected: Int, choose: (Int) -> Unit) {
    GlassCard {
        Heading("Video quality")
        GlassQualityPicker(listOf(0) + inspection.heights, selected, choose)
        Text(if (inspection.playlist) "Resolutions found in the reference video. Items use the best available resolution up to your choice, without upscaling."
            else "Available resolutions only. Video and audio are combined; incompatible codecs are converted to MP4.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
    }
}
@Composable private fun DestinationCard(folder: String, choose: () -> Unit) {
    val context = LocalContext.current
    val label by produceState(if (folder.isBlank()) "Downloads/Ytdlp-G" else "Selected folder", folder) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Storage.folderName(context, folder) }
    }
    GlassCard {
        Heading("Destination folder")
        GlassButton(onClick = choose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), contentPadding = PaddingValues(0.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Save to", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, "Change folder")
            }
        }
    }
}
@Composable private fun ErrorCard(text: String) {
    GlassCard { Text(text, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
}

@Composable internal fun DownloadsScreen(jobs: List<QueueJob>, control: (String, String) -> Unit) {
    var completedTab by rememberSaveable { mutableStateOf(false) }
    var opened by remember { mutableStateOf<QueueJob?>(null) }
    var failures by remember { mutableStateOf<QueueJob?>(null) }
    val context = LocalContext.current
    val ongoing = jobs.filter { it.state !in setOf("DONE", "WARNING") }
    val completed = jobs.filter { it.state in setOf("DONE", "WARNING") }
    Column(Modifier.fillMaxWidth()) {
        Segments(listOf("Downloads (${ongoing.size})", "Completed"), listOf(Icons.Default.Downloading, Icons.Default.CheckCircle), if (completedTab) 1 else 0) { completedTab = it == 1 }
        LazyColumn(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            val visible = if (completedTab) completed else jobs
            if (visible.isEmpty()) item { GlassCard { Text(if (completedTab) "No completed downloads." else "Your downloads will appear here.") } }
            items(visible, key = { it.id }) { job ->
                GlassCard {
                    val finished = job.state in setOf("DONE", "WARNING")
                    val records = BatchRunner.records(job)
                    val missed = records.filter { it.state in setOf("FAILED", "SKIPPED") }
                    val activeIndex = records.firstOrNull { it.state == "RUNNING" }?.position ?: job.current.coerceAtMost(job.items.lastIndex)
                    val current = job.items.getOrNull(activeIndex) ?: job.items.first()
                    Preview(current.copy(title = if (job.items.size > 1) job.title else current.title),
                        "${if (job.choice.mp3) "MP3 • best source" else "MP4"}${if (job.items.size > 1) " • ${job.current} of ${job.items.size}" else ""}${if (job.choice.zip) " • ZIP" else ""}")
                    val progress by animateFloatAsState(job.progress, label = "Progresso real")
                    Row(Modifier.padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (job.state == "RUNNING" && job.indeterminate) LinearProgressIndicator(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)), color = Blue, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f))
                        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)), color = if (finished) Color(0xFF43E1AC) else Blue, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f))
                        Spacer(Modifier.width(10.dp))
                        if (!(job.state == "RUNNING" && job.indeterminate)) Text("${(job.progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(job.phase, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    if (job.speed > 0 && job.state == "RUNNING") Text("${bytes(job.speed)}/s" + if (job.eta > 0) " • ${duration(job.eta)} at this stage" else "",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (job.optimization.isNotBlank() && job.state == "RUNNING") Text(job.optimization, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (job.error.isNotBlank()) Text(job.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text(when (job.state) { "DONE" -> "Completed"; "WARNING" -> "Completed with warnings"; "PAUSED" -> "Paused"; "CANCELLED" -> "Cancelled"; "ERROR" -> "Error"; "QUEUED" -> "Queued"; else -> "Downloading" },
                            color = if (finished) Color(0xFF82F0C6) else MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (job.state in listOf("RUNNING", "QUEUED")) IconButton(onClick = { control(job.id, "pause") }) { Icon(Icons.Default.Pause, "Pause ${job.title}") }
                        if (job.state in listOf("PAUSED", "CANCELLED") || (job.state == "ERROR" && (records.any { it.state in setOf("PENDING", "RUNNING") || it.retryable } || job.files.isNotEmpty()))) IconButton(onClick = { control(job.id, "resume") }) { Icon(if (job.state == "PAUSED") Icons.Default.PlayArrow else Icons.Default.Refresh, "Resume ${job.title}") }
                        if (finished) IconButton(onClick = {
                            if (job.archiveUri.isNotBlank()) openFile(context, Uri.parse(job.archiveUri), "application/zip")
                            else if (job.files.size == 1) openFile(context, Storage.openUri(context, job.files.first()), if (job.choice.mp3) "audio/mpeg" else "video/mp4")
                            else opened = job
                        }) { Icon(Icons.Default.FolderOpen, "Open files for ${job.title}") }
                        else if (job.state != "CANCELLED") IconButton(onClick = { control(job.id, "cancel") }) { Icon(Icons.Default.Close, "Cancel ${job.title}") }
                    }
                    if (missed.isNotEmpty()) TextButton(onClick = { failures = job }) { Text("Review unsuccessful items (${missed.size})") }
                    if (finished) {
                        Text("${bytes(job.files.sumOf { it.bytes })}${job.files.firstOrNull()?.height?.takeIf { it > 0 }?.let { " • ${it}p" }.orEmpty()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    failures?.let { selected ->
        val job = jobs.firstOrNull { it.id == selected.id } ?: selected
        val records = BatchRunner.records(job).filter { it.state in setOf("FAILED", "SKIPPED") }
        val eligible = records.count { it.retryable }
        AlertDialog(onDismissRequest = { failures = null }, title = { Text("Unsuccessful items (${records.size})") }, text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(BatchRunner.summary(job), fontSize = 13.sp) }
                items(records, key = { it.position }) { record ->
                    Column {
                        Text("${record.position + 1}. ${job.items[record.position].title}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(record.reason, fontSize = 12.sp)
                        if (record.retryable) Text("Can be retried", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }, confirmButton = {
            if (eligible > 0 && job.state in setOf("WARNING", "ERROR")) TextButton(onClick = { control(job.id, "retry"); failures = null }) { Text("Retry ($eligible)") }
            else TextButton(onClick = { failures = null }) { Text("Close") }
        }, dismissButton = { if (eligible > 0 && job.state in setOf("WARNING", "ERROR")) TextButton(onClick = { failures = null }) { Text("Close") } })
    }
    opened?.let { job -> AlertDialog(onDismissRequest = { opened = null }, title = { Text("Completed files") }, text = {
        LazyColumn { items(job.files) { file -> TextButton(onClick = { openFile(context, Storage.openUri(context, file), if (job.choice.mp3) "audio/mpeg" else "video/mp4") }) { Text(file.title) } } }
    }, confirmButton = { TextButton(onClick = { opened = null }) { Text("Close") } }) }
}

@Composable private fun SettingsScreen(model: AppModel, folder: () -> Unit) {
    var licenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp)) }
        item { DestinationCard(model.folder, folder) }
        item {
            GlassCard {
                Heading("Appearance")
                listOf("system" to "System default", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { model.setAppearance(value) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(model.theme == value, { model.setAppearance(value) }); Text(label)
                    }
                }
            }
        }
        item {
            GlassCard {
                Heading("Download engine")
                Text("yt-dlp ${Store.preferences(context).getString("engine-version", "2026.10.21")}\nPython, FFmpeg and QuickJS included.", fontSize = 13.sp)
                GlassButton(onClick = model::updateEngine, enabled = !model.updating) { Text(if (model.updating) "Updating…" else "Check and update yt-dlp") }
                TextButton(onClick = model::copyDiagnostics) { Text("Copy diagnostics") }
                if (model.updating) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        item {
            GlassCard {
                Heading("Temporary storage")
                Text("Paused downloads retain partial data. ZIP originals remain in the app. Clearing removes only temporary copies of completed, exported downloads.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = model::cleanCompleted) { Text("Clear completed temporary files") }
            }
        }
        if (model.error.isNotBlank()) item { ErrorCard(model.error) }
        if (model.status.isNotBlank()) item { GlassCard { Text(model.status, fontSize = 13.sp) } }
        item {
            GlassCard {
                Heading("Ytdlp-G ${BuildConfig.VERSION_NAME}")
                Text("A simple interface for videos, music and playlists. Execution core from YTDLnis by Deniscerri and contributors, under GPLv3.", fontSize = 13.sp)
                TextButton(onClick = { licenses = true }) { Text("Licenses and source code") }
            }
        }
    }
    if (licenses) AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Open source") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Ytdlp-G: GPLv3. Core based on YTDLnis, © Deniscerri and contributors. yt-dlp: Unlicense; EJS: ISC; Python: PSF; QuickJS: MIT; FFmpeg includes GPL components. License texts are bundled in the APK and corresponding source accompanies the release. Backdrop 1.0.3, © 2025 Kyant, under Apache 2.0. Visual components adapted from Square/Convx (2026, GPLv3) and the Backdrop catalog (Apache 2.0).", fontSize = 13.sp)
            TextButton(onClick = { openFile(context, Uri.parse("https://github.com/fuhbe/Ytdlp-G-Android"), null) }) { Text("Ytdlp-G source") }
            TextButton(onClick = { openFile(context, Uri.parse("https://github.com/deniscerri/ytdlnis"), null) }) { Text("YTDLnis project") }
        }
    }, confirmButton = { TextButton(onClick = { licenses = false }) { Text("Close") } })
}

private fun openFile(context: Context, uri: Uri, mime: String?) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) { Toast.makeText(context, "Cannot open this file. It may have moved, or no compatible app is installed.", Toast.LENGTH_LONG).show() }
}
internal fun duration(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(Locale.ROOT, seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
internal fun bytes(value: Long): String = if (value >= 1024 * 1024) "%.1f MB".format(Locale.getDefault(), value / 1048576.0) else "%.0f KB".format(Locale.getDefault(), value / 1024.0)
