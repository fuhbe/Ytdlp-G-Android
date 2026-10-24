/* Technical Kotlin port of liquid_glass_widgets LiquidMorphPhysics.
 * MIT, Copyright 2024–2026 Sebastian Degenaar; engine/shader portions
 * Copyright 2025 Tim Lehmann for whynotmake.it. Exact pinned sources:
 * minimal/vendor/liquid-morph-067-test2/ (5d2859796ef0f6964e2fdf25962b033eede27814).
 * Native adaptations: velocity-continuous interruption, viewport containment,
 * coincident-blob blend taper and settle-time handoff. See provenance report.
 */
package com.fuhbe.ytdlpg.simple

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.*

internal enum class MorphPhase { IDLE, DETACHING, TRAVELLING, ARRIVING, SETTLED }
internal data class LiquidMorphState(
    val pathT:Float,val sizeT:Float,val currentDx:Float,val currentDy:Float,
    val pushDx:Float,val pushDy:Float,val anchorScale:Float,val blend:Float,
    val containerScale:Float,val phase:MorphPhase
)
internal object LiquidMorphPhysics {
    const val OPEN_STIFFNESS=120f
    val OPEN_DAMPING_RATIO=16f/(2f*sqrt(120f))
    const val CLOSE_STIFFNESS=200f
    val CLOSE_DAMPING_RATIO=21f/(2f*sqrt(200f))
    const val CLOSE_VELOCITY=-3.2f // Code differs from the older prose's -2.5.
    private val linearToEaseOut=CubicBezierEasing(.35f,.91f,.33f,.97f)
    private val easeOut=CubicBezierEasing(0f,0f,.58f,1f)
    fun adaptiveAmplitude(dy:Float)=if(abs(dy)<=100f)2.5f else (250f/abs(dy)).coerceIn(.7f,2.5f)
    fun blendAttenuation(scale:Float)=if(scale<=3f)1f else (3f/scale).coerceIn(.2f,1f)
    fun compute(raw:Float,dx:Float,dy:Float,scaleDelta:Float=1f,closing:Boolean=false):LiquidMorphState {
        val value=if(raw.isFinite())raw else 0f
        val t=value.coerceIn(0f,1f);val under=min(value,0f);val adaptive=scaleDelta>1.5f
        val amplitude=if(adaptive)adaptiveAmplitude(dy) else 2.5f
        val q=t-1f
        val path=if(closing)t+under else q*q*((amplitude+1f)*q+amplitude)+1f+under
        val size=(if(closing)t.pow(1.4f) else if(adaptive && scaleDelta>3f)t*t*t*(t*(t*6f-15f)+10f) else linearToEaseOut.transform(t))+under
        val travel=hypot(dx,dy);val push=if(adaptive && travel>0)(80f/travel).coerceIn(0f,1f) else 1f
        val anchor=if(closing)(1f-(t-.45f)/.40f).coerceIn(0f,1f) else (1f-t/.4f).coerceIn(0f,1f)
        val blend=if(closing)easeOut.transform((1f-t/.6f).coerceIn(0f,1f))*28f*blendAttenuation(scaleDelta)
            else (abs(path-size)*150f*blendAttenuation(scaleDelta)).coerceIn(0f,28f)
        val phase=when { value<0f->MorphPhase.DETACHING;t<.001f->MorphPhase.IDLE;t<.4f->MorphPhase.DETACHING;t<.8f->MorphPhase.TRAVELLING;t<.999f->MorphPhase.ARRIVING;else->MorphPhase.SETTLED }
        return LiquidMorphState(path,size,dx*path,dy*path,dx*under*push,dy*under*push,anchor,blend.coerceIn(0f,28f),
            if(value>1f)1f+(value-1f)*.1f else if(value<0f)1f+value*.55f else 1f,phase)
    }
}
