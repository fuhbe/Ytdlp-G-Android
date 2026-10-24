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
/** Literal Square LiquidButton matrix, followed by a separate bounded long-pull
 * extension. Applied to decorative rendering, never the semantic/hit rectangle. */
internal fun squareButtonTransform(size:Size,offset:Offset,pressure:Float,density:Float):SquareButtonTransform {
    if(size.minDimension<=0 || !offset.x.isFinite() || !offset.y.isFinite())return SquareButtonTransform(Offset.Zero,1f,1f)
    val width=size.width;val height=size.height
    val scale=1f+4f*density/height*pressure.coerceIn(0f,1f)
    val maxOffset=size.minDimension;val initialDerivative=.05f
    val x=maxOffset*tanh(initialDerivative*offset.x/maxOffset)
    val y=maxOffset*tanh(initialDerivative*offset.y/maxOffset)
    val maxDragScale=4f*density/height;val angle=atan2(offset.y,offset.x)
    val sx=scale+maxDragScale*abs(cos(angle)*offset.x/size.maxDimension)*min(width/height,1f)
    val sy=scale+maxDragScale*abs(sin(angle)*offset.y/size.maxDimension)*min(height/width,1f)
    // Extension only: Square's 0.05 derivative is intentionally restrained.
    // Retain it and add smooth finite travel, rather than a hand-made outline.
    val reach=min(maxOffset*.8f,48f*density)
    val extraX=reach*tanh(.20f*offset.x/maxOffset)
    val extraY=reach*tanh(.20f*offset.y/maxOffset)
    return SquareButtonTransform(Offset(x+extraX,y+extraY),sx.coerceIn(.9f,1.48f),sy.coerceIn(.9f,1.38f))
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
