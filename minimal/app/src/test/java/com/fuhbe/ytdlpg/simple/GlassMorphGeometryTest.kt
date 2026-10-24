package com.fuhbe.ytdlpg.simple
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.asAndroidPath
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMorphGeometryTest {
 @Test fun measuredMenusFitPortraitLandscapeInsetsRtlAndLongLists() {
  for(size in listOf(Size(320f,480f),Size(411f,891f),Size(900f,450f),Size(800f,1200f)))
   for(y in listOf(40f,size.height*.45f,size.height-100f)) for(count in listOf(1,3,12)) for(rtl in listOf(false,true)) {
    val viewport=Rect(8f,24f,size.width-8,size.height-24)
    val origin=Rect(16f,y,size.width-16,y+54)
    val p=glassMorphPlacement(origin,viewport,count,1f,1.8f,rtl)!!
    assertTrue(p.panel.left>=viewport.left && p.panel.right<=viewport.right+.01f)
    assertTrue(p.panel.top>=viewport.top && p.panel.bottom<=viewport.bottom+.01f)
    assertTrue(p.panel.width<=268.01f)
   }
 }
 @Test fun actualRasterHasAConnectedThinNeckThenTwoSeparatedBodies() {
  val p=glassMorphPlacement(Rect(20f,100f,380f,154f),Rect(0f,0f,411f,891f),5,1f,1f,false)!!
  val f=glassMorphFrame(p,.45f,1f)
  val image=Bitmap.createBitmap(411,891,Bitmap.Config.ARGB_8888)
  Canvas(image).drawPath(glassMorphPath(p,.45f,1f).asAndroidPath(),Paint().apply { color=android.graphics.Color.WHITE })
  val x=f.panel.center.x.toInt();val y=((f.origin.bottom+f.panel.top)*.5f).toInt()
  assertTrue(android.graphics.Color.alpha(image.getPixel(x,y))>200)
  assertEquals(0,android.graphics.Color.alpha(image.getPixel((x+60).coerceAtMost(410),y)))
  image.eraseColor(0);Canvas(image).drawPath(glassMorphPath(p,1f,1f).asAndroidPath(),Paint().apply { color=android.graphics.Color.WHITE })
  assertEquals(0,android.graphics.Color.alpha(image.getPixel(x,((p.origin.bottom+p.panel.top)*.5f).toInt())))
 }
 @Test fun silhouetteIsNotAnAffineScaleOfTheOriginAndNeckTapersContinuously() {
  val p=glassMorphPlacement(Rect(20f,100f,380f,154f),Rect(0f,0f,411f,891f),5,1f,1f,false)!!
  val first=glassMorphPath(p,0f,1f).getBounds();val middle=glassMorphPath(p,.45f,1f).getBounds()
  assertEquals(p.origin,first);assertTrue(middle.height>first.height*2)
  assertTrue(glassMorphFrame(p,.89f,1f).neckWidth<glassMorphFrame(p,.80f,1f).neckWidth)
  assertTrue(glassMorphFrame(p,.90f,1f).neckWidth<.01f)
 }
 @Test fun invalidOrWhollyClippedAnchorsCannotStrandAMenu() {
  val view=Rect(0f,0f,320f,480f)
  assertNull(glassMorphPlacement(Rect(0f,600f,200f,654f),view,3,1f,1f,false))
  assertNull(glassMorphPlacement(Rect(Float.NaN,10f,100f,64f),view,3,1f,1f,false))
  assertNull(glassMorphPlacement(Rect(0f,10f,100f,64f),view,0,1f,1f,false))
 }
 @Test fun extremelyShortWindowsUseAnHonestCompactFallback() {
  val p=glassMorphPlacement(Rect(10f,24f,290f,78f),Rect(0f,0f,320f,105f),8,1f,2f,false)!!
  assertFalse(p.bridgePossible)
  assertTrue(glassMorphPath(p,.4f,1f).getBounds().width>0)
  assertTrue(p.panel.bottom<=105f)
 }
}
