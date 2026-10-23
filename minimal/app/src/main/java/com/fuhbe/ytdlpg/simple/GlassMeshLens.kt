package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.sqrt

/** A bounded texture mesh. HWUI draws these triangles on a hardware Canvas on
 * API31/32 without AGSL. Source pixels are the actual cached control layer,
 * including its labels, icons and background; no invented lens artwork.
 * GPU execution/performance must still be measured on the actual device. */
internal class GlassMeshLens {
    val columns=48
    val rows=12
    val vertices=FloatArray((columns+1)*(rows+1)*2)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var hardwareCanvasObserved=false;private set
    var draws=0;private set
    private var previous=Rect.Zero
    private var previousPressure=-1f
    private var previousStrength=-1f
    private var sourceWidth=0
    private var sourceHeight=0
    fun update(width:Int,height:Int,pane:Rect,pressure:Float,strength:Float=1f) {
        if(width==sourceWidth && height==sourceHeight && pane==previous && pressure==previousPressure && strength==previousStrength)return
        sourceWidth=width;sourceHeight=height;previous=pane;previousPressure=pressure;previousStrength=strength
        val rx=(pane.width/2).coerceAtLeast(1f);val ry=(pane.height/2).coerceAtLeast(1f)
        var i=0
        for(y in 0..rows)for(x in 0..columns) {
            val sx=width*x/columns.toFloat();val sy=height*y/rows.toFloat()
            val dx=sx-pane.center.x;val dy=sy-pane.center.y
            // Smooth finite bulge: magnifies interior features and compresses
            // the edge band. Positive Jacobian avoids folded triangles/seams.
            val r=sqrt(dx*dx/(rx*rx)+dy*dy/(ry*ry)).coerceAtMost(1f)
            val gain=(.12f+.045f*pressure.coerceIn(0f,1f))*(1f-r*r)*strength.coerceIn(0f,1f)
            vertices[i++]=sx+dx*gain
            vertices[i++]=sy+dy*gain
        }
    }
    fun draw(scope:DrawScope,bitmap:Bitmap,pane:Rect,pressure:Float,strength:Float=1f) {
        update(bitmap.width,bitmap.height,pane,pressure,strength)
        val canvas=scope.drawContext.canvas.nativeCanvas
        hardwareCanvasObserved=canvas.isHardwareAccelerated;draws++
        canvas.save()
        try {
            canvas.translate(-pane.left,-pane.top)
            canvas.drawBitmapMesh(bitmap,columns,rows,vertices,0,null,0,paint)
        } finally { canvas.restore() }
    }
    fun drawGlyphs(scope:DrawScope,bitmap:Bitmap,pane:Rect,pressure:Float,strength:Float,
        bounds:List<Pair<Int,Rect>>,center:Float,ink:GlassSegmentInk) {
        update(bitmap.width,bitmap.height,pane,pressure,strength)
        val canvas=scope.drawContext.canvas.nativeCanvas
        canvas.save()
        try {
            canvas.translate(-pane.left,-pane.top)
            for((index,r) in bounds) {
                if(r.right<pane.left || r.left>pane.right) continue
                canvas.save()
                try {
                    canvas.clipRect(r.left,r.top,r.right,r.bottom)
                    paint.colorFilter=ink.nativeFilter(glassIllumination(center,index,bounds))
                    canvas.drawBitmapMesh(bitmap,columns,rows,vertices,0,null,0,paint)
                } finally { canvas.restore() }
            }
        } finally { paint.colorFilter=null;canvas.restore() }
    }
}
