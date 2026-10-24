package com.fuhbe.ytdlpg.simple

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.*

internal data class GlassMorphPlacement(val origin:Rect,val panel:Rect,val viewport:Rect,val below:Boolean,val bridgePossible:Boolean)
internal fun glassMorphPlacement(origin:Rect,viewport:Rect,count:Int,density:Float,fontScale:Float,rtl:Boolean):GlassMorphPlacement? {
    if(!listOf(origin.left,origin.top,origin.right,origin.bottom,viewport.left,viewport.top,viewport.right,viewport.bottom,density,fontScale).all { it.isFinite() } ||
        density<=0 || origin.width<=0 || origin.height<=0 || viewport.width<=0 || viewport.height<=0 || count<=0)return null
    if(!origin.overlaps(viewport))return null
    val margin=(8f*density).coerceAtMost(viewport.width*.1f)
    val gap=6f*density
    val availableBelow=(viewport.bottom-margin-origin.top).coerceAtLeast(0f)
    val availableAbove=(origin.bottom-viewport.top-margin).coerceAtLeast(0f)
    val row=max(54f,28f*fontScale+20f)*density
    val desired=min(420f*density,row*count+12f*density)
    val below=availableBelow>=desired || availableBelow>=availableAbove
    val room=if(below)availableBelow else availableAbove
    val width=min((if(fontScale>1.3f) 268f else 232f)*density,(viewport.width-margin*2).coerceAtLeast(1f))
    val bridge=(room-origin.height)>=48f*density
    val height=min(desired,if(bridge)room else (viewport.height-margin*2).coerceAtLeast(1f))
    val x=(if(rtl)origin.right-width else origin.left).coerceIn(viewport.left+margin,(viewport.right-margin-width).coerceAtLeast(viewport.left+margin))
    val y=(if(below)origin.top else origin.bottom-height).coerceIn(viewport.top+margin,(viewport.bottom-margin-height).coerceAtLeast(viewport.top+margin))
    return GlassMorphPlacement(origin,Rect(x,y,x+width,y+height),viewport,below,bridge)
}
internal fun glassMorphEase(value:Float):Float { val t=value.coerceIn(0f,1f);return t*t*(3f-2f*t) }
internal data class GlassMorphFrame(val origin:Rect,val panel:Rect,val neckWidth:Float,val connected:Boolean)
internal fun glassMorphFrame(placement:GlassMorphPlacement,progress:Float,density:Float):GlassMorphFrame {
    val scene=metaballScene(placement,progress,density)
    return GlassMorphFrame(scene.a.rect,scene.b.rect,scene.blend,scene.a.visible && scene.blend>0f)
}
/** The compatibility path is sampled from the SAME SDF used by AGSL. */
internal fun glassMorphPath(placement:GlassMorphPlacement,progress:Float,density:Float):Path {
    if(progress==0f)return Path().apply { addRoundRect(RoundRect(placement.origin,18f*density,18f*density)) }
    return MetaballContour().update(metaballScene(placement,progress,density),placement.viewport,density)
}
