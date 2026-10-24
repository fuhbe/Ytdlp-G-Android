/* Native two-blob renderer: MIT SDF/normal math ported from
 * liquid_glass_widgets 5d2859796ef0f6964e2fdf25962b033eede27814.
 * Copyright 2025 Tim Lehmann; Copyright 2026 Sebastian Degenaar.
 * Android RenderNode/AGSL integration and cache management are local code.
 */
package com.fuhbe.ytdlpg.simple

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

internal class MetaballRenderStats {
    var sourceCaptures=0;var shaderCompilations=0;var effectUpdates=0;var frames=0;var sdfEvaluations=0
    var hardwareCanvas=false;var rejected=false
}
internal val LocalMetaballRenderStats=staticCompositionLocalOf<MetaballRenderStats?> { null }

/** Not the rounded Backdrop lens. This shader evaluates the smooth-union field,
 * derives normals in its neck and samples the REAL cached background input. */
internal const val METABALL_SHADER="""
uniform shader content;
uniform float2 size;
uniform float4 blobA;
uniform float4 blobB;
uniform float2 radii;
uniform float enabledA;
uniform float blend;
uniform float density;
uniform float phase;
uniform float opticalStrength;
layout(color) uniform half4 glassColor;
float rrect(float2 p,float4 b,float r) {
    r=min(r,min(b.z,b.w));
    float2 q=abs(p-b.xy)-b.zw+r;
    return min(max(q.x,q.y),0.0)+length(max(q,0.0))-r;
}
float field(float2 p) {
    float a=enabledA>0.5?rrect(p,blobA,radii.x):100000.0;
    float b=rrect(p,blobB,radii.y);
    float e=max(blend-abs(a-b),0.0);
    return min(a,b)-e*e*0.25/max(blend,0.00001);
}
half4 main(float2 p) {
    float d=field(p);
    if(d>max(1.0,density))return half4(0.0);
    float stepDist=max(0.5,density/3.0);
    float2 gradient=float2(field(p+float2(stepDist,0))-field(p-float2(stepDist,0)),
        field(p+float2(0,stepDist))-field(p-float2(0,stepDist)))/(2.0*stepDist);
    float metric=max(length(gradient),0.1);
    float sd=d/metric;
    float aa=max(0.75,density*0.5);
    float coverage=1.0-smoothstep(-aa,aa,sd);
    if(coverage<0.001)return half4(0.0);
    float thickness=8.0*density;
    float ncos=clamp((thickness+sd)/thickness,0.0,1.0);
    float nsin=sqrt(max(0.0,1.0-ncos*ncos));
    float3 normal=normalize(float3(gradient*ncos,nsin)+float3(0,0,0.0001));
    float height=sqrt(max(0.0,thickness*thickness-pow(max(0.0,thickness+sd),2.0)));
    float3 incident=float3(0,0,-1);
    // Snell's law, as in the reference normal-based render shader.
    float eta=1.0/1.35;
    float dotNI=dot(normal,incident);
    float k=1.0-eta*eta*(1.0-dotNI*dotNI);
    float3 ray=eta*incident-(eta*dotNI+sqrt(max(k,0.0)))*normal;
    float2 displacement=opticalStrength*ray.xy*(height+2.0*density)/max(abs(ray.z),0.12);
    displacement=clamp(displacement,float2(-18.0*density),float2(18.0*density));
    half4 sampled=content.eval(clamp(p+displacement,float2(0),size-float2(1)));
    float3 light=normalize(float3(-0.65+phase*0.35,-0.8,0.9));
    float rim=exp(-abs(sd)/(1.2*density));
    float spec=pow(max(dot(normal,light),0.0),18.0)*0.38;
    half3 rgb=mix(sampled.rgb,glassColor.rgb,glassColor.a);
    rgb=clamp(rgb+half3(rim*(0.18+0.48*max(dot(normal,light),0.0))+spec),half3(0),half3(1));
    return half4(rgb*coverage,coverage);
}
"""

@RequiresApi(33) internal class NativeMetaballEffect(private val stats:MetaballRenderStats?) {
    private val shader=android.graphics.RuntimeShader(METABALL_SHADER).also { stats?.let { s->s.shaderCompilations++ } }
    private var blur:android.graphics.RenderEffect?=null
    private var blurDensity=0f
    fun update(scene:MetaballScene,roi:Rect,density:Float,dark:Boolean,strength:Float=1f):RenderEffect {
        fun set(name:String,blob:SdfBlob) { val r=blob.rect;shader.setFloatUniform(name,r.center.x-roi.left,r.center.y-roi.top,r.width*.5f,r.height*.5f) }
        set("blobA",scene.a);set("blobB",scene.b)
        shader.setFloatUniform("size",roi.width,roi.height);shader.setFloatUniform("radii",scene.a.radius,scene.b.radius)
        shader.setFloatUniform("enabledA",if(scene.a.visible)1f else 0f);shader.setFloatUniform("blend",scene.blend)
        shader.setFloatUniform("opticalStrength",strength.coerceIn(0f,1f));shader.setFloatUniform("density",density);shader.setFloatUniform("phase",scene.state.pathT.coerceIn(0f,1f))
        shader.setColorUniform("glassColor",(if(dark)Color(0xFF223D5C) else Color(0xFFD9F0FF)).copy(alpha=if(dark).18f else .12f).toArgb())
        if(blur==null || density!=blurDensity) { blurDensity=density;blur=android.graphics.RenderEffect.createBlurEffect(2f*density,2f*density,android.graphics.Shader.TileMode.CLAMP) }
        stats?.let { it.effectUpdates++ }
        // Small effect wrapper updated for uniforms; RuntimeShader is NOT recompiled.
        return android.graphics.RenderEffect.createChainEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(shader,"content"),blur!!).asComposeRenderEffect()
    }
}

@Composable internal fun MetaballGlassSurface(modifier:Modifier,roi:Rect,source:Backdrop?,scene:()->MetaballScene) {
    val rendering=LocalGlassRendering.current;val dark=LocalGlassDark.current;val density=LocalDensity.current
    val stats=LocalMetaballRenderStats.current
    val opticalStrength=LocalGlassOpticalStrength.current
    var rejected by remember { mutableStateOf(false) }
    var captureRejected by remember { mutableStateOf(false) }
    val native=remember(rendering) { if(Build.VERSION.SDK_INT>=33 && rendering==GlassRendering.LENS)try { NativeMetaballEffect(stats) }catch(_:RuntimeException){stats?.rejected=true;null}catch(_:LinkageError){stats?.rejected=true;null} else null }
    val layer=rememberGraphicsLayer();var coordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val token=remember(source,roi,rendering,dark) { Any() };val recorded=remember { arrayOfNulls<Any>(1) }
    val contour=remember { MetaballContour() }
    val oldBlur=remember(rendering,density.density) { if(Build.VERSION.SDK_INT>=31 && rendering!=GlassRendering.TINT)android.graphics.RenderEffect.createBlurEffect(3f*density.density,3f*density.density,android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null }
    val effect=if(native!=null && !rejected && Build.VERSION.SDK_INT>=33)Modifier.graphicsLayer {
        try { renderEffect=native.update(scene(),roi,density.density,dark,opticalStrength) }
        catch(_:RuntimeException) { rejected=true;stats?.rejected=true }
        catch(_:LinkageError) { rejected=true;stats?.rejected=true }
    } else Modifier
    Box(modifier.then(effect).onGloballyPositioned { if(coordinates!==it)coordinates=it }.drawWithContent {
        val frame=scene();stats?.let { it.frames++;it.hardwareCanvas=drawContext.canvas.nativeCanvas.isHardwareAccelerated }
        if(rendering!=GlassRendering.TINT && !captureRejected && source!=null && coordinates!=null) {
            if(recorded[0]!==token) {
                try {
                    layer.record { with(source) { drawBackdrop(density,coordinates,null) } }
                    if(native==null || rejected)layer.renderEffect=oldBlur
                    recorded[0]=token;stats?.let { it.sourceCaptures++ }
                } catch(_:RuntimeException) { captureRejected=true;rejected=true;stats?.rejected=true }
                  catch(_:LinkageError) { captureRejected=true;rejected=true;stats?.rejected=true }
                  catch(_:OutOfMemoryError) { captureRejected=true;rejected=true;stats?.rejected=true }
            }
        }
        if(native!=null && !rejected && Build.VERSION.SDK_INT>=33) {
            if(recorded[0]===token)drawLayer(layer)
        } else {
            layer.renderEffect=oldBlur
            val before=contour.evaluations
            val path=contour.update(frame,roi,density.density)
            stats?.let { it.sdfEvaluations+=contour.evaluations-before }
            withTransform({ translate(-roi.left,-roi.top) }) {
                clipPath(path) {
                    withTransform({ translate(roi.left,roi.top) }) { if(recorded[0]===token)drawLayer(layer) }
                    val value=frame.state.sizeT.coerceIn(0f,1f)
                    val opacity=if(rendering==GlassRendering.TINT)(if(dark).46f else .52f)+.34f*glassMorphEase((value-.75f)/.25f) else if(dark).24f else .20f
                    drawRect((if(dark)Color(0xFF142D4B) else Color(0xFFDCEBF5)).copy(alpha=opacity.coerceAtMost(.9f)),topLeft=roi.topLeft,size=roi.size)
                }
                drawPath(path,Brush.linearGradient(listOf(Color.White.copy(alpha=.75f),Color.White.copy(alpha=.12f),Color.White.copy(alpha=.5f))),style=Stroke(1.dp.toPx()))
            }
        }
    })
}
