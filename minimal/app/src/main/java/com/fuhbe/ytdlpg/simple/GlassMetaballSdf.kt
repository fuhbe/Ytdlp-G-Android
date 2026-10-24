/* SDF rounded boxes + smoothUnion ported from liquid_glass_widgets/shaders/sdf.glsl
 * MIT Copyright 2025 Tim Lehmann; Copyright 2026 Sebastian Degenaar.
 * Marching-square contour/caching and viewport-safe geometry are local code.
 * The neck is the zero isoline of smooth-min, never an explicit Bezier bridge.
 */
package com.fuhbe.ytdlpg.simple

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import kotlin.math.*

internal data class SdfBlob(val rect:Rect,val radius:Float,val visible:Boolean=true)
internal data class MetaballScene(val a:SdfBlob,val b:SdfBlob,val blend:Float,val state:LiquidMorphState) {
    fun distance(x:Float,y:Float)=smoothUnion(blobDistance(x,y,a),blobDistance(x,y,b),blend)
}
internal fun blobDistance(x:Float,y:Float,blob:SdfBlob):Float {
    if(!blob.visible || blob.rect.width<.01f || blob.rect.height<.01f)return 100000f
    val b=blob.rect;val radius=blob.radius.coerceIn(0f,b.minDimension/2f)
    val qx=abs(x-b.center.x)-b.width*.5f+radius;val qy=abs(y-b.center.y)-b.height*.5f+radius
    return min(max(qx,qy),0f)+hypot(max(qx,0f),max(qy,0f))-radius
}
internal fun smoothUnion(a:Float,b:Float,k:Float):Float {
    val e=max(k-abs(a-b),0f)
    return min(a,b)-e*e*.25f/max(k,.00001f)
}
private fun fitBlob(rect:Rect,viewport:Rect):Rect {
    val w=rect.width.coerceIn(.1f,viewport.width);val h=rect.height.coerceIn(.1f,viewport.height)
    val x=rect.left.coerceIn(viewport.left,viewport.right-w);val y=rect.top.coerceIn(viewport.top,viewport.bottom-h)
    return Rect(x,y,x+w,y+h)
}
internal fun metaballScene(p:GlassMorphPlacement,raw:Float,density:Float,closing:Boolean=false):MetaballScene {
    val o=p.origin;val dest=p.panel
    val ratio=sqrt(dest.width*dest.height/(o.width*o.height))
    val state=LiquidMorphPhysics.compute(raw,(dest.center.x-o.center.x)/density,(dest.center.y-o.center.y)/density,ratio,closing)
    val aSize=Size(o.width*state.anchorScale,o.height*state.anchorScale)
    val ac=o.center+Offset(state.pushDx*density,state.pushDy*density)
    val a=Rect(ac-aSize.center, aSize)
    val t=state.sizeT
    val w=if(t<0f)o.width*(1+t*.25f) else o.width+(dest.width-o.width)*t
    val h=if(t<0f)o.height*(1+t*.25f) else o.height+(dest.height-o.height)*t
    val scale=state.containerScale.coerceIn(.8f,1.08f)
    val bc=o.center+Offset(state.currentDx*density,state.currentDy*density)
    val size=Size(max(.1f,w*scale),max(.1f,h*scale))
    val b=fitBlob(Rect(bc-size.center,size),p.viewport)
    val radiusT=if(closing)1f else if(t<=0f)0f else (2f.pow(10f*(t.coerceIn(0f,1f)-1f))).coerceIn(0f,1f)
    val startRadius=b.minDimension*.5f
    val expansion=glassMorphEase((raw.coerceIn(0f,1f)/.1f).coerceIn(0f,1f))
    val sourceRadius=18f*density
    val radius=if(closing)sourceRadius+(startRadius-sourceRadius)*expansion else
        sourceRadius+(startRadius-sourceRadius)*expansion+(24f*density-startRadius)*radiusT
    // Exact coincidence must land on the original outline, not smooth-min's
    // -k/4 inflation. Taper blend only inside the final containment zone.
    val separation=(bc-ac).getDistance()
    val coincidence=(separation/(o.minDimension*.35f).coerceAtLeast(1f)).coerceIn(0f,1f)
    val blend=state.blend*density*coincidence
    return MetaballScene(SdfBlob(a,18f*density*state.anchorScale,state.anchorScale>.001f),SdfBlob(b,radius.coerceIn(0f,b.minDimension*.5f)),blend,state)
}

/** Geometry compensation is captured once per direction/geometry change. It
 * decays along the current spring, preserving the displayed shape on reversal. */
internal class MetaballResolver {
    private var previous:MetaballScene?=null
    private var closing=false
    private var placement:GlassMorphPlacement?=null
    private var startRaw=0f
    private var correction:Pair<MetaballScene,MetaballScene>?=null
    fun resolve(p:GlassMorphPlacement,raw:Float,density:Float,isClosing:Boolean):MetaballScene {
        val base=metaballScene(p,raw,density,isClosing)
        if(previous!=null && (closing!=isClosing || placement!=p)) { correction=previous!! to base;startRaw=raw }
        closing=isClosing;placement=p
        val saved=correction
        var result=base
        if(saved!=null) {
            val target=if(isClosing)0f else 1f
            val t=(abs(raw-startRaw)/max(abs(target-startRaw),.01f)).coerceIn(0f,1f)
            val amount=1f-glassMorphEase(t)
            fun blob(now:SdfBlob,old:SdfBlob,origin:SdfBlob):SdfBlob {
                val n=now.rect;val a=old.rect;val b=origin.rect
                val rect=Rect(n.left+(a.left-b.left)*amount,n.top+(a.top-b.top)*amount,n.right+(a.right-b.right)*amount,n.bottom+(a.bottom-b.bottom)*amount)
                return SdfBlob(fitBlob(rect,p.viewport),(now.radius+(old.radius-origin.radius)*amount).coerceAtLeast(0f),now.visible || (old.visible && amount>.001f))
            }
            result=base.copy(a=blob(base.a,saved.first.a,saved.second.a),b=blob(base.b,saved.first.b,saved.second.b),blend=max(0f,base.blend+(saved.first.blend-saved.second.blend)*amount))
            if(t>=1f)correction=null
        }
        previous=result;return result
    }
}

/** Shared fallback contour. Allocates bounded arrays on viewport changes only;
 * the SDF is evaluated on a <=40k-cell grid, independent of bitmap captures. */
internal class MetaballContour {
    val path=Path()
    var evaluations=0;private set
    private var key:Triple<MetaballScene,Rect,Float>?=null
    private var values=FloatArray(0)
    private var pointX=FloatArray(0);private var pointY=FloatArray(0)
    private var linkA=IntArray(0);private var linkB=IntArray(0);private var used=BooleanArray(0)
    fun update(scene:MetaballScene,bounds:Rect,density:Float):Path {
        val next=Triple(scene,bounds,density);if(key==next)return path;key=next;path.reset()
        val step=max(2f*density,sqrt(bounds.width*bounds.height/40000f))
        val nx=max(1,ceil(bounds.width/step).toInt());val ny=max(1,ceil(bounds.height/step).toInt())
        val count=(nx+1)*(ny+1);if(values.size!=count)values=FloatArray(count)
        val horizontal=nx*(ny+1);val total=horizontal+(nx+1)*ny
        if(linkA.size!=total) { pointX=FloatArray(total);pointY=FloatArray(total);linkA=IntArray(total);linkB=IntArray(total);used=BooleanArray(total) }
        linkA.fill(-1);linkB.fill(-1);used.fill(false)
        val dx=bounds.width/nx;val dy=bounds.height/ny
        for(y in 0..ny)for(x in 0..nx) { values[y*(nx+1)+x]=scene.distance(bounds.left+x*dx,bounds.top+y*dy);evaluations++ }
        fun attach(a:Int,b:Int) { if(linkA[a]<0)linkA[a]=b else linkB[a]=b }
        fun connect(a:Int,b:Int) { attach(a,b);attach(b,a) }
        val v=FloatArray(4);val ids=IntArray(4);val crossing=IntArray(4)
        for(y in 0 until ny)for(x in 0 until nx) {
            val i=y*(nx+1)+x
            v[0]=values[i];v[1]=values[i+1];v[2]=values[i+nx+2];v[3]=values[i+nx+1]
            val inside=(if(v[0]<0)1 else 0)+(if(v[1]<0)2 else 0)+(if(v[2]<0)4 else 0)+(if(v[3]<0)8 else 0)
            if(inside==0 || inside==15)continue
            ids[0]=y*nx+x;ids[1]=horizontal+y*(nx+1)+x+1;ids[2]=(y+1)*nx+x;ids[3]=horizontal+y*(nx+1)+x
            var n=0
            for(e in 0..3) {
                val j=(e+1)%4
                if((v[e]<0)!=(v[j]<0)) {
                    val t=(v[e]/(v[e]-v[j])).coerceIn(0f,1f);val id=ids[e]
                    pointX[id]=bounds.left+(x+when(e){0->t;1->1f;2->1f-t;else->0f})*dx
                    pointY[id]=bounds.top+(y+when(e){0->0f;1->t;2->1f;else->1f-t})*dy
                    crossing[n++]=e
                }
            }
            if(n==2)connect(ids[crossing[0]],ids[crossing[1]])
            else if(n==4) {
                val middle=scene.distance(bounds.left+(x+.5f)*dx,bounds.top+(y+.5f)*dy)<0;evaluations++
                if((inside==5 && middle)||(inside==10 && !middle)) { connect(ids[0],ids[1]);connect(ids[2],ids[3]) }
                else { connect(ids[0],ids[3]);connect(ids[1],ids[2]) }
            }
        }
        for(start in 0 until total) {
            if(linkA[start]<0 || used[start])continue
            var current=start;var previous=-1;var steps=0
            path.moveTo(pointX[start],pointY[start])
            while(steps++<total) {
                used[current]=true
                val nextId=if(linkA[current]!=previous)linkA[current] else linkB[current]
                if(nextId<0)break
                if(nextId==start) { path.close();break }
                path.lineTo(pointX[nextId],pointY[nextId]);previous=current;current=nextId
            }
        }
        return path
    }
}
