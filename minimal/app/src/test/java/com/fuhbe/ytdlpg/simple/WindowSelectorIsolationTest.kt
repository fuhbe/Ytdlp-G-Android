package com.fuhbe.ytdlpg.simple
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="en-w411dp-h1100dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowSelectorIsolationTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 @Test fun allFourApprovedSelectorsKeepDiagonalReleaseThroughTheUnchangedWindowDispatch() {
  val model=AppModel(RuntimeEnvironment.getApplication()).apply {
   theme="light"
   val list=(1..3).map { Media("sample-$it","https://youtu.be/123456789ab","Sample $it",heights=listOf(1080,720)) }
   inspection=Inspection("Sample playlist",list,true,listOf(1080,720));selected=list.map { it.id }.toSet()
  }
  compose.setContent { AppContent(model) };compose.waitForIdle()
  fun drag(first:SemanticsNodeInteraction,last:SemanticsNodeInteraction,dy:Float) {
   val a=first.fetchSemanticsNode().boundsInRoot.center;val b=last.fetchSemanticsNode().boundsInRoot.center
   val start=SystemClock.uptimeMillis();var time=start
   fun send(action:Int,p:Offset) { time+=32;compose.runOnIdle { val e=MotionEvent.obtain(start,time,action,p.x,p.y,0);try { compose.activity.window.callback.dispatchTouchEvent(e) }finally { e.recycle() } } }
   send(MotionEvent.ACTION_DOWN,a)
   send(MotionEvent.ACTION_MOVE,a+Offset(if(b.x>a.x)30f else -30f,0f)) // acquire horizontally first
   send(MotionEvent.ACTION_MOVE,b+Offset(0f,dy));send(MotionEvent.ACTION_UP,b+Offset(0f,dy))
   compose.waitForIdle()
  }
  compose.onNodeWithText("MP4").performScrollTo()
  drag(compose.onNodeWithText("MP4"),compose.onNodeWithText("MP3"),90f);assertTrue(model.choice.mp3)
  drag(compose.onNodeWithText("MP3"),compose.onNodeWithText("MP4"),-90f);assertFalse(model.choice.mp3)
  compose.onNodeWithText("Normal").performScrollTo()
  drag(compose.onNodeWithText("Normal"),compose.onNodeWithText("ZIP"),90f);assertTrue(model.choice.zip)
  drag(compose.onNodeWithText("ZIP"),compose.onNodeWithText("Normal"),-90f);assertFalse(model.choice.zip)
  drag(compose.onNode(hasContentDescription("Back") and hasAnyAncestor(hasTestTag("glass-navigation"))),compose.onNodeWithContentDescription("Downloads"),-90f)
  assertEquals("downloads",model.area)
  drag(compose.onNodeWithText("Downloads (0)"),compose.onNodeWithText("Completed"),90f)
  compose.onNodeWithText("No completed downloads.").assertIsDisplayed()
  drag(compose.onNodeWithText("Completed"),compose.onNodeWithText("Downloads (0)"),-90f)
  compose.onNodeWithText("Downloads (0)").assertIsSelected()
 }
}
