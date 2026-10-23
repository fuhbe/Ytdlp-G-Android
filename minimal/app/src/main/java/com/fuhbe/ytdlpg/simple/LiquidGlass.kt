package com.fuhbe.ytdlpg.simple

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/** Presentation capabilities only. Never reads or changes download policy. */
internal enum class GlassRendering { TINT, BLUR, LENS }
internal fun glassRendering(api: Int, hardware: Boolean, lowRam: Boolean, memoryClass: Int, powerSave: Boolean): GlassRendering = when {
    !hardware || lowRam || memoryClass < 192 || powerSave || api < 31 -> GlassRendering.TINT
    api < 33 -> GlassRendering.BLUR
    else -> GlassRendering.LENS
}

internal val LocalGlassRendering = staticCompositionLocalOf { GlassRendering.TINT }
internal val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalGlassDark = staticCompositionLocalOf { false }

@Composable internal fun LiquidGlassTheme(dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val manager = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var rendering by remember(view) { mutableStateOf(GlassRendering.TINT) }
    // Window attachment can follow the first composition. Refresh on focus/attach,
    // including after Quick Settings changes power saver; never poll per frame.
    DisposableEffect(view, dark) {
        var disposed = false
        val refresh = Runnable { if (!disposed) rendering = glassRendering(Build.VERSION.SDK_INT,
            view.isHardwareAccelerated, manager.isLowRamDevice, manager.memoryClass, power.isPowerSaveMode) }
        val attached = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { view.post(refresh) }
            override fun onViewDetachedFromWindow(v: View) { rendering = GlassRendering.TINT }
        }
        val focused = ViewTreeObserver.OnWindowFocusChangeListener { if (it) view.post(refresh) }
        view.addOnAttachStateChangeListener(attached)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focused)
        view.post(refresh)
        onDispose {
            disposed = true; view.removeCallbacks(refresh); view.removeOnAttachStateChangeListener(attached)
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focused)
        }
    }
    LaunchedEffect(rendering) { android.util.Log.i("YtdlpG-Glass", "material=${rendering.name} api=${Build.VERSION.SDK_INT}") }
    val wallpaper = rememberLayerBackdrop()
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFACDDFF), onPrimary = Color(0xFF082B50),
        background = Color(0xFF07172E), surface = Color(0xFF142D4B), surfaceVariant = Color(0xFF274764),
        onSurface = Color(0xFFF6FAFF), onSurfaceVariant = Color(0xFFD5E4F5), error = Color(0xFFFFCBCB))
    else lightColorScheme(primary = Color(0xFFC1E7FF), onPrimary = Color(0xFF10345B),
        background = Color(0xFF31699B), surface = Color(0xFF315F8A), surfaceVariant = Color(0xFF527CA3),
        onSurface = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFFDFEDFC), error = Color(0xFFFFD9D9))
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, LocalGlassDark provides dark,
            LocalGlassRendering provides rendering, LocalGlassBackdrop provides wallpaper) {
            Box(Modifier.fillMaxSize().background(scheme.background)) {
                Canvas(Modifier.fillMaxSize().then(if (rendering != GlassRendering.TINT) Modifier.layerBackdrop(wallpaper) else Modifier)) {
                    glassWallpaper(dark)
                }
                content()
            }
        }
    }
}

/** One static captured layer; these color fields are actual backdrop pixels. */
private fun DrawScope.glassWallpaper(dark: Boolean) {
    val w = size.width; val h = size.height
    drawRect(Brush.linearGradient(if (dark) listOf(Color(0xFF081C3B), Color(0xFF102945), Color(0xFF071121))
        else listOf(Color(0xFF1D72C2), Color(0xFF587EAC), Color(0xFF3C648B)), end = Offset(w, h)))
    val fields = if (dark) listOf(Color(0xFF215F9F), Color(0xFF3B4A75), Color(0xFF294F69), Color(0xFF846F61))
        else listOf(Color(0xFF4CA5D7), Color(0xFF7C9BB7), Color(0xFF285F9D), Color(0xFFAA9484))
    val centers = listOf(Offset(w * .1f, h * .05f), Offset(w * .94f, h * .28f), Offset(w * .35f, h * .65f), Offset(w * .87f, h * .84f))
    centers.forEachIndexed { i, center ->
        val radius = w * if (i == 3) 1.05f else .9f
        drawCircle(Brush.radialGradient(listOf(fields[i].copy(alpha = if (dark) .5f else .78f), Color.Transparent), center, radius), radius, center)
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, if (dark) Color(0x9A020A18) else Color(0x4011355C))))
}

/** No gestures or click handling: this modifier only paints behind the control. */
@Composable internal fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(23.dp),
    prominent: Boolean = false,
    selected: Boolean = false,
    source: Backdrop? = LocalGlassBackdrop.current
): Modifier {
    val dark = LocalGlassDark.current
    val rendering = LocalGlassRendering.current
    var shaderRejected by remember { mutableStateOf(false) }
    val tint = if (selected) Color(0xFF087DF1).copy(alpha = .60f)
        else if (dark) Color(0xFF102641).copy(alpha = if (rendering == GlassRendering.TINT) .64f else .58f)
        else Color(0xFF173F69).copy(alpha = if (rendering == GlassRendering.TINT) .55f else .50f)
    val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = if (selected) .26f else if (dark) .08f else .10f), Color.White.copy(alpha = .025f)))
    val edge = Brush.linearGradient(listOf(Color.White.copy(alpha = if (prominent) .85f else .48f),
        Color(0xFFB9E8FF).copy(alpha = .13f), Color.White.copy(alpha = .35f)))
    val effect = if (source != null && rendering != GlassRendering.TINT) Modifier.drawBackdrop(
        backdrop = source, shape = { shape }, effects = {
            // Ordinary cards blur; only essential interactive surfaces use the lens.
            blur((if (prominent) 7.dp else 5.dp).toPx())
            if (prominent && rendering == GlassRendering.LENS && !shaderRejected) {
                try { lens(10.dp.toPx(), 16.dp.toPx(), depthEffect = true, chromaticAberration = false) }
                catch (_: RuntimeException) { shaderRejected = true; android.util.Log.w("YtdlpG-Glass", "Lens unavailable; preserving background blur") }
                catch (_: LinkageError) { shaderRejected = true; android.util.Log.w("YtdlpG-Glass", "Lens linkage unavailable; preserving background blur") }
            }
        }, highlight = null, shadow = null,
        onDrawSurface = { drawRect(tint); drawRect(sheen) }
    ) else Modifier.background(tint, shape).background(sheen, shape)
    return shadow(if (prominent) 7.dp else 3.dp, shape, ambientColor = Color.Black.copy(alpha = .1f), spotColor = Color.Black.copy(alpha = .15f))
        .then(effect).clip(shape).border(if (prominent) 1.dp else .75f.dp, edge, shape)
}

/** Reuse the static sampled wallpaper; never capture animated download progress.
 * The dock has its own reserved space, so it cannot cover an actionable control. */
@Composable internal fun GlassContentLayer(content: @Composable (Modifier, Backdrop?) -> Unit) {
    content(Modifier, LocalGlassBackdrop.current)
}

/** Relocates existing destinations; selected pages have no duplicate click action. */
@Composable internal fun GlassNavigationDock(
    area: String, onBack: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit,
    source: Backdrop?, modifier: Modifier = Modifier
) {
    Row(modifier.testTag("glass-navigation").liquidGlass(RoundedCornerShape(30.dp), prominent = true, source = source).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        DockDestination("Início", "Voltar", Icons.Default.Home, area == "home", onBack, Modifier.weight(1f))
        DockDestination("Downloads", "Downloads", Icons.Default.DownloadForOffline, area == "downloads", onDownloads, Modifier.weight(1f))
        DockDestination("Configurações", "Configurações", Icons.Default.Settings, area == "settings", onSettings, Modifier.weight(1f))
    }
}

@Composable private fun DockDestination(label: String, description: String, icon: ImageVector,
    selected: Boolean, action: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(25.dp)
    val decoration = if (selected) Modifier.liquidGlass(shape, prominent = true, selected = true) else Modifier
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().then(decoration).padding(horizontal = 3.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = Color.White)
            Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = Color.White)
        }
    }
    if (selected) Box(modifier, contentAlignment = Alignment.Center) { body() }
    else Surface(onClick = action, color = Color.Transparent, shape = shape,
        modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = description }) { body() }
}
