package com.fuhbe.ytdlpg.simple

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
@Config(sdk=[32],application=SimpleApp::class,qualifiers="en-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ButtonConcurrentPointerTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 @Test fun aSecondFingerOnAnotherButtonCancelsBothActionsAndNormalTapStillWorks() {
  var first=0;var second=0
  compose.setContent { GlassTheme(false) { GlassButtonHost { Column(Modifier.padding(top=150.dp)) {
   GlassButton({first++},Modifier.testTag("first").fillMaxWidth()) { Text("Analyze") };Spacer(Modifier.height(70.dp))
   GlassButton({second++},Modifier.testTag("second").fillMaxWidth()) { Text("Download") }
  } } } };compose.waitForIdle()
  val a=compose.onNodeWithTag("first").fetchSemanticsNode().boundsInRoot.center
  val b=compose.onNodeWithTag("second").fetchSemanticsNode().boundsInRoot.center
  val down=SystemClock.uptimeMillis();var time=down
  fun send(action:Int,points:List<Offset>) {
   time+=16
   val properties=points.indices.map { i-> MotionEvent.PointerProperties().apply { id=i;toolType=MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
   val coordinates=points.map { p->MotionEvent.PointerCoords().apply { x=p.x;y=p.y;pressure=1f;size=1f } }.toTypedArray()
   compose.runOnIdle { val event=MotionEvent.obtain(down,time,action,points.size,properties,coordinates,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
    try { compose.activity.window.callback.dispatchTouchEvent(event) }finally { event.recycle() }
   }
  }
  send(MotionEvent.ACTION_DOWN,listOf(a))
  send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(a,b))
  send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(a,b))
  send(MotionEvent.ACTION_UP,listOf(a));compose.waitForIdle()
  assertEquals(0,first);assertEquals(0,second)
  send(MotionEvent.ACTION_DOWN,listOf(b));send(MotionEvent.ACTION_UP,listOf(b));compose.waitForIdle()
  assertEquals(0,first);assertEquals(1,second)
 }
 @Test fun lazyDisposalDoesNotStrandTheFloatingBodyAfterTheRealFingerLifts() {
  lateinit var host:GlassButtonHostState;var calls=0
  compose.setContent { GlassTheme(false) { GlassButtonHost {
   host=LocalGlassButtonHost.current!!
   LazyColumn(Modifier.fillMaxSize()) {
    item { GlassButton({calls++},Modifier.fillMaxWidth().testTag("disposable")) { Text("Download") } }
    items(50) { Spacer(Modifier.height(100.dp)) }
   }
  } } };compose.waitForIdle()
  val p=compose.onNodeWithTag("disposable").fetchSemanticsNode().boundsInRoot.center
  val down=SystemClock.uptimeMillis();var time=down
  fun send(action:Int,point:Offset) { time+=16;compose.runOnIdle {
   val event=MotionEvent.obtain(down,time,action,point.x,point.y,0)
   try { compose.activity.window.callback.dispatchTouchEvent(event) }finally { event.recycle() }
  } }
  send(MotionEvent.ACTION_DOWN,p)
  repeat(15) { send(MotionEvent.ACTION_MOVE,p-Offset(0f,(it+1)*80f)) }
  compose.runOnIdle { assertNotNull(host.active);assertFalse("The LazyColumn really disposed the source control",host.active!!.attached);assertTrue("Only the real window UP/CANCEL ends this physical stream",host.active!!.motion.touching) }
  send(MotionEvent.ACTION_UP,p-Offset(0f,1200f));compose.waitForIdle()
  assertEquals(0,calls);assertNull("Release must settle even after the source composition scope was cancelled",host.active)
 }

}
