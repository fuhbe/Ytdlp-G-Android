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
 // Historical case IDs retained for traceability; new specification requires
 // Blob A to vanish, rather than the old two persistent bodies/Bezier bridge.
 @Test fun actualRasterHasAConnectedThinNeckThenTwoSeparatedBodies() {
  val p=glassMorphPlacement(Rect(20f,100f,380f,154f),Rect(0f,0f,411f,891f),5,1f,1f,false)!!
  val contour=MetaballContour();var fused=0
  for(t in listOf(.05f,.1f,.2f,.3f)) {
   val scene=metaballScene(p,t,1f);val image=Bitmap.createBitmap(411,891,Bitmap.Config.ARGB_8888)
   Canvas(image).drawPath(contour.update(scene,p.viewport,1f).asAndroidPath(),Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE })
   for(y in 80..450)for(x in 10..400 step 3)if(blobDistance(x+.5f,y+.5f,scene.a)>.5f && blobDistance(x+.5f,y+.5f,scene.b)>.5f && scene.distance(x+.5f,y+.5f)<-2f) {
    assertTrue(android.graphics.Color.alpha(image.getPixel(x,y))>128);fused++
   }
  }
  assertTrue(fused>15)
  val settled=metaballScene(p,1f,1f);assertFalse(settled.a.visible);assertTrue(settled.distance(379f,127f)>0f)
 }
 @Test fun silhouetteIsNotAnAffineScaleOfTheOriginAndNeckTapersContinuously() {
  val p=glassMorphPlacement(Rect(20f,100f,380f,154f),Rect(0f,0f,411f,891f),5,1f,1f,false)!!
  val first=glassMorphPath(p,0f,1f).getBounds();val middle=glassMorphPath(p,.45f,1f).getBounds()
  assertEquals(p.origin,first);assertTrue(middle.height>first.height*2)
  assertTrue(metaballScene(p,.3f,1f).a.rect.width<metaballScene(p,.1f,1f).a.rect.width)
  assertFalse(metaballScene(p,.4f,1f).a.visible)
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
