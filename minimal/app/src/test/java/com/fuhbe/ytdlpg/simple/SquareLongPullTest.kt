package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.platform.testTag
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SquareLongPullTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 private lateinit var host:GlassButtonHostState
 private lateinit var scroll:ScrollState
 private val stats=ElasticButtonStats();private var calls=0
 private var time=0L;private var downTime=0L
 private fun show(scrolling:Boolean=false) {
  compose.setContent { GlassTheme(false) { CompositionLocalProvider(LocalElasticButtonStats provides stats) {
   GlassButtonHost {
    host=LocalGlassButtonHost.current!!;scroll=rememberScrollState()
    Column(Modifier.fillMaxSize().then(if(scrolling)Modifier.verticalScroll(scroll) else Modifier).padding(horizontal=32.dp)) {
     Spacer(Modifier.height(200.dp))
     GlassButton({calls++},Modifier.fillMaxWidth().height(56.dp).testTag("long-pull")) { Text("Analyze link") }
     Spacer(Modifier.height(1800.dp))
    }
   }
  } } };compose.waitForIdle()
 }
 private fun send(action:Int,p:Offset) {
  if(action==MotionEvent.ACTION_DOWN) { downTime=SystemClock.uptimeMillis();time=downTime }
  time+=16
  compose.runOnIdle {
   val event=MotionEvent.obtain(downTime,time,action,p.x,p.y,0)
   try { compose.activity.window.callback.dispatchTouchEvent(event) }finally { event.recycle() }
  }
 }
 @Test fun actualWindowKeepsTheSameFingerFarOutsideInEveryDirectionAndCancelsOnlyClick() {
  show();val hit=compose.onNodeWithTag("long-pull").fetchSemanticsNode().boundsInRoot
  compose.mainClock.autoAdvance=false
  for(d in listOf(Offset(600f,0f),Offset(-600f,0f),Offset(0f,650f),Offset(0f,-650f),Offset(520f,-540f))) {
   send(MotionEvent.ACTION_DOWN,hit.center);val entry=host.active!!;val source=entry.origin
   send(MotionEvent.ACTION_MOVE,hit.center+d*.5f);send(MotionEvent.ACTION_MOVE,hit.center+d)
   compose.runOnIdle {
    assertTrue(entry.motion.touching);assertEquals(d.x,entry.motion.delta.x,.1f);assertEquals(d.y,entry.motion.delta.y,.1f)
    val t=squareButtonTransform(source.size,entry.motion.delta,entry.motion.level,1f)
    assertTrue("Long pull must go beyond the former 6dp overscan",t.translation.getDistance()>24f)
    assertTrue(t.scaleX>1f || t.scaleY>1f)
   }
   assertEquals(hit,compose.onNodeWithTag("long-pull").fetchSemanticsNode().boundsInRoot)
   send(MotionEvent.ACTION_UP,hit.center+d);compose.mainClock.advanceTimeBy(2000);compose.waitForIdle()
   assertNull(host.active);assertEquals(0,calls);assertTrue(entry.motion.settled)
  }
  send(MotionEvent.ACTION_DOWN,hit.center);send(MotionEvent.ACTION_UP,hit.center);compose.mainClock.advanceTimeBy(2000)
  assertEquals(1,calls);compose.mainClock.autoAdvance=true
 }
 @Test fun consumedVerticalScrollDoesNotFalselyCancelTheVisualStream() {
  show(scrolling=true);val hit=compose.onNodeWithTag("long-pull").fetchSemanticsNode().boundsInRoot
  send(MotionEvent.ACTION_DOWN,hit.center);val entry=host.active!!
  repeat(8) { send(MotionEvent.ACTION_MOVE,hit.center-Offset(0f,(it+1)*40f)) }
  compose.runOnIdle { assertTrue(entry.motion.touching);assertTrue(entry.motion.delta.y < -280f) }
  assertTrue("The observer must never steal scrolling",scroll.value>100)
  send(MotionEvent.ACTION_UP,hit.center-Offset(0f,320f));compose.waitForIdle();assertEquals(0,calls);assertNull(host.active)
 }
 @Test fun realAndroidCancelResetsWithoutDispatchingAnAction() {
  show();val p=compose.onNodeWithTag("long-pull").fetchSemanticsNode().boundsInRoot.center
  send(MotionEvent.ACTION_DOWN,p);send(MotionEvent.ACTION_MOVE,p+Offset(400f,200f));send(MotionEvent.ACTION_CANCEL,p)
  compose.waitForIdle();assertEquals(0,calls);assertNull(host.active)
  send(MotionEvent.ACTION_DOWN,p);send(MotionEvent.ACTION_UP,p);compose.waitForIdle();assertEquals(1,calls)
 }
 @Test fun actualWindowMovesDoNotRecordMaterialAgainOrRecomposeTheButton() {
  show();val p=compose.onNodeWithTag("long-pull").fetchSemanticsNode().boundsInRoot.center
  fun draw()=compose.runOnIdle { val v=compose.activity.window.decorView;v.draw(Canvas(Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888))) }
  draw();send(MotionEvent.ACTION_DOWN,p);draw();compose.waitForIdle();draw()
  val entry=host.active!!;val records=entry.optics.materialRecords;val compositions=stats.compositions;val springs=stats.returnSprings
  repeat(40) { send(MotionEvent.ACTION_MOVE,p+Offset(it*8f,it*4f));draw() }
  assertEquals(records,entry.optics.materialRecords);assertEquals(compositions,stats.compositions);assertEquals(springs,stats.returnSprings)
  send(MotionEvent.ACTION_UP,p+Offset(312f,156f));compose.waitForIdle();assertEquals(0,calls)
  java.io.File("build/reports/square-sdf-test2/window-material-cache.json").apply { parentFile!!.mkdirs() }.writeText("{\"windowMoveSamples\":40,\"extraMaterialRecords\":0,\"extraButtonCompositions\":0,\"springsStartedDuringMove\":0,\"hostSoftwareOnly\":true}")
 }
 @Test fun squareCapsuleRemainsOneBodyWithoutAThinWaistUnderExtremePull() {
  // Actual upstream G2 ContinuousCapsule, rasterized after the actual matrix.
  val shape=com.fuhbe.ytdlpg.simple.buttonsquare.shapes.ContinuousCapsule()
  val bitmap=Bitmap.createBitmap(900,500,Bitmap.Config.ARGB_8888)
  val canvas=Canvas(bitmap);val size=Size(320f,56f)
  val transform=squareButtonTransform(size,Offset(900f,500f),1f,1f)
  val outline=shape.createOutline(size,androidx.compose.ui.unit.LayoutDirection.Ltr,androidx.compose.ui.unit.Density(1f))
  val path=(outline as androidx.compose.ui.graphics.Outline.Generic).path
  canvas.translate(220f+transform.translation.x,180f+transform.translation.y)
  canvas.scale(transform.scaleX,transform.scaleY,160f,28f)
  canvas.drawPath(path.asAndroidPath(),android.graphics.Paint().apply { color=android.graphics.Color.WHITE })
  val middle=(180+transform.translation.y+28).toInt()
  var transitions=0;var previous=false
  for(x in 0 until 900) { val filled=android.graphics.Color.alpha(bitmap.getPixel(x,middle))>128;if(filled!=previous)transitions++;previous=filled }
  assertEquals("One continuous cross section, not two lobes or a hole",2,transitions)
 }
}
