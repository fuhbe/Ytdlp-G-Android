package com.fuhbe.ytdlpg.simple

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import com.kyant.backdrop.Backdrop

/** Source exists only while an existing quality popup needs it. It records the
 * actual underlying screen and wallpaper, never the popup (no feedback loop).
 * No bitmap readback, CPU blur, engine state, timers or background work. */
internal class GlassOverlaySource(val backdrop:GlassContentBackdrop) {
    var users by mutableIntStateOf(0)
    var ready by mutableStateOf(false)
    var frozenUsers by mutableIntStateOf(0)
    var records=0
}
internal val LocalGlassOverlaySource=staticCompositionLocalOf<GlassOverlaySource?> { null }

@Composable internal fun GlassOverlayCapture(content:@Composable (Modifier,Backdrop?)->Unit) {
    val wallpaper=LocalGlassBackdrop.current
    val rendering=LocalGlassRendering.current
    val layer=rememberGraphicsLayer()
    val overlay=remember(layer) { GlassOverlaySource(GlassContentBackdrop(layer)) }
    var rejected by remember { mutableStateOf(false) }
    val dark=LocalGlassDark.current
    DisposableEffect(dark,rendering) { overlay.ready=false;onDispose {} }
    val enabled=overlay.users>0 && rendering!=GlassRendering.TINT && !rejected
    val capture=if(enabled) Modifier.onGloballyPositioned { overlay.backdrop.coordinates=it }
        .drawWithContent {
            try {
                if(!overlay.ready || overlay.frozenUsers==0) { layer.record {
                    if(wallpaper!=null) with(wallpaper) { drawBackdrop(this@drawWithContent,overlay.backdrop.coordinates,null) }
                    this@drawWithContent.drawContent()
                }
                overlay.records++
                if(!overlay.ready) overlay.ready=true
                }
            } catch (_:RuntimeException) {
                rejected=true;overlay.ready=false
                android.util.Log.w("YtdlpG-Glass","Overlay source unavailable; compatible glass retained")
            } catch (_:LinkageError) {
                rejected=true;overlay.ready=false
                android.util.Log.w("YtdlpG-Glass","Overlay source unsupported; compatible glass retained")
            }
            drawContent()
        } else Modifier
    DisposableEffect(enabled) { if(!enabled) overlay.ready=false;onDispose {} }
    CompositionLocalProvider(LocalGlassOverlaySource provides overlay) { content(capture,wallpaper) }
}
