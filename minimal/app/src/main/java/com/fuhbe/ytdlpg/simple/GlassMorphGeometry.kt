package com.fuhbe.ytdlpg.simple

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.*

internal data class GlassMorphPlacement(val origin:Rect,val panel:Rect,val viewport:Rect,val below:Boolean,val bridgePossible:Boolean)
internal fun glassMorphPlacement(origin:Rect,viewport:Rect,count:Int,density:Float,fontScale:Float,rtl:Boolean):GlassMorphPlacement? {
    if(!listOf(origin.left,origin.top,origin.right,origin.bottom,viewport.left,viewport.top,viewport.right,viewport.bottom,density,fontScale).all { it.isFinite() } ||
        density<=0 || origin.width<=0 || origin.height<=0 || viewport.width<=0 || viewport.height<=0 || count<=0)return null
    if(!origin.overlaps(viewport))return null
    val margin=(8f*density).coerceAtMost(viewport.width*.1f)
    val gap=6f*density
    val availableBelow=(viewport.bottom-margin-origin.bottom-gap).coerceAtLeast(0f)
    val availableAbove=(origin.top-gap-viewport.top-margin).coerceAtLeast(0f)
    val row=max(54f,28f*fontScale+20f)*density
    val desired=min(420f*density,row*count+12f*density)
    val below=availableBelow>=desired || availableBelow>=availableAbove
    val room=if(below)availableBelow else availableAbove
    val width=min((if(fontScale>1.3f) 268f else 232f)*density,(viewport.width-margin*2).coerceAtLeast(1f))
    val bridge=room>=48f*density
    val height=min(desired,if(bridge)room else (viewport.height-margin*2).coerceAtLeast(1f))
    val x=(if(rtl)origin.right-width else origin.left).coerceIn(viewport.left+margin,(viewport.right-margin-width).coerceAtLeast(viewport.left+margin))
    val y=(if(below)origin.bottom+gap else origin.top-gap-height).coerceIn(viewport.top+margin,(viewport.bottom-margin-height).coerceAtLeast(viewport.top+margin))
    return GlassMorphPlacement(origin,Rect(x,y,x+width,y+height),viewport,below,bridge)
}
internal fun glassMorphEase(value:Float):Float { val t=value.coerceIn(0f,1f);return t*t*(3f-2f*t) }
internal data class GlassMorphFrame(val origin:Rect,val panel:Rect,val neckWidth:Float,val connected:Boolean)
internal fun glassMorphFrame(placement:GlassMorphPlacement,progress:Float,density:Float):GlassMorphFrame {
    val p=progress.coerceIn(0f,1f);val e=glassMorphEase((p-.06f)/.76f)
    val o=placement.origin;val target=placement.panel
    val bulge=sin(PI.toFloat()*p)*(if(placement.bridgePossible) 7f*density else 0f)
    val body=if(placement.below)Rect(o.left,o.top,o.right,o.bottom+bulge) else Rect(o.left,o.top-bulge,o.right,o.bottom)
    val width=(o.width*.42f+(target.width-o.width*.42f)*e).coerceAtMost(placement.viewport.width)
    val height=(target.height*e).coerceAtLeast(.1f)
    val x=(target.center.x-width*.5f).coerceIn(placement.viewport.left,(placement.viewport.right-width).coerceAtLeast(placement.viewport.left))
    val away=sin(PI.toFloat()*e)*24f*density
    val near=if(placement.below) max(body.bottom+2f*density,target.top+away) else min(body.top-2f*density,target.bottom-away)
    val y=(if(placement.below)near else near-height).coerceIn(placement.viewport.top,(placement.viewport.bottom-height).coerceAtLeast(placement.viewport.top))
    val panel=Rect(x,y,x+width,y+height)
    val neck=18f*sin(PI.toFloat()*(p/.90f).coerceIn(0f,1f)).coerceAtLeast(0f)*density
    return GlassMorphFrame(body,panel,neck,placement.bridgePossible && p>.025f && p<.90f)
}
/** Actual silhouette topology: two rounded regions joined by an hourglass neck.
 * Path union removes internal seams; it never crossfades unrelated rectangles. */
internal fun glassMorphPath(placement:GlassMorphPlacement,progress:Float,density:Float):Path {
    val p=progress.coerceIn(0f,1f);val f=glassMorphFrame(placement,p,density)
    val origin=Path().apply { addRoundRect(RoundRect(f.origin,18f*density,18f*density)) }
    if(p<.025f)return origin
    if(!placement.bridgePossible) {
        val e=glassMorphEase(p)
        val a=placement.origin;val b=placement.panel
        val rect=Rect(a.left+(b.left-a.left)*e,a.top+(b.top-a.top)*e,a.right+(b.right-a.right)*e,a.bottom+(b.bottom-a.bottom)*e)
        return Path().apply { addRoundRect(RoundRect(rect,20f*density,20f*density)) }
    }
    val panel=Path().apply { addRoundRect(RoundRect(f.panel,min(24f*density,f.panel.height*.45f),min(24f*density,f.panel.height*.45f))) }
    var result=Path.combine(PathOperation.Union,origin,panel)
    if(f.connected) {
        val oy=if(placement.below)f.origin.bottom-2f*density else f.origin.top+2f*density
        val py=if(placement.below)f.panel.top+min(3f*density,f.panel.height*.2f) else f.panel.bottom-min(3f*density,f.panel.height*.2f)
        val shoulderInset=min(20f*density,f.origin.width*.4f)
        val center=f.panel.center.x.coerceIn(f.origin.left+shoulderInset,f.origin.right-shoulderInset)
        val target=f.panel.center.x;val half=f.neckWidth*.5f
        val shoulder=min(half*2f,min(f.origin.width*.22f,f.panel.width*.22f))
        val middle=(oy+py)*.5f
        val bridge=Path().apply {
            moveTo(center-shoulder,oy)
            cubicTo(center-half,middle,target-half,middle,target-shoulder,py)
            lineTo(target+shoulder,py)
            cubicTo(target+half,middle,center+half,middle,center+shoulder,oy)
            close()
        }
        result=Path.combine(PathOperation.Union,result,bridge)
    }
    return result
}
