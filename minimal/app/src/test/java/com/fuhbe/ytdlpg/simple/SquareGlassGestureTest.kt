package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Drives the actual shared component; fixture metadata never calls the engine.
 * Captured software frames prove gestures/layout, not hardware AGSL refraction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="w411dp-h1060dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SquareGlassGestureTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val calls=mutableListOf<Int>()
    private fun track()=compose.onNodeWithTag("square-test")
    private fun show(rtl:Boolean=false,unequal:Boolean=false) {
        compose.setContent {
            var selected by remember { mutableIntStateOf(0) }
            GlassTheme(false) {
                CompositionLocalProvider(LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    GlassSlidingSegments(selected,2,{calls+=it;selected=it},Modifier.fillMaxWidth().testTag("square-test")) {
                        Surface(onClick={calls+=0;selected=0},color=androidx.compose.ui.graphics.Color.Transparent,
                            modifier=Modifier.weight(if(unequal) 1f else 2f).height(52.dp).glassSegmentItem(0)) { Text("MP3") }
                        Surface(onClick={calls+=1;selected=1},color=androidx.compose.ui.graphics.Color.Transparent,
                            modifier=Modifier.weight(2f).height(52.dp).glassSegmentItem(1)) { Text("MP4") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    @Test @Config(sdk=[26,28,31,32,33,35]) fun dragSelectsExactlyOnceAcrossAllRequiredApis() {
        show()
        track().performTouchInput { swipe(Offset(width*.25f,centerY),Offset(width*.75f,centerY),300) }
        assertEquals(listOf(1),calls)
        compose.onAllNodesWithTag("glass-selection-capsule").assertCountEquals(1)
        compose.onAllNodesWithText("MP3").assertCountEquals(1)
        compose.onAllNodesWithText("MP4").assertCountEquals(1)
        compose.onNodeWithText("MP3").performTouchInput { click() }
        assertEquals(listOf(1,0),calls)
    }
    @Test fun rtlDragUsesMeasuredPhysicalBounds() {
        show(rtl=true)
        track().performTouchInput { swipe(Offset(width*.75f,centerY),Offset(width*.25f,centerY),300) }
        assertEquals(listOf(1),calls)
    }
    @Test fun unequalChildrenKeepThePaneAlignedToTheirActualWidths() {
        show(unequal=true)
        track().performTouchInput { swipe(Offset(width/6f,centerY),Offset(width*.6f,centerY),300) }
        compose.waitForIdle()
        assertEquals(listOf(1),calls)
        val pane=compose.onNodeWithTag("glass-selection-capsule").fetchSemanticsNode().boundsInRoot
        val item=compose.onNodeWithText("MP4").fetchSemanticsNode().boundsInRoot
        assertEquals(item.left,pane.left,2f)
        assertEquals(item.width,pane.width,2f)
    }
    @Test fun cancellationAndMultitouchNeverCommit() {
        show()
        track().performTouchInput { down(Offset(width*.25f,centerY));moveTo(Offset(width*.75f,centerY),200);cancel() }
        assertTrue(calls.isEmpty())
        track().performTouchInput {
            down(0,Offset(width*.25f,centerY));moveTo(0,Offset(width*.75f,centerY),200)
            down(1,Offset(width*.5f,centerY));up(0);up(1)
        }
        assertTrue(calls.isEmpty())
    }
    @Test fun outsideReleaseCommitsButUnclaimedVerticalGestureDoesNot() {
        show()
        track().performTouchInput { down(Offset(width*.25f,centerY));moveTo(Offset(width*.75f,centerY),200);moveTo(Offset(width*.75f,-30f),100);up() }
        assertEquals(listOf(1),calls);calls.clear()
        track().performTouchInput { swipe(Offset(width*.25f,centerY),Offset(width*.25f,-100f),200) }
        assertTrue(calls.isEmpty())
    }
    @Test fun returningToSelectedItemNeverDispatchesOrChangesTheModelEarly() {
        show()
        track().performTouchInput { down(Offset(width*.25f,centerY));moveTo(Offset(width*.75f,centerY),200) }
        assertTrue(calls.isEmpty())
        track().performTouchInput { moveTo(Offset(width*.25f,centerY),200);up() }
        assertTrue(calls.isEmpty())
    }
    @Test fun actualMp3Mp4AndZipGesturesKeepTheExistingChoiceRules() {
        val model=AppModel(RuntimeEnvironment.getApplication()).apply {
            val items=(1..3).map { Media("square-$it","https://youtu.be/123456789ab","Example $it",heights=listOf(1080,720)) }
            inspection=Inspection("Playlist",items,true,listOf(1080,720));selected=items.map { it.id }.toSet()
        }
        compose.setContent { AppContent(model) }
        fun swipeBetween(first:String,second:String) {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(first))
            val a=compose.onNodeWithText(first).fetchSemanticsNode().boundsInRoot.center
            val b=compose.onNodeWithText(second).fetchSemanticsNode().boundsInRoot.center
            compose.onRoot().performTouchInput { swipe(a,b,300) }
        }
        swipeBetween("MP4","MP3");compose.runOnIdle { assertTrue(model.choice.mp3) }
        swipeBetween("MP3","MP4");compose.runOnIdle { assertFalse(model.choice.mp3) }
        swipeBetween("Normal","ZIP");compose.runOnIdle { assertTrue(model.choice.zip) }
        compose.runOnIdle { model.selected=setOf("square-1");model.choice=Policy.choice(model.choice,1) }
        compose.onAllNodesWithText("ZIP").assertCountEquals(0)
        compose.runOnIdle { assertFalse(model.choice.zip) }
    }
    @Test fun actualHistoryTabsStillFilterCompletedJobs() {
        compose.setContent { GlassTheme(false) { DownloadsScreen(emptyList()) {_,_->} } }
        val a=compose.onNodeWithText("Downloads (0)").fetchSemanticsNode().boundsInRoot.center
        val b=compose.onNodeWithText("Concluídos").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { swipe(a,b,300) }
        compose.onNodeWithText("Nenhum download concluído.").assertIsDisplayed()
    }
    @Test fun liveDownloadCountDoesNotJumpOrPrematurelyCommitTheHeldPane() {
        val jobs=mutableStateOf<List<QueueJob>>(emptyList())
        compose.setContent { GlassTheme(false) { DownloadsScreen(jobs.value) {_,_->} } }
        val control=compose.onNodeWithTag("glass-segment-track")
        control.performTouchInput { down(Offset(width*.25f,centerY));moveTo(Offset(width*.6f,centerY),200) }
        compose.waitForIdle()
        val before=compose.onNodeWithTag("glass-selection-capsule").fetchSemanticsNode().boundsInRoot.left
        compose.runOnIdle {
            val media=Media("count-fixture","https://youtu.be/123456789ab","Example")
            jobs.value=(1..10).map { QueueJob(id="count-$it",title="Example $it",items=listOf(media),choice=Choice(),folder="",state="PAUSED") }
        }
        compose.onNodeWithText("Downloads (10)").assertIsDisplayed()
        val after=compose.onNodeWithTag("glass-selection-capsule").fetchSemanticsNode().boundsInRoot.left
        assertEquals(before,after,1f)
        compose.onAllNodesWithText("Nenhum download concluído.").assertCountEquals(0)
        control.performTouchInput { moveTo(Offset(width*.75f,centerY),100);up() }
        compose.onNodeWithText("Nenhum download concluído.").assertIsDisplayed()
    }
    @Test fun measuredGeometryHandlesAsymmetricBoundsAndInvalidPoints() {
        val bounds=mapOf(0 to Rect(0f,0f,80f,60f),1 to Rect(80f,0f,300f,60f))
        assertEquals(1,glassSegmentTarget(Offset(100f,30f),IntSize(300,60),bounds))
        assertNull(glassSegmentTarget(Offset(Float.NaN,30f),IntSize(300,60),bounds))
        assertNull(glassSegmentTarget(Offset(300f,30f),IntSize(300,60),bounds))
        assertEquals(80f,glassSegmentWidth(40f,bounds.values),.01f)
        assertEquals(220f,glassSegmentWidth(190f,bounds.values),.01f)
        assertTrue(glassSegmentWidth(100f,bounds.values) in 81f..219f)
    }
    @Test fun captureRealDragAndReleaseFramesFromTheSharedComponent() {
        show()
        val out=File("../../docs/visual-square-065/motion").apply { mkdirs() }
        fun frame(name:String) {
            compose.waitForIdle()
            compose.runOnIdle {
                val view=compose.activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(out,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        }
        compose.mainClock.autoAdvance=false
        frame("00-idle")
        track().performTouchInput { down(Offset(width*.25f,centerY));moveTo(Offset(width*.42f,centerY),100) }
        compose.mainClock.advanceTimeBy(96);frame("01-pressed")
        track().performTouchInput { moveTo(Offset(width*.58f,centerY),80) }
        compose.mainClock.advanceTimeBy(64);frame("02-drag-middle")
        assertTrue(calls.isEmpty())
        track().performTouchInput { moveTo(Offset(width*.75f,centerY),80) }
        compose.mainClock.advanceTimeBy(80);frame("03-drag-target")
        track().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64);frame("04-release-spring")
        compose.mainClock.advanceTimeBy(1500);frame("05-settled")
        compose.mainClock.autoAdvance=true
        assertEquals(listOf(1),calls)
    }
}
