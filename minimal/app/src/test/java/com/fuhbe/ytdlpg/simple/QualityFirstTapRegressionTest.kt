package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="en-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QualityFirstTapRegressionTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 @Test @Config(sdk=[26,32,35]) fun aVisible1080OptionAcceptsItsFirstRealTapWhileTheSpringIsStillRunning()=check(1080,.97f)
 @Test fun theFirstVisible720RowAcceptsItsFirstTap()=check(720,.88f)
 @Test fun theFirstVisibleBestRowAcceptsItsFirstTap()=check(0,.90f)
 private fun check(value:Int,threshold:Float) {
  val stats=GlassMorphStats();val calls=mutableListOf<Int>();var selected by mutableIntStateOf(if(value==720)1080 else 720)
  compose.setContent { GlassTheme(false) { CompositionLocalProvider(LocalGlassMorphStats provides stats) {
   Column(Modifier.fillMaxSize().padding(16.dp)) { Spacer(Modifier.height(150.dp));GlassQualityPicker(listOf(0,1080,720),selected) { calls+=it;selected=it } }
  } } };compose.waitForIdle();compose.mainClock.autoAdvance=false
  compose.onNodeWithTag("glass-quality-trigger").performClick()
  fun frame() { compose.mainClock.advanceTimeByFrame();compose.waitForIdle();compose.runOnIdle {
   (if(android.os.Build.VERSION.SDK_INT>=29)WindowInspector.getGlobalWindowViews() else listOf(compose.activity.window.decorView)).filter { it.width>0 && it.height>0 }.forEach { it.draw(Canvas(Bitmap.createBitmap(it.width,it.height,Bitmap.Config.ARGB_8888))) }
  } }
  var frames=0
  while((stats.state==null || stats.state!!.progress.value<threshold) && frames++<100)frame()
  assertNotNull(stats.placement);assertTrue(stats.state!!.progress.isRunning)
  val panel=stats.placement!!.panel
  compose.onNodeWithTag("glass-morph-window").performTouchInput { click(Offset(panel.center.x,panel.top+6f+54f*(listOf(0,1080,720).indexOf(value)+.5f))) }
  compose.waitForIdle();assertEquals("The FIRST actual visible-row tap must select",listOf(value),calls);assertEquals(value,selected)
  compose.mainClock.advanceTimeBy(2000);compose.waitForIdle();assertFalse(stats.state!!.mounted);compose.mainClock.autoAdvance=true
 }
}
