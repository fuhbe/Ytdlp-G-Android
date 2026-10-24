package com.fuhbe.ytdlpg.simple
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MetaballCacheTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 @Test fun movingSdfGeometryDoesNotRecaptureItsActualBackgroundInput() {
  val menu=GlassMorphStats();val render=MetaballRenderStats()
  compose.setContent { GlassTheme(false) { CompositionLocalProvider(LocalGlassMorphStats provides menu,LocalMetaballRenderStats provides render,LocalGlassRendering provides GlassRendering.BLUR) {
   GlassOverlayCapture { capture,_ -> Column(Modifier.fillMaxSize().then(capture).padding(16.dp)) {
    Text("Actual activity text behind the SDF")
    Spacer(Modifier.height(100.dp));GlassQualityPicker(listOf(0,1080,720),1080) {}
    Text("More real UI pixels, not a glass illustration")
   } }
  } } }
  compose.waitForIdle();compose.mainClock.autoAdvance=false
  compose.onNodeWithTag("glass-quality-trigger").performClick()
  fun draw()=compose.runOnIdle { WindowInspector.getGlobalWindowViews().filter { it.width>0 && it.height>0 }.forEach { it.draw(Canvas(Bitmap.createBitmap(it.width,it.height,Bitmap.Config.ARGB_8888))) } }
  repeat(8) { compose.waitForIdle();draw();compose.mainClock.advanceTimeByFrame() }
  assertTrue(render.sourceCaptures>0);assertTrue(menu.source!!.ready)
  val input=render.sourceCaptures;val activity=menu.source!!.records;val evaluations=render.sdfEvaluations
  repeat(18) { compose.mainClock.advanceTimeBy(16);compose.waitForIdle();draw() }
  assertEquals("No background-input capture per morph frame",input,render.sourceCaptures)
  assertEquals("No activity capture per morph frame",activity,menu.source!!.records)
  assertTrue("The actual SDF geometry must keep rendering",render.sdfEvaluations>evaluations)
  File("build/reports/square-sdf-test2/cache.json").apply { parentFile!!.mkdirs() }.writeText("{\"frames\":18,\"extraInputCaptures\":0,\"extraActivityCaptures\":0,\"cpuBitmapReadbacksInRenderer\":0,\"hostSoftwareForcedBlur\":true,\"gpuPerformanceClaim\":false}")
  compose.runOnIdle { menu.state!!.close() };compose.mainClock.advanceTimeBy(2000);compose.mainClock.autoAdvance=true
 }
}
