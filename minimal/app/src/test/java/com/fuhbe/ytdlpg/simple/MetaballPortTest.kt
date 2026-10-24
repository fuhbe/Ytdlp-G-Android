package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.asAndroidPath
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MetaballPortTest {
 private fun placement()=glassMorphPlacement(Rect(20f,100f,380f,154f),Rect(0f,0f,411f,700f),5,1f,1f,false)!!
 @Test fun portUsesThePinnedCodePhysicsRatherThanOutdatedProse() {
  assertEquals(120f,LiquidMorphPhysics.OPEN_STIFFNESS,0f);assertEquals(200f,LiquidMorphPhysics.CLOSE_STIFFNESS,0f)
  assertEquals(-3.2f,LiquidMorphPhysics.CLOSE_VELOCITY,0f)
  val open=LiquidMorphPhysics.compute(.5f,20f,60f);val close=LiquidMorphPhysics.compute(.5f,20f,60f,closing=true)
  assertTrue(open.pathT>1f);assertEquals(.5f,close.pathT,.0001f)
  assertEquals(0f,open.anchorScale,0f);assertTrue(close.anchorScale>.8f)
  assertEquals(.7f,LiquidMorphPhysics.adaptiveAmplitude(10000f),.0001f)
  assertEquals(.2f,LiquidMorphPhysics.blendAttenuation(100f),.0001f)
 }
 @Test fun nativeRasterPixelsAreDeterminedByTheTwoBlobSmoothUnionField() {
  val p=placement();val contour=MetaballContour();var tested=0;var fusionPixels=0
  val image=Bitmap.createBitmap(411,700,Bitmap.Config.ARGB_8888)
  val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE }
  for(closing in listOf(false,true))for(raw in listOf(.03f,.08f,.14f,.22f,.30f,.38f,.50f,.65f,.9f,1f)) {
   val scene=metaballScene(p,raw,1f,closing);image.eraseColor(0)
   Canvas(image).drawPath(contour.update(scene,p.viewport,1f).asAndroidPath(),paint)
   for(y in 4 until 696 step 5)for(x in 4 until 407 step 5) {
    val d=scene.distance(x+.5f,y+.5f)
    if(abs(d)>5f) { assertEquals("SDF must determine raster coverage ($x,$y,$raw,$closing,d=$d)",d<0,android.graphics.Color.alpha(image.getPixel(x,y))>128);tested++ }
    if(scene.a.visible && blobDistance(x+.5f,y+.5f,scene.a)>.5f && blobDistance(x+.5f,y+.5f,scene.b)>.5f && d<-.5f) {
     assertTrue(android.graphics.Color.alpha(image.getPixel(x,y))>128);fusionPixels++
    }
   }
  }
  assertTrue(tested>100000);assertTrue("Smooth-min must CREATE visible connecting pixels outside both shapes",fusionPixels>15)
  File("build/reports/square-sdf-test2/field-raster.json").apply { parentFile!!.mkdirs() }.writeText(JSONObject()
   .put("actualNativeSkiaSoftwareRaster",true).put("checkedPixels",tested).put("fusionPixelsOutsideBothIndividualBlobs",fusionPixels)
   .put("sdfEvaluations",contour.evaluations).put("gpuExecution",false).toString(2))
 }
 @Test fun coincidentBlobsLandOnTheRealTriggerWithoutSmoothMinInflation() {
  val p=placement();val scene=metaballScene(p,0f,1f,true)
  assertEquals(0f,scene.blend,0f);assertEquals(p.origin,scene.b.rect)
  assertEquals(0f,scene.distance(p.origin.left,p.origin.center.y),.001f)
  assertTrue(scene.distance(p.origin.left-1f,p.origin.center.y)>0)
  assertFalse(metaballScene(p,1f,1f).a.visible)
 }
 @Test fun reverseMidFlightKeepsDisplayedGeometryContinuousAndThenSettles() {
  val p=placement();val resolver=MetaballResolver()
  val before=resolver.resolve(p,.32f,1f,false);val reversed=resolver.resolve(p,.32f,1f,true)
  assertEquals(before.a.rect,reversed.a.rect);assertEquals(before.b.rect,reversed.b.rect);assertEquals(before.blend,reversed.blend,.001f)
  for(raw in listOf(.25f,.15f,.05f,0f))resolver.resolve(p,raw,1f,true)
  val settled=resolver.resolve(p,0f,1f,true);assertEquals(p.origin,settled.b.rect);assertEquals(0f,settled.blend,0f)
 }
 @Test fun aHeldFrameReusesTheContourAndDoesNoNewFieldEvaluation() {
  val p=placement();val s=metaballScene(p,.2f,1f);val contour=MetaballContour()
  val a=contour.update(s,p.viewport,1f);val evaluations=contour.evaluations
  repeat(60) { assertSame(a,contour.update(s,p.viewport,1f)) };assertEquals(evaluations,contour.evaluations)
 }
 @Test @Config(sdk=[35]) fun actualAgslProgramCompilesOnceAndAcceptsNormalBasedRefractionUniforms() {
  val stats=MetaballRenderStats();val native=NativeMetaballEffect(stats);val p=placement()
  repeat(20) { native.update(metaballScene(p,it/20f,1f),p.viewport,1f,false) }
  assertEquals(1,stats.shaderCompilations);assertEquals(20,stats.effectUpdates)
  File("build/reports/square-sdf-test2/agsl-compile.json").apply { parentFile!!.mkdirs() }.writeText("{\"actualRuntimeShaderCompiled\":true,\"shaderCompilations\":1,\"uniformUpdates\":20,\"gpuPixelsOrPerformanceVerified\":false}")
 }
}
