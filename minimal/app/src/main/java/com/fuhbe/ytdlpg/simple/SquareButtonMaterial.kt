/* Square LiquidButton/InteractiveHighlight + GlassEffect adaptation.
 * Square 5df51684eec348e283126836449d9ac4aa62541e; GPL-3.0.
 * Catalog parts Copyright 2025 Kyant, Apache-2.0. Exact originals/notices:
 * minimal/vendor/square-067-test2/. No selector or download implementation.
 */
package com.fuhbe.ytdlpg.simple

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.fuhbe.ytdlpg.simple.buttonsquare.shapes.ContinuousCapsule
import kotlin.math.*

internal data class SquareButtonTransform(val translation:Offset,val scaleX:Float,val scaleY:Float)
/** Fixed semantic/layout anchor. Keep Square's single G2 surface and pressure
 * response, but constrain long pulls to a local extension with tanh resistance.
 * The opposite edge is invariant throughout a directional pull. Labels never
 * use this matrix. No spring/coroutine is allocated for pointer samples. */
internal fun squareButtonTransform(size:Size,offset:Offset,pressure:Float,density:Float):SquareButtonTransform {
    if(size.minDimension<=0 || !offset.x.isFinite() || !offset.y.isFinite())return SquareButtonTransform(Offset.Zero,1f,1f)
    val unit=density.coerceAtLeast(.01f)
    val press=pressure.coerceIn(0f,1.1f)*min(unit,size.minDimension*.018f)
    val reach=min(2f*unit,size.minDimension*.04f)
    val resistance=(size.minDimension*1.25f).coerceAtLeast(unit)
    val stretchX=reach*tanh(abs(offset.x)/resistance)
    val stretchY=reach*tanh(abs(offset.y)/resistance)
    // Positive pulls hold the left/top edge; negative pulls hold right/bottom.
    // Maximum silhouette overscan is 2.55dp, independent of screen/drag length.
    return SquareButtonTransform(Offset(sign(offset.x)*stretchX*.5f,sign(offset.y)*stretchY*.5f),
        1f+(press+stretchX)/size.width,1f+(-press+stretchY)/size.height)
}

@RequiresApi(33) internal class SquareButtonHighlightShader {
    // Original InteractiveHighlight radial shader, compiled once per instance.
    private val shader=android.graphics.RuntimeShader("""
        uniform float2 size;
        layout(color) uniform half4 color;
        uniform float radius;
        uniform float2 position;
        half4 main(float2 coord) {
            float dist=distance(coord,position);
            float intensity=smoothstep(radius,radius*0.5,dist);
            return color*intensity;
        }
    """.trimIndent())
    private val brush=ShaderBrush(shader)
    fun paint(scope:DrawScope,point:Offset,progress:Float) = with(scope) {
        shader.setFloatUniform("size",size.width,size.height)
        shader.setColorUniform("color",Color.White.copy(alpha=.15f*progress).toArgb())
        shader.setFloatUniform("radius",size.minDimension*1.5f)
        shader.setFloatUniform("position",point.x.coerceIn(0f,size.width),point.y.coerceIn(0f,size.height))
        drawRect(brush,blendMode=BlendMode.Plus)
    }
}

internal class SquareButtonOptics { var attempted=false;var shader:Any?=null;var materialRecords=0 }
@Composable internal fun Modifier.squareButtonMaterial(motion:ElasticButtonMotion,emphasized:Boolean=false,optics:SquareButtonOptics?=null):Modifier {
    val shape=remember { ContinuousCapsule() }
    val source=LocalGlassBackdrop.current;val rendering=LocalGlassRendering.current;val dark=LocalGlassDark.current
    var rejected by remember { mutableStateOf(false) }
    val film=if(dark)Color(0xFF83A3C6).copy(alpha=.17f) else Color.White.copy(alpha=.23f)
    val wash=if(emphasized)Color(0xFF1479E8).copy(alpha=.13f) else Color.Transparent
    val material=if(source!=null && rendering!=GlassRendering.TINT) Modifier.drawBackdrop(backdrop=source,shape={shape},
        effects={
            blur(2.dp.toPx()) // Square GlassEffect default, not the card's 7dp frost.
            if(rendering==GlassRendering.LENS && !rejected)try {
                val reach=size.minDimension*.45f
                lens((.4f*48f).dp.toPx().coerceAtMost(reach),(.6f*48f).dp.toPx().coerceAtMost(reach),depthEffect=false,chromaticAberration=false)
            }catch(_:RuntimeException){rejected=true}catch(_:LinkageError){rejected=true}
        },highlight={Highlight(width=1.dp,alpha=.65f,style=HighlightStyle.Plain())},shadow={Shadow(radius=5.dp,alpha=.35f)},onDrawSurface={drawRect(film);drawRect(wash)})
        else Modifier.shadow(4.dp,shape).drawBehind { drawOutline(shape.createOutline(size,layoutDirection,this),film);drawOutline(shape.createOutline(size,layoutDirection,this),wash) }
    val optical=optics ?: remember { SquareButtonOptics() }
    val highlight=remember(optical,rendering) {
        if(Build.VERSION.SDK_INT>=33 && rendering==GlassRendering.LENS) {
            if(!optical.attempted) { optical.attempted=true;optical.shader=try { SquareButtonHighlightShader() }catch(_:RuntimeException){null}catch(_:LinkageError){null} }
            optical.shader as? SquareButtonHighlightShader
        } else null
    }
    val cached=rememberGraphicsLayer()
    val cacheKey=remember(source,rendering,dark,emphasized) { Any() }
    val recorded=remember { arrayOfNulls<Any>(1) }
    var point by remember { mutableStateOf(Offset.Zero) }
    val savedPoint=remember { arrayOf(Offset.Zero) }
    val savedSize=remember { arrayOf(Size.Zero) }
    return onGloballyPositioned { val p=it.positionInWindow();if(point!=p)point=p }.clip(shape).drawWithContent {
        drawContent()
        val p=motion.level.coerceIn(0f,1f)
        if(p>0f) {
            drawRect(Color.White.copy(alpha=.08f*p),blendMode=BlendMode.Plus)
            if(highlight!=null && Build.VERSION.SDK_INT>=33)try { highlight.paint(this,motion.light,p) }catch(_:RuntimeException){}catch(_:LinkageError){}
            else drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha=.15f*p),Color.Transparent),
                center=Offset(motion.light.x.coerceIn(0f,size.width),motion.light.y.coerceIn(0f,size.height)),radius=size.minDimension*1.5f),blendMode=BlendMode.Plus)
        }
        drawOutline(shape.createOutline(size,layoutDirection,this),Color.White.copy(alpha=.48f+.2f*p),style=Stroke(1.dp.toPx()))
    }.drawWithContent {
        val held=motion.touching || !motion.settled
        if(held) {
            if(recorded[0]!==cacheKey || savedSize[0]!=size || savedPoint[0]!=point) {
                cached.record { this@drawWithContent.drawContent() }
                recorded[0]=cacheKey;savedPoint[0]=point;savedSize[0]=size;optical.materialRecords++
            }
            drawLayer(cached)
        } else {
            // Ordinary idle drawing follows asynchronous wallpaper/content
            // updates. Freeze only the active press/return, not the cold frame.
            recorded[0]=null;drawContent()
        }
    }.then(material)
}
