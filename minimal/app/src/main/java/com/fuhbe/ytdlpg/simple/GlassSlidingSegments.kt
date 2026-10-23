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

@Composable internal fun GlassSlidingSegments(
    selected:Int,count:Int,onSelected:(Int)->Unit,modifier:Modifier=Modifier,
    source:Backdrop?=LocalGlassBackdrop.current,puckTag:String="glass-selection-capsule",
    content:@Composable RowScope.()->Unit
) {
    val registry=remember(count) { SegmentBounds(count) }
    val scope=rememberCoroutineScope()
    val motion=remember(scope) { DampedDragAnimation(scope,.5f) }
    val highlight=remember(scope) { InteractiveHighlight(scope) }
    var dragging by remember { mutableStateOf(false) }
    val currentSelected by rememberUpdatedState(selected)
    val currentAction by rememberUpdatedState(onSelected)
    val sampled=LocalGlassRendering.current!=GlassRendering.TINT
    val trackBackdrop=rememberLayerBackdrop()
    // Square combines the external backdrop and the pre-label track surface.
    // No replica of content, and no capture of this puck into its own source.
    val pressedBackdrop=remember(source,trackBackdrop,motion) { source?.let { outside ->
        object:Backdrop {
            override val isCoordinatesDependent=true
            private val paint=Paint()
            override fun DrawScope.drawBackdrop(density:Density,coordinates:LayoutCoordinates?,layerBlock:(GraphicsLayerScope.()->Unit)?) {
                with(outside) { drawBackdrop(density,coordinates,layerBlock) }
                val p=motion.pressProgress.coerceIn(0f,1f)
                if(p>0f) {
                    paint.alpha=p
                    drawContext.canvas.saveLayer(Rect(Offset.Zero,size),paint)
                    with(trackBackdrop) { drawBackdrop(density,coordinates,layerBlock) }
                    drawContext.canvas.restore()
                }
            }
        }
    } }
    val currentRect=registry.rects[selected]
    val width=registry.size.width.toFloat()
    LaunchedEffect(currentRect,registry.size,selected,dragging) {
        if(!dragging && currentRect!=null && width>0) motion.animateToValue(currentRect.center.x/width)
    }
    // Count/size/layout-direction changes invalidate a gesture rather than
    // allowing a stale release to select a different destination.
    val rectSnapshot=registry.rects.toMap()
    Box(modifier.liquidGlass(RoundedCornerShape(30.dp),prominent=true,source=source,
        exportedBackdrop=if(sampled) trackBackdrop else null,
        interaction={motion.pressProgress},lightPosition={motion.value}).padding(4.dp)) {
        Box(Modifier.fillMaxWidth().pointerInput(registry,registry.size,rectSnapshot) {
            var startX=0f
            var startCenter=0f
            fun restore() {
                dragging=false;highlight.end()
                val target=registry.rects[currentSelected]
                if(target!=null && registry.size.width>0) motion.animateToValue(target.center.x/registry.size.width)
                else motion.release()
            }
            inspectDragGestures(onPress={down -> motion.press();highlight.begin(down.position) },
                onPressEnd={motion.release();highlight.end()},onDragStart={down ->
                startX=down.position.x;startCenter=motion.value*registry.size.width
                dragging=true
            },onDragEnd={change ->
                val target=glassSegmentTarget(change.position,registry.size,registry.rects)
                restore()
                if(target!=null && target!=currentSelected) currentAction(target)
            },onDragCancel={restore()}) { change,_ ->
                if(registry.size.width>0) motion.updateValue((startCenter+change.position.x-startX)/registry.size.width)
                highlight.move(change.position)
            }
        }) {
            if(currentRect!=null && width>0f) {
                val density=LocalDensity.current
                val center=motion.value.coerceIn(0f,1f)*width
                val targetWidth=glassSegmentWidth(center,registry.rects.values)
                val animatedWidth by animateFloatAsState(targetWidth,spring(1f,1000f),label="Square measured width")
                // Square remeasures the growing pane so the native lens samples
                // its real expanded bounds instead of stretching a cached bitmap.
                Box(Modifier.offset { IntOffset((center-animatedWidth/2f).roundToInt(),currentRect.top.roundToInt()) }
                    .width(with(density) { animatedWidth.coerceAtLeast(1f).toDp() })
                    .height(with(density) { currentRect.height.toDp() })
                    .testTag(puckTag)
                    .layout { measurable,constraints ->
                        val velocity=motion.velocity/10f
                        val scaleX=(motion.scaleX/(1f-(velocity*.75f).coerceIn(-.12f,.12f))).coerceIn(.9f,1.15f)
                        val scaleY=(motion.scaleY*(1f-(velocity*.25f).coerceIn(-.12f,.12f))).coerceIn(.9f,1.12f)
                        val w=constraints.maxWidth;val h=constraints.maxHeight
                        val expandedW=(w*scaleX).roundToInt().coerceAtLeast(1)
                        val expandedH=(h*scaleY).roundToInt().coerceAtLeast(1)
                        val child=measurable.measure(Constraints.fixed(expandedW,expandedH))
                        layout(w,h) { child.place((w-expandedW)/2,(h-expandedH)/2) }
                    }
                    .squareGlassPuck(if(sampled) pressedBackdrop else source,{motion.pressProgress},
                        { if(highlight.pressProgress>.01f && width>0) highlight.position.x/width else motion.value })
                    .then(highlight.modifier { Offset(highlight.position.x-center+animatedWidth/2f,highlight.position.y-currentRect.top) }))
            }
            CompositionLocalProvider(LocalSegmentBounds provides registry) {
                Row(Modifier.fillMaxWidth().onGloballyPositioned { registry.row=it;registry.refresh() },content=content)
            }
        }
    }
}
