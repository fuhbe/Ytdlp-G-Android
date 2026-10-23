/* Adapted from Square LiquidBottomTabs, GPL-3.0 / Apache-2.0 catalog.
 * Pin 17ed2b87974cf6fec9c531ef3f1438448bb2b729. Local integration measures
 * actual child bounds, shares one gesture stream and never duplicates glyphs.
 * Full provenance and originals: minimal/vendor/square/.
 */
package com.fuhbe.ytdlpg.simple

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.fuhbe.ytdlpg.simple.squareglass.DampedDragAnimation
import com.fuhbe.ytdlpg.simple.squareglass.InteractiveHighlight
import com.fuhbe.ytdlpg.simple.squareglass.inspectDragGestures
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.roundToInt

private class SegmentBounds(val count:Int) {
    var row:LayoutCoordinates?=null
    val children=mutableMapOf<Int,LayoutCoordinates>()
    val rects=mutableStateMapOf<Int,Rect>()
    var size by mutableStateOf(IntSize.Zero)
    fun refresh() {
        val parent=row?.takeIf { it.isAttached } ?: return
        size=parent.size
        children.forEach { (index,child) -> if(child.isAttached) {
            val bounds=parent.localBoundingBoxOf(child,clipBounds=false)
            if(bounds.width>0 && bounds.height>0 && rects[index]!=bounds) rects[index]=bounds
        } }
    }
}
private val LocalSegmentBounds=staticCompositionLocalOf<SegmentBounds?> { null }
/** Attach to the existing clickable Surface. Physical bounds handle RTL and
 * localized/font-scaled widths without assuming equal slots. */
@Composable internal fun Modifier.glassSegmentItem(index:Int):Modifier {
    val registry=LocalSegmentBounds.current ?: return this
    DisposableEffect(registry,index) { onDispose { registry.children.remove(index);registry.rects.remove(index) } }
    return onGloballyPositioned { registry.children[index]=it;registry.refresh() }
}

internal fun glassSegmentTarget(point:Offset,size:IntSize,bounds:Map<Int,Rect>):Int? {
    if(!point.x.isFinite() || !point.y.isFinite() || point.x<0 || point.y<0 || point.x>=size.width || point.y>=size.height) return null
    return bounds.entries.firstOrNull { it.value.contains(point) }?.key
}

/** Interpolate physical centers and widths; logical indices never imply LTR. */
internal fun glassSegmentWidth(center:Float,bounds:Collection<Rect>):Float {
    val sorted=bounds.sortedBy { it.center.x }
    if(sorted.isEmpty()) return 0f
    if(center<=sorted.first().center.x) return sorted.first().width
    sorted.zipWithNext().forEach { (a,b) -> if(center<=b.center.x) {
        val t=((center-a.center.x)/(b.center.x-a.center.x).coerceAtLeast(1f)).coerceIn(0f,1f)
        return a.width+(b.width-a.width)*t
    } }
    return sorted.last().width
}

internal class GlassRenderStats {
    var compositions=0; var sourceRecords=0; var readbacks=0; var meshDraws=0
    var hardwareMesh=false
    var lastSnapshot:android.graphics.Bitmap?=null
    var lastGlyphSnapshot:android.graphics.Bitmap?=null
}
internal val LocalGlassRenderStats=staticCompositionLocalOf<GlassRenderStats?> { null }
// Instrumentation control only: never exposed as a product setting.
internal val LocalGlassOpticalStrength=staticCompositionLocalOf { 1f }

@Composable internal fun GlassSlidingSegments(
    selected:Int,count:Int,onSelected:(Int)->Unit,modifier:Modifier=Modifier,
    source:Backdrop?=LocalGlassBackdrop.current,puckTag:String="glass-selection-capsule",
    sourceKey:Any?=null,content:@Composable RowScope.()->Unit
) {
    val stats=LocalGlassRenderStats.current
    SideEffect { stats?.let { it.compositions++ } }
    val registry=remember(count) { SegmentBounds(count) }
    val scope=rememberCoroutineScope()
    val motion=remember(scope) { DampedDragAnimation(scope,.5f) }
    val highlight=remember(scope) { InteractiveHighlight(scope) }
    var dragging by remember { mutableStateOf(false) }
    val currentSelected by rememberUpdatedState(selected)
    val currentAction by rememberUpdatedState(onSelected)
    val rendering=LocalGlassRendering.current
    val sampled=rendering!=GlassRendering.TINT && source!=null
    val meshMode=sampled && android.os.Build.VERSION.SDK_INT in 31..32
    val trackBackdrop=rememberLayerBackdrop()
    val requests=remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var glyphBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var sourceReady by remember { mutableStateOf(false) }
    var captureRejected by remember { mutableStateOf(false) }
    val lens=remember { GlassMeshLens() }
    val contentLayer=androidx.compose.ui.graphics.rememberGraphicsLayer()
    val glyphLayer=androidx.compose.ui.graphics.rememberGraphicsLayer()
    val contentBackdrop=remember(contentLayer) { GlassContentBackdrop(contentLayer) }
    var releaseRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(contentLayer,meshMode,sampled) {
        for(event in requests) {
            androidx.compose.runtime.withFrameNanos { }
            if(motion.isTouching) continue // freeze bitmap source while held
            if(!sampled) continue
            sourceReady=true
            if(meshMode && !captureRejected && registry.size.width>0) {
                if(registry.size.width.toLong()*registry.size.height>2_000_000L) continue
                try {
                    val image=contentLayer.toImageBitmap()
                    val glyphs=glyphLayer.toImageBitmap()
                    val copy=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        image.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888,false) to
                            glyphs.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888,false)
                    }
                    if(copy.first!=null && copy.second!=null) {
                        bitmap=copy.first;glyphBitmap=copy.second
                        stats?.let { it.readbacks+=2;it.lastSnapshot=copy.first;it.lastGlyphSnapshot=copy.second }
                    }
                } catch (_:kotlinx.coroutines.CancellationException) { throw kotlinx.coroutines.CancellationException() }
                catch (_:RuntimeException) { captureRejected=true;android.util.Log.w("YtdlpG-Glass","Mesh capture unavailable; preserving compatible glass") }
                catch (_:LinkageError) { captureRejected=true;android.util.Log.w("YtdlpG-Glass","Mesh capture unsupported; preserving compatible glass") }
            }
        }
    }
    val currentRect=registry.rects[selected]
    val width=registry.size.width.toFloat()
    LaunchedEffect(currentRect,registry.size,selected,dragging) {
        if(!dragging && currentRect!=null && width>0) motion.animateToValue(currentRect.center.x/width)
    }
    val rectSnapshot=registry.rects.toMap()
    val physicalRects=remember(rectSnapshot) { rectSnapshot.values.sortedBy { it.center.x } }
    val ordered=remember(rectSnapshot) { rectSnapshot.entries.sortedBy { it.value.center.x }.map { it.key to it.value } }
    val inactive=androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val active=androidx.compose.material3.MaterialTheme.colorScheme.primary
    val ink=remember(inactive,active) { GlassSegmentInk(inactive,active) }
    fun center():Float {
        val first=physicalRects.firstOrNull() ?: return 0f
        return glassRubberCenter(motion.value*width,first.center.x,physicalRects.last().center.x,first.width)
    }
    contentBackdrop.foreground=glyphLayer
    contentBackdrop.drawForeground={ ink.draw(this,center(),ordered) { drawLayer(glyphLayer) } }
    fun pane():Rect {
        val r=currentRect ?: return Rect.Zero
        val center=center()
        var w=physicalRects.lastOrNull()?.width ?: r.width
        if(physicalRects.isNotEmpty() && center<=physicalRects.first().center.x) w=physicalRects.first().width
        else for(i in 0 until physicalRects.size-1) {
            val a=physicalRects[i];val b=physicalRects[i+1]
            if(center<=b.center.x) { val t=((center-a.center.x)/(b.center.x-a.center.x).coerceAtLeast(1f)).coerceIn(0f,1f);w=a.width+(b.width-a.width)*t;break }
        }
        val v=motion.velocity/10f
        val edge=(kotlin.math.abs(motion.value*width-center)/(r.width*.5f).coerceAtLeast(1f)).coerceIn(0f,1f)
        val sx=(motion.scaleX/(1f-(v*.75f).coerceIn(-.12f,.12f))+edge*.12f).coerceIn(.9f,1.22f)
        val sy=(motion.scaleY*(1f-(v*.25f).coerceIn(-.12f,.12f))-edge*.045f).coerceIn(.9f,1.12f)
        val h=r.height*sy;w*=sx
        return Rect(center-w/2f,r.center.y-h/2f,center+w/2f,r.center.y+h/2f)
    }
    val opticalStrength=LocalGlassOpticalStrength.current.coerceIn(0f,1f)
    val canRefract=sampled && (if(meshMode) bitmap!=null && glyphBitmap!=null && !captureRejected else sourceReady)
    val mask=remember { androidx.compose.ui.graphics.Path() }
    val density=LocalDensity.current
    val dark=LocalGlassDark.current
    val revision=remember(selected,sourceKey,dark,density,rectSnapshot,releaseRevision,sampled) { Any() }
    Box(modifier.liquidGlass(RoundedCornerShape(30.dp),prominent=true,source=source,
        exportedBackdrop=if(sampled) trackBackdrop else null).padding(4.dp)) {
        Box(Modifier.fillMaxWidth().pointerInput(registry,registry.size,rectSnapshot) {
            var startX=0f;var startCenter=0f
            fun endLight() { motion.release();highlight.end();releaseRevision++;requests.trySend(Unit) }
            fun restore() {
                dragging=false;highlight.end()
                val target=registry.rects[currentSelected]
                if(target!=null && registry.size.width>0) motion.animateToValue(target.center.x/registry.size.width)
                else motion.release()
                releaseRevision++;requests.trySend(Unit)
            }
            inspectDragGestures(onPress={down -> motion.press();highlight.begin(down.position) },
                onPressEnd={endLight()},onDragStart={down ->
                startX=down.position.x
                // A grab near either edge of the selected pane keeps a small
                // grip offset. Starting anywhere else on the track acquires
                // the finger immediately instead of dragging a distant pane.
                val selectedPane=registry.rects[currentSelected]
                val oldCenter=motion.value*registry.size.width
                val grip=if(selectedPane?.contains(down.position)==true)
                    (down.position.x-oldCenter).coerceIn(-selectedPane.width*.18f,selectedPane.width*.18f) else 0f
                startCenter=down.position.x-grip;dragging=true
            },onDragEnd={change ->
                val target=glassDragTarget(change.position,registry.size,ordered,startX)
                restore()
                if(target!=null && target!=currentSelected) currentAction(target)
            },onDragCancel={restore()}) { change,_ ->
                if(registry.size.width>0) motion.updateValue((startCenter+change.position.x-startX)/registry.size.width,change.uptimeMillis)
                highlight.move(change.position)
            }
        }) {
            CompositionLocalProvider(LocalSegmentBounds provides registry) {
                Row(Modifier.fillMaxWidth().onGloballyPositioned { registry.row=it;contentBackdrop.coordinates=it;registry.refresh() }
                    .graphicsLayer() // source capture invalidates on real content changes, not finger movement
                    .drawWithContent {
                        if(sampled && contentBackdrop.recordedRevision!==revision && (!motion.isTouching || contentBackdrop.recordedRevision==null)) {
                            contentLayer.record {
                                with(trackBackdrop) { drawBackdrop(this@drawWithContent,registry.row,null) }
                            }
                            glyphLayer.record { this@drawWithContent.drawContent() }
                            contentBackdrop.recordedRevision=revision
                            stats?.let { it.sourceRecords++ }
                            requests.trySend(Unit)
                        }
                        fun paintLabels() { ink.draw(this,center(),ordered) { this@drawWithContent.drawContent() } }
                        if(canRefract && currentRect!=null) {
                            val r=pane();mask.reset();mask.addRoundRect(androidx.compose.ui.geometry.RoundRect(r,r.height/2f,r.height/2f))
                            clipPath(mask,androidx.compose.ui.graphics.ClipOp.Difference) { paintLabels() }
                        } else paintLabels()
                    },content=content)
            }
            if(currentRect!=null && width>0f) {
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.layout { measurable,constraints ->
                        val r=pane()
                        val child=measurable.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1),r.height.roundToInt().coerceAtLeast(1)))
                        layout(constraints.maxWidth,constraints.maxHeight) { child.place(r.left.roundToInt(),r.top.roundToInt()) }
                    }.testTag(puckTag)
                        .squareGlassPuck(if(canRefract && !meshMode) contentBackdrop else null,{motion.pressProgress},
                            { if(highlight.pressProgress>.01f && width>0) highlight.position.x/width else motion.value })
                        .drawWithContent {
                            drawContent()
                            val image=bitmap
                            if(canRefract && meshMode && image!=null) {
                                try {
                                    lens.draw(this,image,pane(),motion.pressProgress,opticalStrength)
                                    glyphBitmap?.let { lens.drawGlyphs(this,it,pane(),motion.pressProgress,opticalStrength,ordered,center(),ink) }
                                    val r=pane()
                                    highlight.drawOverlay(this,Offset(highlight.position.x-r.left,highlight.position.y-r.top))
                                    stats?.let { it.meshDraws++;it.hardwareMesh=lens.hardwareCanvasObserved }
                                } catch (_:RuntimeException) { captureRejected=true;android.util.Log.w("YtdlpG-Glass","Mesh drawing unavailable; compatible glass retained") }
                            }
                        }
                        .then(if(canRefract && meshMode) Modifier else highlight.modifier { val r=pane();Offset(highlight.position.x-r.left,highlight.position.y-r.top) }))
                }
            }
        }
    }
}
