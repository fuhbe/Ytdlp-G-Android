/* Adapted visual recipes from Square's LiquidBottomTabs / GlassEffect,
 * Square 17ed2b87974cf6fec9c531ef3f1438448bb2b729 (GPL-3.0),
 * Backdrop catalog components Copyright 2025 Kyant (Apache-2.0).
 * Original sources, licenses and local changes: minimal/vendor/square/.
 */
package com.fuhbe.ytdlpg.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShaderCache
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** Square's native directional specular, with a plain rim on rejected drivers.
 * The dependency caches the shader; this wrapper never compiles one per frame. */
private class SafeSquareHighlight(private val angle: Float, private val reject: () -> Unit) : HighlightStyle {
    override val color = Color.White.copy(alpha=.65f)
    override val blendMode = BlendMode.Plus
    override fun DrawScope.createShader(shape: Shape, runtimeShaderCache: RuntimeShaderCache): Shader? =
        try { with(HighlightStyle.Default(color=color, angle=angle)) { createShader(shape, runtimeShaderCache) } }
        catch (_: RuntimeException) { reject(); null }
        catch (_: LinkageError) { reject(); null }
}

/** Genuine backdrop lens, not an image of glass. The cached real control
 * layer includes glyphs; the original is masked under the lens to avoid doubles. */
@Composable internal fun Modifier.squareGlassPuck(
    source: Backdrop?, pressure: () -> Float, light: () -> Float
): Modifier {
    val opticalStrength=LocalGlassOpticalStrength.current.coerceIn(0f,1f)
    val dark=LocalGlassDark.current
    val rendering=LocalGlassRendering.current
    var rejected by remember { mutableStateOf(false) }
    val shape=remember { RoundedCornerShape(percent=50) }
    val reject:()->Unit=remember { { if(!rejected) { rejected=true;android.util.Log.w("YtdlpG-Glass","Square lens/rim unavailable; using blur fallback") } } }
    val film=if(dark) Color(0xFFE4F3FF).copy(alpha=.12f) else Color.White.copy(alpha=.22f)
    val coating:DrawScope.()->Unit={
        val p=pressure().coerceIn(0f,1f)
        drawRect(film.copy(alpha=film.alpha*(1f-p*.4f)))
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=if(dark) .13f else .22f),Color.Transparent,
            Color.White.copy(alpha=.05f))))
    }
    val material=if(source!=null && rendering!=GlassRendering.TINT) Modifier.drawBackdrop(
        backdrop=source,shape={shape},effects={
            val p=pressure().coerceIn(0f,1f)
            // LiquidBottomTabs clears the lens as it is pressed. Keep a little
            // frosting at rest, bounded refraction reach as in GlassEffect.
            // The source now includes real glyphs. Keep the optical center crisp.
            // The static shell provides frosting without reblurring text per frame.
            if(rendering==GlassRendering.LENS && !rejected && size.minDimension>24f) {
                val reach=size.minDimension*.45f
                try { lens((7f+3f*p).dp.toPx().coerceAtMost(reach)*opticalStrength,
                    (9f+5f*p).dp.toPx().coerceAtMost(reach)*opticalStrength,depthEffect=true,chromaticAberration=false) }
                catch (_:RuntimeException) { reject() }
                catch (_:LinkageError) { reject() }
            }
        },highlight={
            Highlight(width=.8f.dp,alpha=.68f+pressure().coerceIn(0f,1f)*.25f,
                style=if(rendering==GlassRendering.LENS && !rejected) SafeSquareHighlight(25f+40f*light().coerceIn(0f,1f),reject)
                else HighlightStyle.Plain(color=Color.White.copy(alpha=.6f)))
        },shadow={Shadow(radius=5.dp,alpha=.4f+pressure().coerceIn(0f,1f)*.4f)},
        innerShadow={InnerShadow(radius=4.dp,alpha=pressure().coerceIn(0f,1f)*.5f)},onDrawSurface=coating
    ) else Modifier.shadow(3.dp,shape).clip(shape).drawWithContent { coating();drawContent() }
    return then(material).clip(shape).drawWithContent {
        drawContent()
        // The same finite, finger-driven rim remains usable without AGSL.
        drawOutline(shape.createOutline(size,layoutDirection,this),
            Brush.radialGradient(listOf(Color.White.copy(alpha=.9f),Color.White.copy(alpha=.12f),Color.White.copy(alpha=.4f)),
                center=Offset(size.width*light().coerceIn(0f,1f),-size.height*.2f),radius=size.width*.8f),
            style=Stroke((.7f+pressure().coerceIn(0f,1f)*.3f).dp.toPx()))
    }
}
