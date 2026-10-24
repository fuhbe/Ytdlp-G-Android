package com.fuhbe.ytdlpg.simple

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.platform.testTag
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
class AnchoredButtonRegressionTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 @Test fun longPullHasASmallSaturatingExtensionAndAFixedOppositeEdge() {
  for(density in listOf(1f,2f,3f))for(width in listOf(120f,320f,720f)) {
   val size=Size(width*density,56*density)
   val press=squareButtonTransform(size,Offset.Zero,1f,density)
   for(delta in listOf(Offset(1000f,0f),Offset(-1000f,0f),Offset(0f,1000f),Offset(0f,-1000f),Offset(500f,-500f))) {
    val t=squareButtonTransform(size,delta*density,1f,density)
    assertTrue("The whole button must not travel with a long finger pull",t.translation.getDistance()<=2f*density)
    val growthX=(t.scaleX-press.scaleX)*size.width
    val growthY=(t.scaleY-press.scaleY)*size.height
    assertTrue(growthX<=2.01f*density && growthY<=2.01f*density)
    if(delta.x>0)assertEquals("Left anchor stays fixed",0f,t.translation.x-growthX/2,.001f)
    if(delta.x<0)assertEquals("Right anchor stays fixed",0f,t.translation.x+growthX/2,.001f)
    if(delta.y>0)assertEquals("Top anchor stays fixed",0f,t.translation.y-growthY/2,.001f)
    if(delta.y<0)assertEquals("Bottom anchor stays fixed",0f,t.translation.y+growthY/2,.001f)
   }
   val a=squareButtonTransform(size,Offset(1000f*density,0f),1f,density)
   val b=squareButtonTransform(size,Offset(100000f*density,0f),1f,density)
   assertTrue("Further pulling meets nonlinear resistance",b.translation.x-a.translation.x<.05f*density)
   val idle=squareButtonTransform(size,Offset.Zero,0f,density)
   assertEquals(Offset.Zero,idle.translation);assertEquals(1f,idle.scaleX,0f);assertEquals(1f,idle.scaleY,0f)
  }
 }
 @Test fun pressingDownloadNeverComposesASecondVisualCopyOfItsContent() {
  var instances=0;var calls=0
  compose.setContent { GlassTheme(false) { GlassButtonHost {
   Column(Modifier.fillMaxSize().padding(top=180.dp)) {
    GlassButton({calls++},Modifier.fillMaxWidth().testTag("anchored-download")) {
     DisposableEffect(Unit) { instances++;onDispose { instances-- } }
     Text("Download")
    }
   }
  } } };compose.waitForIdle();assertEquals(1,instances)
  val p=compose.onNodeWithTag("anchored-download").fetchSemanticsNode().boundsInRoot.center
  compose.mainClock.autoAdvance=false;val down=SystemClock.uptimeMillis();var time=down
  fun send(action:Int,point:Offset) { time+=16;compose.runOnIdle { val e=MotionEvent.obtain(down,time,action,point.x,point.y,0)
   try { compose.activity.window.callback.dispatchTouchEvent(e) }finally { e.recycle() }
  };compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
  send(MotionEvent.ACTION_DOWN,p);assertEquals("One actual content composition, not a hidden original plus a floating clone",1,instances)
  send(MotionEvent.ACTION_MOVE,p+Offset(240f,-100f));assertEquals(1,instances)
  send(MotionEvent.ACTION_UP,p+Offset(240f,-100f));compose.mainClock.advanceTimeBy(2000);compose.waitForIdle()
  assertEquals(1,instances);assertEquals(0,calls);compose.onAllNodesWithText("Download").assertCountEquals(1)
  compose.mainClock.autoAdvance=true
 }
}
