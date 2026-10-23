package com.fuhbe.ytdlpg.simple

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.view.ViewTreeObserver
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke

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
    else lightColorScheme(primary = Color(0xFF075DB9), onPrimary = Color.White,
        background = Color(0xFFC2DEED), surface = Color(0xFFDCEBF5), surfaceVariant = Color(0xFFB6D1E8),
        onSurface = Color(0xFF12324F), onSurfaceVariant = Color(0xFF2E506E), error = Color(0xFF962A32))
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, LocalGlassDark provides dark,
            LocalGlassRendering provides rendering, LocalGlassBackdrop provides wallpaper) {
            Box(Modifier.fillMaxSize().background(scheme.background)) {
                GlassWallpaper(dark, Modifier.fillMaxSize().then(
                    if (rendering != GlassRendering.TINT) Modifier.layerBackdrop(wallpaper) else Modifier))
                content()
            }
        }
    }
}

/** No gestures or click handling: this modifier only paints behind the control. */
@Composable internal fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(23.dp),
    prominent: Boolean = false,
    selected: Boolean = false,
    source: Backdrop? = LocalGlassBackdrop.current,
    interaction: () -> Float = { 0f },
    lightPosition: () -> Float = { .22f },
    exportedBackdrop: LayerBackdrop? = null
): Modifier {
    val dark = LocalGlassDark.current
    val rendering = LocalGlassRendering.current
    var shaderRejected by remember { mutableStateOf(false) }
    val tint = if (selected) Color(0xFF0059C8).copy(alpha = .86f)
        else if (dark) Color(0xFF081629).copy(alpha = if (rendering == GlassRendering.TINT) .46f else .32f)
        else Color(0xFFE6F5FF).copy(alpha = if (rendering == GlassRendering.TINT) .52f else .36f)
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (selected) .20f else if (dark) .07f else .22f), Color.Transparent))
    val reflection = Modifier.drawWithContent {
        drawContent()
        val pressure = interaction().coerceIn(0f,1f)
        val x = lightPosition().coerceIn(0f,1f)
        drawOutline(shape.createOutline(size,layoutDirection,this),
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha=if(prominent) .8f else .55f),
                Color(0xFFB9E8FF).copy(alpha=.12f),Color.White.copy(alpha=.28f)),
                center=Offset(size.width*x,-size.height*.15f),radius=size.width*(.7f+pressure*.2f)),
            style=Stroke((if(prominent) 1f+pressure*.5f else .75f).dp.toPx()))
    }
    val effect = if (source != null && rendering != GlassRendering.TINT) Modifier.drawBackdrop(
        backdrop = source, shape = { shape }, effects = {
            // Ordinary cards blur; only essential interactive surfaces use the lens.
            blur((if (prominent) 7.dp else 5.dp).toPx())
            if (prominent && rendering == GlassRendering.LENS && !shaderRejected) {
                try { lens((10f+interaction()*2f).dp.toPx(), (16f+interaction()*4f).dp.toPx(), depthEffect = true, chromaticAberration = false) }
                catch (_: RuntimeException) { shaderRejected = true; android.util.Log.w("YtdlpG-Glass", "Lens unavailable; preserving background blur") }
                catch (_: LinkageError) { shaderRejected = true; android.util.Log.w("YtdlpG-Glass", "Lens linkage unavailable; preserving background blur") }
            }
        }, exportedBackdrop = exportedBackdrop, highlight = {
            // Plain uses no additional AGSL shader; the moving specular field is
            // drawn below, including on Android 12. The backdrop lens remains real.
            Highlight(width=(.6f+interaction().coerceIn(0f,1f)*.4f).dp,
                alpha=if(prominent) .8f else .4f,style=HighlightStyle.Plain())
        }, shadow = null,
        onDrawSurface = {
            drawRect(tint); drawRect(sheen)
            val pressure=interaction().coerceIn(0f,1f)
            if(prominent) drawRect(Brush.radialGradient(
                listOf(Color.White.copy(alpha=.05f+pressure*.13f),Color.Transparent),
                center=Offset(size.width*lightPosition().coerceIn(0f,1f),0f),radius=size.width*.8f))
        }
    ) else Modifier.background(tint, shape).background(sheen, shape)
    return shadow(if (prominent) 7.dp else 3.dp, shape, ambientColor = Color.Black.copy(alpha = .1f), spotColor = Color.Black.copy(alpha = .15f))
        .then(effect).clip(shape).then(reflection)
}

/** Reuse the static sampled wallpaper; never capture animated download progress.
 * The dock has its own reserved space, so it cannot cover an actionable control. */
@Composable internal fun GlassContentLayer(content: @Composable (Modifier, Backdrop?) -> Unit) {
    GlassOverlayCapture(content)
}

