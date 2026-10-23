package com.fuhbe.ytdlpg.simple

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Physical order, not logical index: RTL and differently sized destinations
 * share exactly the same interpolation. No model selection is changed here. */
internal fun glassIllumination(center:Float,index:Int,bounds:List<Pair<Int,Rect>>):Float {
    if(bounds.isEmpty() || !center.isFinite()) return 0f
    if(center<=bounds.first().second.center.x) return if(index==bounds.first().first) 1f else 0f
    for(i in 0 until bounds.lastIndex) {
        val a=bounds[i];val b=bounds[i+1]
        if(center<=b.second.center.x) {
            val t=((center-a.second.center.x)/(b.second.center.x-a.second.center.x).coerceAtLeast(1f)).coerceIn(0f,1f)
            return when(index) { a.first->1f-t;b.first->t;else->0f }
        }
    }
    return if(index==bounds.last().first) 1f else 0f
}

/** Directional 38% boundary (rather than requiring the destination center).
 * Cancelling outside the vertical track remains cancellation, never a click. */
internal fun glassDragTarget(point:Offset,size:androidx.compose.ui.unit.IntSize,
    bounds:List<Pair<Int,Rect>>,origin:Float):Int? {
    if(!point.x.isFinite() || !point.y.isFinite() || point.y<0f || point.y>=size.height ||
        point.x<0f || point.x>=size.width || bounds.isEmpty()) return null
    val direction=sign(point.x-origin)
    var probe=point.x
    for(i in 0 until bounds.lastIndex) {
        val a=bounds[i].second.center.x;val b=bounds[i+1].second.center.x
        if(point.x in a..b) { probe+=direction*(b-a)*.12f;break }
    }
    return bounds.minByOrNull { abs(it.second.center.x-probe) }?.first
}

/** Bounded rubber band: extra edge travel affects the material, not navigation. */
internal fun glassRubberCenter(raw:Float,first:Float,last:Float,width:Float):Float {
    val edge=raw.coerceIn(first,last);val delta=raw-edge
    val limit=(width*.18f).coerceAtLeast(1f)
    return edge+delta/(1f+abs(delta)/limit)*.45f
}

/** Precompute tint filters once per theme, never allocate filters per sample.
 * The cached glyph layer stays neutral; tint is applied while drawing, also
 * inside the native lens / Android 12 mesh. No duplicate composable labels. */
internal class GlassSegmentInk(inactive:Color,active:Color) {
    private val colors=Array(65) { lerp(inactive,active,it/64f) }
    private val paints=Array(65) { Paint().apply { colorFilter=ColorFilter.tint(colors[it]) } }
    private val native=Array(65) { android.graphics.PorterDuffColorFilter(colors[it].toArgb(),android.graphics.PorterDuff.Mode.SRC_IN) }
    fun nativeFilter(weight:Float)=native[(weight.coerceIn(0f,1f)*64).roundToInt()]
    fun draw(scope:DrawScope,center:Float,bounds:List<Pair<Int,Rect>>,content:DrawScope.()->Unit)=with(scope) {
        for((index,r) in bounds) clipRect(r.left,r.top,r.right,r.bottom) {
            val paint=paints[(glassIllumination(center,index,bounds)*64).roundToInt()]
            // Bounds belong to the source row, even when the draw scope is a
            // smaller translated lens sampling a destination at the far end.
            drawContext.canvas.saveLayer(r,paint)
            try { content() } finally { drawContext.canvas.restore() }
        }
    }
}
