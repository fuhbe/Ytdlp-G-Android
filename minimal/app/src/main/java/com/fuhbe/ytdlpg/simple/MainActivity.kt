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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@Composable fun GlassTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFA4D0FF), onPrimary = Color(0xFF07325C),
        background = Color(0xFF0B1C34), surface = Color(0xFF1D3653), onSurface = Color(0xFFF1F7FF), onSurfaceVariant = Color(0xFFBFD2E9))
    else lightColorScheme(primary = Blue, onPrimary = Color.White, background = Color(0xFFD8E9FB), surface = Color(0xFFEDF5FE),
        onSurface = Color(0xFF102F54), onSurfaceVariant = Color(0xFF486480))
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            Box(Modifier.fillMaxSize().background(scheme.background)) {
                GlassBackdrop(dark)
                content()
            }
        }
    }
}

/** Static luminous curves approximate frosted glass without per-frame blur. */
@Composable private fun GlassBackdrop(dark: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val pale = if (dark) Color(0xFF3B78A8) else Color.White
        drawRect(Brush.linearGradient(if (dark) listOf(Color(0xFF193D61), Color(0xFF0B1C34), Color(0xFF254566)) else
            listOf(Color(0xFFBEDDFB), Color(0xFFE4F1FF), Color(0xFFC2DEFA), Color(0xFFF0F6FE)), end = Offset(size.width, size.height)))
        drawCircle(Brush.radialGradient(listOf(pale.copy(alpha = .62f), Color.Transparent), center = Offset(size.width * .84f, size.height * .12f), radius = size.width * .9f), size.width * .9f, Offset(size.width * .84f, size.height * .12f))
        for (i in 0..2) {
            val path = Path().apply {
                moveTo(-size.width * .3f, size.height * (.22f + i * .018f))
                cubicTo(size.width * 1.5f, size.height * -.12f, -size.width * .6f, size.height * .83f, size.width * 1.3f, size.height * (.7f + i * .027f))
            }
            drawPath(path, Brush.linearGradient(listOf(Color.Transparent, pale.copy(alpha = .2f), pale.copy(alpha = .5f), Color.Transparent)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = (5 + i * 12).dp.toPx()))
        }
    }
}

@Composable fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminanceValue() < .3f
    Column(modifier.shadow(9.dp, Shape, ambientColor = Color(0xFF3773AA).copy(alpha = .12f), spotColor = Color(0xFF3773AA).copy(alpha = .12f))
        .clip(Shape).background(Brush.linearGradient(if (dark) listOf(Color(0xA63E6185), Color(0xB3213D5B)) else listOf(Color(0xBFFFFFFF), Color(0x70FFFFFF))))
        .border(1.dp, if (dark) Color(0x556D9DC7) else Color(0xDCFFFFFF), Shape).padding(15.dp).animateContentSize(tween(160)), content = content)
}
private fun Color.luminanceValue() = red * .2126f + green * .7152f + blue * .0722f

@Composable fun AppContent(model: AppModel) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (model.theme) { "dark" -> true; "light" -> false; else -> systemDark }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
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
        } catch (_: Exception) { model.error = "Não foi possível manter acesso a essa pasta. Escolha outra pasta." }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val chooseFolder = { folderPicker.launch(model.folder.takeIf { it.isNotBlank() }?.let(Uri::parse)) }
    BackHandler(model.area != "home" || model.inspection?.playlist == true) {
        if (model.area != "home") model.area = "home" else model.clear()
    }
    GlassTheme(dark) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (model.area != "home" || model.inspection?.playlist == true) {
                    IconButton(onClick = { if (model.area != "home") model.area = "home" else model.clear() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                } else {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Color(0xFF56B7FF), Blue))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(25.dp)) }
                    Spacer(Modifier.width(10.dp))
                }
                Text("Ytdlp-G", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (model.area != "downloads") IconButton(onClick = { model.area = "downloads" }) { Icon(Icons.Default.DownloadForOffline, "Downloads") }
                if (model.area != "settings") IconButton(onClick = { model.area = "settings" }) { Icon(Icons.Default.Settings, "Configurações") }
            }
            if (model.area == "downloads") DownloadsScreen(jobs, model::control)
            else if (model.area == "settings") SettingsScreen(model, chooseFolder)
            else HomeScreen(model, chooseFolder) {
                focus.clearFocus()
                if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                model.enqueue()
            }
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
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).shadow(if (enabled) 7.dp else 0.dp, RoundedCornerShape(22.dp), spotColor = Blue.copy(alpha = .4f))) {
        Row(Modifier.background(Brush.verticalGradient(if (enabled) listOf(Color(0xFF61BCFF), Color(0xFF1683F9), Color(0xFF0767E5)) else listOf(Color(0xFF7D93AA), Color(0xFF6A829B))))
            .border(1.dp, Color.White.copy(alpha = .6f), RoundedCornerShape(22.dp)).padding(15.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
@Composable private fun Segments(labels: List<String>, icons: List<ImageVector>, selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .45f)).border(1.dp, Color.White.copy(alpha = .5f), RoundedCornerShape(19.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Surface(onClick = { onSelected(index) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
                Row(Modifier.background(if (active) Brush.verticalGradient(listOf(Color(0xFF65BCFF), Color(0xFF0876F1))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))).padding(horizontal = 10.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(icons[index], null, modifier = Modifier.size(20.dp), tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable private fun Preview(media: Media, subtitle: String = "", large: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (large) 102.dp else 72.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
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
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (!playlist) item {
            GlassCard {
                Heading("Link do vídeo")
                OutlinedTextField(model.url, { model.url = it; if (inspection != null) model.clear() },
                    placeholder = { Text("Cole um ou vários links do YouTube", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(20.dp)) },
                    trailingIcon = { IconButton(onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        model.clear(); model.url = clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    }) { Icon(Icons.Default.ContentPaste, "Colar link", Modifier.size(20.dp)) } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(16.dp), maxLines = 4,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); model.analyze() }))
                if (inspection == null) ActionButton(if (model.busy) "Analisando…" else "Analisar link", Icons.Default.AutoAwesome, !model.busy && model.url.isNotBlank()) { focus.clearFocus(); model.analyze() }
                if (model.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    Text(model.status, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { model.clear() }) { Text("Cancelar análise") }
                }
            }
        }
        if (model.error.isNotBlank()) item { ErrorCard(model.error) }
        if (inspection != null) {
            item {
                GlassCard {
                    if (playlist) Text("Playlist detectada!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    else Heading("Prévia do vídeo")
                    val preview = inspection.items.first().let { if (playlist) it.copy(title = inspection.title) else it }
                    Preview(preview, if (playlist) "${inspection.items.size} vídeos • ${duration(inspection.items.sumOf { it.duration })}" else duration(preview.duration), true)
                }
            }
            if (playlist) {
                item {
                    Heading("Vídeos da playlist", "${model.selected.size} de ${inspection.items.size} selecionados")
                    TextButton(onClick = {
                        model.selected = if (model.selected.size == inspection.items.count { it.available }) emptySet() else inspection.items.filter { it.available }.map { it.id }.toSet()
                        model.choice = Policy.choice(model.choice, model.selected.size)
                    }) { Text(if (model.selected.size == inspection.items.count { it.available }) "Limpar seleção" else "Selecionar todos") }
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
                if (inspection.items.size > 4) item { TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Mostrar menos" else "Mostrar todos os ${inspection.items.size} vídeos") } }
            }
            item {
                GlassCard {
                    Heading("Formato")
                    Segments(listOf("MP3", "MP4"), listOf(Icons.Default.MusicNote, Icons.Default.SmartDisplay), if (model.choice.mp3) 0 else 1) { model.choice = model.choice.copy(mp3 = it == 0) }
                    if (model.choice.mp3) Text("Extrai a melhor fonte de áudio e converte para MP3 em qualidade variável alta. A qualidade é limitada pela fonte.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 9.dp))
                }
            }
            if (!model.choice.mp3) item { QualityCard(inspection, model.choice.height) { model.choice = model.choice.copy(height = it) } }
            item { DestinationCard(model.folder, chooseFolder) }
            if (Policy.zipAllowed(model.selected.size)) item {
                GlassCard {
                    Heading("Modo de download")
                    Segments(listOf("Normal", "ZIP"), listOf(Icons.Default.Download, Icons.Default.FolderZip), if (model.choice.zip) 1 else 0) { model.choice = model.choice.copy(zip = it == 1) }
                    Text("ZIP reúne os arquivos selecionados. Os originais temporários são preservados até você limpar os concluídos.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item { ActionButton(if (model.queuing) "Iniciando…" else if (playlist) "Baixar playlist" else "Baixar", Icons.Default.Download, model.selected.isNotEmpty() && !model.queuing, download) }
        }
        if (inspection == null && !model.busy) item {
            Text("Vídeos e playlists, em MP3 ou MP4. Cole vários links, um por linha.\n\nBaixe apenas conteúdo que você tem autorização para salvar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable private fun PlaylistRow(media: Media, model: AppModel) {
    Row(Modifier.fillMaxWidth().clickable(enabled = media.available) { model.select(media.id) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(media.id in model.selected, { model.select(media.id) }, enabled = media.available,
            modifier = Modifier.semantics { contentDescription = "Selecionar ${media.title}" })
        Box(Modifier.size(62.dp, 55.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Icon(Icons.Default.Landscape, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(media.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (media.duration > 0) Text(duration(media.duration), color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .7f), RoundedCornerShape(topStart = 4.dp)).padding(horizontal = 3.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(media.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(if (media.available) media.author else "Indisponível", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun QualityCard(inspection: Inspection, selected: Int, choose: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard {
        Heading("Qualidade de vídeo")
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.HighQuality, null, Modifier.size(23.dp)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (selected == 0) "Melhor qualidade" else "${selected}p", fontWeight = FontWeight.SemiBold)
                    Text(if (selected == 0) "Até 4K, quando disponível" else "Até a resolução selecionada", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ExpandMore, "Escolher qualidade")
            }
            DropdownMenu(expanded, { expanded = false }) {
                (listOf(0) + inspection.heights).forEach { value -> DropdownMenuItem(text = { Text(if (value == 0) "Melhor qualidade (até 4K)" else "${value}p") }, onClick = { choose(value); expanded = false }) }
            }
        }
        Text(if (inspection.playlist) "Resoluções encontradas no vídeo de referência. Se um item não oferecer a escolhida, usa a melhor disponível abaixo dela, sem aumentar a resolução."
            else "Somente resoluções encontradas. Vídeo e áudio são combinados; codecs incompatíveis são convertidos para MP4.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
    }
}
@Composable private fun DestinationCard(folder: String, choose: () -> Unit) {
    val context = LocalContext.current
    val label by produceState(if (folder.isBlank()) "Downloads/Ytdlp-G" else "Pasta selecionada", folder) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Storage.folderName(context, folder) }
    }
    GlassCard {
        Heading("Pasta de destino")
        Surface(onClick = choose, color = MaterialTheme.colorScheme.surface.copy(alpha = .3f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Salvar em", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, "Alterar pasta")
            }
        }
    }
}
@Composable private fun ErrorCard(text: String) {
    GlassCard { Text(text, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
}

@Composable private fun DownloadsScreen(jobs: List<QueueJob>, control: (String, String) -> Unit) {
    var completedTab by rememberSaveable { mutableStateOf(false) }
    var opened by remember { mutableStateOf<QueueJob?>(null) }
    val context = LocalContext.current
    val ongoing = jobs.filter { it.state != "DONE" }
    val completed = jobs.filter { it.state == "DONE" }
    Column(Modifier.fillMaxWidth()) {
        Segments(listOf("Downloads (${ongoing.size})", "Concluídos"), listOf(Icons.Default.Downloading, Icons.Default.CheckCircle), if (completedTab) 1 else 0) { completedTab = it == 1 }
        LazyColumn(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            val visible = if (completedTab) completed else jobs
            if (visible.isEmpty()) item { GlassCard { Text(if (completedTab) "Nenhum download concluído." else "Seus downloads aparecerão aqui.") } }
            items(visible, key = { it.id }) { job ->
                GlassCard {
                    val current = job.items.getOrNull(job.current.coerceAtMost(job.items.lastIndex)) ?: job.items.first()
                    Preview(current.copy(title = if (job.items.size > 1) job.title else current.title),
                        "${if (job.choice.mp3) "MP3 • melhor fonte" else "MP4"}${if (job.items.size > 1) " • ${job.current} de ${job.items.size}" else ""}${if (job.choice.zip) " • ZIP" else ""}")
                    val progress by animateFloatAsState(job.progress, label = "Progresso real")
                    Row(Modifier.padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)), color = if (job.state == "DONE") Color(0xFF129775) else Blue, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f))
                        Spacer(Modifier.width(10.dp))
                        Text("${(job.progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(if (job.speed > 0 && job.state == "RUNNING") "${bytes(job.speed)}/s • ${duration(job.eta)} restantes" else job.phase,
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    if (job.error.isNotBlank()) Text(job.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text(when (job.state) { "DONE" -> "Concluído"; "PAUSED" -> "Pausado"; "CANCELLED" -> "Cancelado"; "ERROR" -> "Erro"; "QUEUED" -> "Na fila"; else -> "Baixando" },
                            color = if (job.state == "DONE") Color(0xFF128466) else MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (job.state in listOf("RUNNING", "QUEUED")) IconButton(onClick = { control(job.id, "pause") }) { Icon(Icons.Default.Pause, "Pausar ${job.title}") }
                        if (job.state in listOf("PAUSED", "ERROR", "CANCELLED")) IconButton(onClick = { control(job.id, "resume") }) { Icon(if (job.state == "PAUSED") Icons.Default.PlayArrow else Icons.Default.Refresh, "Retomar ${job.title}") }
                        if (job.state == "DONE") IconButton(onClick = {
                            if (job.archiveUri.isNotBlank()) openFile(context, Uri.parse(job.archiveUri), "application/zip")
                            else if (job.files.size == 1) openFile(context, Storage.openUri(context, job.files.first()), if (job.choice.mp3) "audio/mpeg" else "video/mp4")
                            else opened = job
                        }) { Icon(Icons.Default.FolderOpen, "Abrir arquivos de ${job.title}") }
                        else if (job.state != "CANCELLED") IconButton(onClick = { control(job.id, "cancel") }) { Icon(Icons.Default.Close, "Cancelar ${job.title}") }
                    }
                    if (job.state == "DONE") {
                        Text("${bytes(job.files.sumOf { it.bytes })}${job.files.firstOrNull()?.height?.takeIf { it > 0 }?.let { " • ${it}p" }.orEmpty()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    opened?.let { job -> AlertDialog(onDismissRequest = { opened = null }, title = { Text("Arquivos concluídos") }, text = {
        LazyColumn { items(job.files) { file -> TextButton(onClick = { openFile(context, Storage.openUri(context, file), if (job.choice.mp3) "audio/mpeg" else "video/mp4") }) { Text(file.title) } } }
    }, confirmButton = { TextButton(onClick = { opened = null }) { Text("Fechar") } }) }
}

@Composable private fun SettingsScreen(model: AppModel, folder: () -> Unit) {
    var licenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Configurações", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp)) }
        item { DestinationCard(model.folder, folder) }
        item {
            GlassCard {
                Heading("Aparência")
                listOf("system" to "Automático", "light" to "Claro", "dark" to "Escuro").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { model.setAppearance(value) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(model.theme == value, { model.setAppearance(value) }); Text(label)
                    }
                }
            }
        }
        item {
            GlassCard {
                Heading("Mecanismo de download")
                Text("yt-dlp ${Store.preferences(context).getString("engine-version", "2026.10.21")}\nPython, FFmpeg e QuickJS incluídos.", fontSize = 13.sp)
                TextButton(onClick = model::updateEngine, enabled = !model.updating) { Text(if (model.updating) "Atualizando…" else "Verificar e atualizar yt-dlp") }
                TextButton(onClick = model::copyDiagnostics) { Text("Copiar diagnóstico") }
                if (model.updating) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        item {
            GlassCard {
                Heading("Armazenamento temporário")
                Text("Downloads pausados mantêm os dados parciais. Os originais do ZIP permanecem no aplicativo. Limpar remove apenas cópias temporárias de downloads concluídos e já salvos.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = model::cleanCompleted) { Text("Limpar temporários concluídos") }
            }
        }
        if (model.error.isNotBlank()) item { ErrorCard(model.error) }
        if (model.status.isNotBlank()) item { GlassCard { Text(model.status, fontSize = 13.sp) } }
        item {
            GlassCard {
                Heading("Ytdlp-G ${BuildConfig.VERSION_NAME}")
                Text("Interface simples para vídeos, músicas e playlists. Base de execução YTDLnis, de Deniscerri e colaboradores, sob GPLv3.", fontSize = 13.sp)
                TextButton(onClick = { licenses = true }) { Text("Licenças e código-fonte") }
            }
        }
    }
    if (licenses) AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Código aberto") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Ytdlp-G: GPLv3. Núcleo baseado no YTDLnis, © Deniscerri e colaboradores. yt-dlp: Unlicense; EJS: ISC; Python: PSF; QuickJS: MIT; FFmpeg inclui componentes GPL. Os textos das licenças estão incluídos no APK e o código correspondente acompanha a distribuição.", fontSize = 13.sp)
            TextButton(onClick = { openFile(context, Uri.parse("https://github.com/fuhbe/Ytdlp-G-Android"), null) }) { Text("Código do Ytdlp-G") }
            TextButton(onClick = { openFile(context, Uri.parse("https://github.com/deniscerri/ytdlnis"), null) }) { Text("Projeto YTDLnis") }
        }
    }, confirmButton = { TextButton(onClick = { licenses = false }) { Text("Fechar") } })
}

private fun openFile(context: Context, uri: Uri, mime: String?) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) { Toast.makeText(context, "Não foi possível abrir. O arquivo pode ter sido movido ou não há um aplicativo compatível instalado.", Toast.LENGTH_LONG).show() }
}
internal fun duration(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(Locale.ROOT, seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
internal fun bytes(value: Long): String = if (value >= 1024 * 1024) "%.1f MB".format(Locale.getDefault(), value / 1048576.0) else "%.0f KB".format(Locale.getDefault(), value / 1024.0)
