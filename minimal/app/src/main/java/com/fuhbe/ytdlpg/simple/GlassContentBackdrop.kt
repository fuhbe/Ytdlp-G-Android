// Coordinate translation adapted from Backdrop 1.0.3 LayerBackdrop,
// Copyright 2025 Kyant, Apache-2.0 (license and exact source JAR bundled).
package com.fuhbe.ytdlpg.simple

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/** Explicit content revision cache: neither pointer position, deformation nor
 * a new destination canvas causes another source recording/readback. */
internal class GlassContentBackdrop(val layer:GraphicsLayer):Backdrop {
    override val isCoordinatesDependent=true
    var coordinates:LayoutCoordinates?=null
    var recordedRevision:Any?=null
    var foreground:GraphicsLayer?=null
    var drawForeground:(DrawScope.()->Unit)?=null
    override fun DrawScope.drawBackdrop(density:Density,coordinates:LayoutCoordinates?,layerBlock:(GraphicsLayerScope.()->Unit)?) {
        val origin=this@GlassContentBackdrop.coordinates ?: return
        val target=coordinates ?: return
        // A focusable quality Popup is another window/Compose owner. Its
        // window-local (0,0) differs from the activity's. Screen coordinates
        // are the common space; localPositionOf alone can throw across owners.
        val offset=try { origin.localPositionOf(target) }
            catch (_:IllegalStateException) { target.localToScreen(Offset.Zero)-origin.localToScreen(Offset.Zero) }
            catch (_:IllegalArgumentException) { target.localToScreen(Offset.Zero)-origin.localToScreen(Offset.Zero) }
        if(!offset.x.isFinite() || !offset.y.isFinite()) return
        // These controls use physical pane bounds, with no layerBlock transform.
        withTransform({ translate(-offset.x,-offset.y) }) {
            drawLayer(layer)
            drawForeground?.invoke(this)
        }
    }
}
