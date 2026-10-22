package com.fuhbe.ytdlpg

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(54, 91, 127)
        window.navigationBarColor = android.graphics.Color.rgb(32, 66, 99)
        window.decorView.systemUiVisibility = 0
        val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        setContent { App(shared) }
    }
}

internal val Ink = Color(0xFFF2F7FF)
internal val SecondaryInk = Color(0xFFCCE0F5)
private val GlassEdge = Color.White.copy(alpha = 0.32f)
private val GlassShape = RoundedCornerShape(15.dp)

@Composable
internal fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF70BCFF), onPrimary = Color.White, surface = Color(0xFF315A80),
        onSurface = Ink, onSurfaceVariant = SecondaryInk, background = Color(0xFF254D72)
    )) {
        CompositionLocalProvider(LocalContentColor provides Ink) {
            Box(Modifier.fillMaxSize()) {
                GlassBackground()
                content()
            }
        }
    }
}

@Composable
private fun GlassBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(
            listOf(Color(0xFF658CB1), Color(0xFF467CAD), Color(0xFF2D5D87), Color(0xFF1D3F60)),
            end = Offset(size.width, size.height)
        ))
        rotate(32f, Offset(size.width * 0.5f, size.height * 0.4f)) {
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.04f),
                        Color(0xFFDAECF7).copy(alpha = 0.40f), Color.White.copy(alpha = 0.04f), Color.Transparent),
                    startX = size.width * 0.12f, endX = size.width * 0.65f
                ),
                topLeft = Offset(-size.width, -size.height),
                size = Size(size.width * 3, size.height * 3),
                cornerRadius = CornerRadius(90f)
            )
        }
        drawRect(Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width, 0f), radius = size.width * 1.3f
        ))
    }
}

@Composable
private fun App(shared: String, model: MediaViewModel = viewModel()) {
    val context = LocalContext.current
    val editor by model.state.collectAsState()
    val running by DownloadState.running.collectAsState()
    val status by DownloadState.status.collectAsState()
    val progress by DownloadState.progress.collectAsState()
    val current by DownloadState.current.collectAsState()
    val detail by DownloadState.detail.collectAsState()
    val results by DownloadState.results.collectAsState()
    var showDownload by rememberSaveable { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) model.chooseFolder(it)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.startDownload()
        showDownload = true
    }
    LaunchedEffect(shared) { if (shared.isNotBlank()) model.input(shared, immediately = true) }
    LaunchedEffect(running) { if (running) showDownload = true }
    fun download() {
        if (Build.VERSION.SDK_INT < 29 && editor.folder == null) {
            folderPicker.launch(null)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else { model.startDownload(); showDownload = true }
    }
    GlassTheme {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Header(showDownload, onBack = { showDownload = false })
            if (showDownload && DownloadState.task != null) DownloadContent(
                DownloadState.task!!, running, status, progress, current, detail, results,
                onCancel = { context.startService(Intent(context, DownloadService::class.java).setAction("cancel")) },
                onBack = { showDownload = false }
            ) else EditorContent(
                editor, running, model::input, model::cancelAnalysis, model::chooseKind,
                model::chooseQuality, model::chooseZip, { folderPicker.launch(editor.folder) }, ::download
            )
        }
    }
}

@Composable
internal fun Header(showBack: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = Ink)
        }
        Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(39.dp).clip(RoundedCornerShape(8.dp)))
        Text("Ytdlp-G", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more), tint = Ink) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.app_language)) }, onClick = {
                    menu = false
                    val intent = if (Build.VERSION.SDK_INT >= 33)
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + context.packageName))
                    else Intent(Settings.ACTION_LOCALE_SETTINGS)
                    runCatching { context.startActivity(intent) }
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { menu = false; about = true })
            }
        }
    }
    if (about) AlertDialog(onDismissRequest = { about = false },
        title = { Text("Ytdlp-G") },
        text = { Text("0.3.0-beta.1\nyt-dlp · Python · FFmpeg · QuickJS\ngithub.com/fuhbe/Ytdlp-G-Android") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("OK") } })
}

@Composable
internal fun EditorContent(
    state: EditorState, running: Boolean = false,
    onInput: (String, Boolean) -> Unit = { _, _ -> },
    onCancel: () -> Unit = {}, onKind: (String) -> Unit = {},
    onQuality: (Quality) -> Unit = {}, onZip: (Boolean) -> Unit = {},
    onFolder: () -> Unit = {}, onDownload: () -> Unit = {}
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel(Modifier.fillMaxWidth(), padding = 4.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFE6F1FB), Color(0xFFC8E0F3)))),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = state.url, onValueChange = { onInput(it, false) }, singleLine = true,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        textStyle = TextStyle(color = Color(0xFF17354F), fontSize = 12.sp),
                        cursorBrush = Brush.linearGradient(listOf(Color(0xFF207FFF), Color(0xFF207FFF))),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); onInput(state.url, true) }),
                        decorationBox = { field ->
                            if (state.url.isEmpty()) Text(stringResource(R.string.link_hint), color = Color(0xFF52718E), fontSize = 11.sp, maxLines = 1)
                            field()
                        }
                    )
                    if (state.url.isNotEmpty()) IconButton(onClick = { onInput("", false) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.clear), tint = Color(0xFF66839E), modifier = Modifier.size(17.dp))
                    }
                }
                GlassButton(stringResource(R.string.paste), onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (text.isNotBlank()) { focus.clearFocus(); onInput(text, true) }
                }, height = 42.dp, light = true)
            }
        }
        if (state.busy) GlassPanel(padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Ink, strokeWidth = 2.dp)
                Text(state.message, color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 9.dp))
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
            }
        }
        if (state.error.isNotEmpty()) ErrorPanel(state.error) { onInput(state.url, true) }
        val inspection = state.inspection
        if (inspection == null) {
            if (!state.busy && state.error.isEmpty()) Text(
                stringResource(R.string.ready), color = Ink, fontSize = 13.sp, modifier = Modifier.padding(6.dp)
            )
        } else {
            MediaSummary(inspection)
            GlassSegments(state.kind, listOf(
                Segment("video", stringResource(R.string.video_tab), 0),
                Segment("audio", stringResource(R.string.audio_tab), R.drawable.ic_music)
            ).filter { segment -> inspection.qualities.any { it.kind == segment.key } }, onKind)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.quality), Modifier.weight(1f))
                Text(stringResource(R.string.qualities_hint), color = SecondaryInk, fontSize = 10.sp)
            }
            GlassPanel(padding = 0.dp) {
                val options = inspection.qualities.filter { it.kind == state.kind }
                Column(Modifier.selectableGroup()) {
                    options.forEachIndexed { index, quality ->
                        QualityRow(quality, state.quality == quality, onClick = { onQuality(quality) })
                        if (index != options.lastIndex) HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.18f)
                        )
                    }
                }
            }
            SectionLabel(stringResource(R.string.files_tab))
            GlassSegments(if (state.zip) "zip" else "files", listOf(
                Segment("files", stringResource(R.string.normal_files), R.drawable.ic_file),
                Segment("zip", stringResource(R.string.zip_tab), R.drawable.ic_archive)
            ), { onZip(it == "zip") }, compact = true)
            SectionLabel(stringResource(R.string.save_to))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassPanel(Modifier.weight(1f), padding = 9.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Glyph(R.drawable.ic_folder, 21.dp)
                        Text(folderDisplay(state.folder), color = Ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                GlassButton(stringResource(R.string.change), onClick = onFolder, light = true, height = 40.dp)
            }
            GlassButton(
                stringResource(R.string.download), onClick = { focus.clearFocus(); onDownload() },
                modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_download, height = 48.dp,
                enabled = !running && !state.busy && state.quality != null
            )
            if (inspection.skipped > 0) Text(
                stringResource(R.string.skipped_count, inspection.skipped), color = SecondaryInk, fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(label, modifier, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun MediaSummary(inspection: Inspection) {
    var expanded by remember { mutableStateOf(false) }
    GlassPanel(Modifier.fillMaxWidth().clickable { expanded = true }, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumbnail(inspection.thumbnail, Modifier.size(width = 90.dp, height = 62.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(inspection.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (inspection.uploader.isNotEmpty()) Text(inspection.uploader, color = SecondaryInk, fontSize = 11.sp, maxLines = 1)
                Text(
                    if (inspection.items.size == 1) stringResource(R.string.single_video)
                    else stringResource(R.string.available_count, inspection.items.size),
                    color = SecondaryInk, fontSize = 11.sp
                )
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = Ink, modifier = Modifier.size(20.dp))
        }
    }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false }, title = { Text(inspection.title) },
        text = { Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            inspection.items.forEachIndexed { index, item -> Text("${index + 1}. ${item.title}", modifier = Modifier.padding(vertical = 5.dp)) }
        } }, confirmButton = { TextButton(onClick = { expanded = false }) { Text("OK") } }
    )
}

@Composable
private fun QualityRow(quality: Quality, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Canvas(Modifier.size(23.dp)) {
            drawCircle(if (selected) Color.White else Color(0xFFB8D9EF), style = Stroke(2.dp.toPx()))
            if (selected) {
                drawCircle(Color(0xFF0C7BFF), radius = size.width * 0.38f)
                drawCircle(Color.White, radius = size.width * 0.23f)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (quality.original) quality.label else stringResource(R.string.mp3_title),
                color = Ink, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            if (!quality.original) Text(stringResource(R.string.mp3_subtitle), color = SecondaryInk, fontSize = 10.sp)
        }
        if (quality.original && quality.kind == "audio") Text(
            stringResource(R.string.original), color = SecondaryInk, fontSize = 10.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.11f)).border(0.5.dp, GlassEdge, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp)
        )
        if (quality.bytes > 0) Text("~" + fileSize(quality.bytes), color = SecondaryInk, fontSize = 10.sp)
    }
}

private data class Segment(val key: String, val text: String, val icon: Int)

@Composable
private fun GlassSegments(selected: String, options: List<Segment>, onClick: (String) -> Unit, compact: Boolean = false) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.27f), Color(0xFF90C7EE).copy(alpha = 0.15f))))
            .border(1.dp, GlassEdge, shape).padding(2.dp).selectableGroup()
    ) {
        options.forEach { option ->
            val active = selected == option.key
            Row(
                Modifier.weight(1f).height(if (compact) 42.dp else 44.dp).clip(shape)
                    .background(if (active) Brush.verticalGradient(listOf(Color(0xFF40A9FF), Color(0xFF0073FF)))
                    else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .then(if (active) Modifier.border(1.dp, Color.White.copy(alpha = 0.55f), shape) else Modifier)
                    .selectable(active, role = Role.Tab, onClick = { onClick(option.key) }),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                if (option.icon != 0) Glyph(option.icon, 20.dp)
                else Icon(Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(9.dp))
                Text(option.text, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun DownloadContent(
    task: DownloadState.Task, running: Boolean, status: String, progress: Float, index: Int,
    detail: String, results: List<ItemResult>, onCancel: () -> Unit, onBack: () -> Unit
) {
    val current = task.inspection.items.getOrNull(index)
    val completed = results.filter { it.uri != null }
    val failures = results.filter { it.error.isNotEmpty() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MediaSummary(task.inspection)
        GlassPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status.ifBlank { stringResource(R.string.preparing) }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${if (running) index + 1 else results.size} / ${task.inspection.items.size}", color = SecondaryInk, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                GlassProgress(progress, Modifier.weight(1f))
                Text("${(progress * 100).toInt()}%", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
            }
            if (running && current != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    Thumbnail(current.thumbnail, Modifier.size(width = 70.dp, height = 59.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(current.uploader, color = SecondaryInk, fontSize = 11.sp)
                        Text(progressDetail(detail), color = SecondaryInk, fontSize = 10.sp, maxLines = 2)
                    }
                }
                GlassButton(stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth(), light = true, close = true)
            } else GlassButton(stringResource(R.string.back), onClick = onBack, modifier = Modifier.fillMaxWidth(), light = true)
        }
        GlassPanel(padding = 10.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Glyph(R.drawable.ic_shield, 22.dp)
                Column {
                    Text(stringResource(R.string.saving_in), color = SecondaryInk, fontSize = 10.sp)
                    Text(folderDisplay(task.folder), color = SecondaryInk, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (completed.isNotEmpty()) ResultsPanel(completed)
        if (failures.isNotEmpty()) GlassPanel {
            SectionLabel(stringResource(R.string.failed_items, failures.size))
            failures.forEach { result ->
                Text(result.item.title, color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                ErrorPanel(result.error, compact = true)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun GlassProgress(progress: Float, modifier: Modifier = Modifier) {
    val value = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.height(16.dp).clip(shape).background(Color.White.copy(alpha = 0.14f))
        .border(0.6.dp, GlassEdge, shape).progressSemantics(value)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF58BDFF), Color(0xFF168DFA)))))
    }
}

@Composable
private fun ResultsPanel(results: List<ItemResult>) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(true) }
    GlassPanel(padding = 11.dp) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.completed_items, results.size), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, null, tint = Ink)
        }
        if (expanded) results.forEachIndexed { index, result ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 7.dp), color = Color.White.copy(alpha = 0.16f))
            Row(
                Modifier.fillMaxWidth().clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(result.uri, context.contentResolver.getType(result.uri!!))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.no_file_app, Toast.LENGTH_SHORT).show()
                    }
                }.padding(top = if (index == 0) 10.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Thumbnail(result.item.thumbnail, Modifier.size(width = 51.dp, height = 43.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.item.title, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(result.item.uploader, color = SecondaryInk, fontSize = 10.sp, maxLines = 1)
                }
                Text(fileSize(result.bytes), color = SecondaryInk, fontSize = 10.sp)
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF5DDB9D), modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun ErrorPanel(error: String, compact: Boolean = false, onRetry: (() -> Unit)? = null) {
    val context = LocalContext.current
    var details by remember(error) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(if (error.contains("403")) stringResource(R.string.source_denied) else error.take(250),
            color = Color(0xFFFFD7D4), fontSize = if (compact) 11.sp else 12.sp)
        Row {
            TextButton(onClick = { details = !details }, contentPadding = PaddingValues(end = 12.dp)) {
                Text(stringResource(R.string.details), color = Ink, fontSize = 11.sp)
            }
            if (onRetry != null) TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(stringResource(R.string.retry), color = Ink, fontSize = 11.sp)
            }
        }
        if (details) {
            Text(error, color = SecondaryInk, fontSize = 10.sp)
            TextButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                    ClipData.newPlainText("Ytdlp-G", error)
                )
            }) { Text(stringResource(R.string.copy_details), color = Ink, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun progressDetail(raw: String): String = when {
    raw.startsWith("[download]") -> raw.removePrefix("[download]").trim()
    raw.contains("ExtractAudio") -> stringResource(R.string.converting)
    raw.isNotBlank() && !raw.startsWith("[") -> raw
    else -> stringResource(R.string.download_in_progress)
}

@Composable
private fun Thumbnail(url: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFF416C91)).border(0.5.dp, GlassEdge, RoundedCornerShape(7.dp))) {
        Icon(Icons.Default.PlayArrow, null, tint = SecondaryInk, modifier = Modifier.align(Alignment.Center).size(25.dp))
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun Glyph(resource: Int, size: Dp) {
    Icon(painterResource(resource), null, tint = Color.Unspecified, modifier = Modifier.size(size))
}

@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier.fillMaxWidth(),
    padding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides Ink) {
        Column(
            modifier.clip(GlassShape).background(Brush.verticalGradient(
                listOf(Color(0xFF8FB9DE).copy(alpha = 0.36f), Color(0xFF416F99).copy(alpha = 0.45f))
            )).border(0.8.dp, GlassEdge, GlassShape).padding(padding),
            content = content
        )
    }
}

@Composable
private fun GlassButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: Int = 0, light: Boolean = false, close: Boolean = false,
    height: Dp = 42.dp, enabled: Boolean = true
) {
    val shape = RoundedCornerShape(if (height >= 48.dp) 14.dp else 11.dp)
    Row(
        modifier.height(height).clip(shape)
            .background(Brush.verticalGradient(
                if (!enabled) listOf(Color(0xFF527B9D), Color(0xFF456888))
                else if (light) listOf(Color(0xFF99BDE0).copy(alpha = 0.8f), Color(0xFF709BC5).copy(alpha = 0.65f))
                else listOf(Color(0xFF3AA8FF), Color(0xFF0874F6))
            )).border(0.8.dp, GlassEdge, shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (light) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != 0) { Glyph(icon, 22.dp); Spacer(Modifier.width(10.dp)) }
        if (close) { Icon(Icons.Default.Close, null, tint = Ink); Spacer(Modifier.width(7.dp)) }
        Text(text, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = if (height >= 48.dp) 16.sp else 12.sp, maxLines = 1)
    }
}

internal fun folderDisplay(uri: Uri?): String {
    if (uri == null) return "Download/Ytdlp-G"
    val path = uri.lastPathSegment.orEmpty()
    return if (path.startsWith("primary:")) path.substringAfter(':') else path.replace(':', '/')
}

private fun fileSize(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
