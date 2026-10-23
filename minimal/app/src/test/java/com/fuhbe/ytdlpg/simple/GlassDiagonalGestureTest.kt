package com.fuhbe.ytdlpg.simple

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
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

/** Real pointer events delivered to the production selectors, not a mock recognizer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="w800dp-h1100dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassDiagonalGestureTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val selected=mutableIntStateOf(0)
    private val trackWidth=mutableStateOf(360.dp)
    private val calls=mutableListOf<Int>()
    private var parentMoves=0
    private lateinit var scroll:ScrollState
    private fun show(count:Int=3,rtl:Boolean=false,scrollable:Boolean=false,observeParent:Boolean=false) {
        compose.setContent { GlassTheme(false) {
            scroll=rememberScrollState()
            val observer=if(observeParent) Modifier.pointerInput(Unit) {
                awaitPointerEventScope { while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Main)
                    for(change in event.changes) if(change.pressed && change.position!=change.previousPosition && !change.isConsumed) {
                        parentMoves++;change.consume()
                    }
                } }
            } else Modifier
            Column(Modifier.fillMaxSize().then(observer).then(if(scrollable) Modifier.verticalScroll(scroll) else Modifier)) {
                Spacer(Modifier.height(200.dp))
                CompositionLocalProvider(LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    GlassSlidingSegments(selected.intValue,count,{calls+=it;selected.intValue=it},Modifier.width(trackWidth.value).testTag("diagonal-track")) {
                        repeat(count) { index -> Surface(onClick={calls+=index;selected.intValue=index},color=androidx.compose.ui.graphics.Color.Transparent,
                            modifier=Modifier.weight(1f).height(56.dp).glassSegmentItem(index).testTag("diagonal-item-$index")) { Text("Option $index") } }
                    }
                }
                Spacer(Modifier.height(1800.dp))
            }
        } }
        compose.waitForIdle()
    }
    private fun track()=compose.onNodeWithTag("diagonal-track")
    private fun reset(index:Int,w:Int=360) { compose.runOnIdle { selected.intValue=index;trackWidth.value=w.dp;calls.clear() };compose.waitForIdle() }
    private fun diagonal(to:Int,y:Float,finalMove:Boolean=true) {
        val box=track().fetchSemanticsNode().boundsInRoot
        val a=compose.onNodeWithTag("diagonal-item-${selected.intValue}").fetchSemanticsNode().boundsInRoot.center-box.topLeft
        val b=compose.onNodeWithTag("diagonal-item-$to").fetchSemanticsNode().boundsInRoot.center-box.topLeft
        track().performTouchInput {
            down(a)
            // Establish horizontal intent before leaving the visual bounds.
            moveTo(Offset(a.x+if(b.x>a.x) 30f else -30f,a.y),60)
            if(finalMove) moveTo(Offset(b.x,y),180) else {
                moveTo(Offset((a.x+b.x)*.5f,y),100)
                updatePointerTo(0,Offset(b.x,y))
            }
            up()
        }
    }
    @Test fun diagonalReleaseAboveTheTrackReproducesTheReportedReturn() {
        show(count=2)
        diagonal(1,-80f)
        assertEquals("A captured horizontal drag must select MP4 when released above the slider",1,selected.intValue)
        assertEquals(listOf(1),calls)
    }
    @Test @Config(sdk=[26,32,35]) fun diagonalsUpAndDownInsideAndOutsideSelectTheLastHorizontalDestination() {
        show()
        for(w in listOf(280,360,600)) for(initial in 0..2) for(target in 0..2) if(initial!=target) {
            for(y in listOf(10f,50f,-90f,160f)) {
                reset(initial,w);diagonal(target,y)
                assertEquals("width=$w initial=$initial target=$target releaseY=$y",target,selected.intValue)
                assertEquals(listOf(target),calls)
            }
        }
    }
    @Test fun rtlAndOutsideFingerUpWithoutAFinalMoveUseHorizontalPosition() {
        show(rtl=true)
        for(initial in 0..2) for(target in 0..2) if(initial!=target) for(y in listOf(-100f,150f)) {
            reset(initial);diagonal(target,y,finalMove=false)
            assertEquals(target,selected.intValue);assertEquals(listOf(target),calls)
        }
    }
    @Test fun leavingAndReenteringTheVisualBoundsDoesNotCancelTheCapturedPointer() {
        show()
        track().performTouchInput {
            down(Offset(width/6f,centerY));moveTo(Offset(width*.4f,centerY),80)
            moveTo(Offset(width*.6f,-100f),80);moveTo(Offset(width*.75f,180f),80)
            moveTo(Offset(width*5/6f,centerY),80);up()
        }
        assertEquals(2,selected.intValue);assertEquals(listOf(2),calls)
    }
    @Test fun realCancelOutsideTheTrackDoesNotCommitAndTheNextDragStillWorks() {
        show()
        track().performTouchInput {
            down(Offset(width/6f,centerY));moveTo(Offset(width*.4f,centerY),80)
            moveTo(Offset(width*5/6f,-100f),100);cancel()
        }
        assertEquals(0,selected.intValue);assertTrue(calls.isEmpty())
        diagonal(2,160f)
        assertEquals(2,selected.intValue);assertEquals(listOf(2),calls)
    }
    @Test fun aSecondFingerCannotReplaceTheCapturedPointer() {
        show()
        track().performTouchInput {
            down(0,Offset(width/6f,centerY));moveTo(0,Offset(width*.4f,centerY),80)
            moveTo(0,Offset(width*5/6f,-90f),100)
            down(1,Offset(width/2f,centerY));up(0);up(1)
        }
        assertEquals(0,selected.intValue);assertTrue(calls.isEmpty())
    }
    @Test fun parentScrollCannotStealAnOwnedDiagonalButVerticalIntentStillScrolls() {
        show(scrollable=true)
        diagonal(2,-100f)
        assertEquals(2,selected.intValue);assertEquals(0,scroll.value)
        reset(0)
        track().performTouchInput { down(center);moveTo(Offset(centerX,centerY-140f),240);up() }
        assertTrue("Vertical intent must still scroll the surrounding screen",scroll.value>30)
        assertEquals(0,selected.intValue);assertTrue(calls.isEmpty())
    }
    @Test fun ownedMovesAreConsumedBeforeAncestorMainPassHandlers() {
        show(observeParent=true)
        diagonal(2,-100f)
        assertEquals(2,selected.intValue);assertEquals(0,parentMoves)
    }
    @Test fun settlingAfterAnOutsideReleaseNeverRestoresTheOldSelection() {
        show();compose.mainClock.autoAdvance=false
        diagonal(2,-90f)
        repeat(12) { compose.mainClock.advanceTimeBy(32);assertEquals(2,selected.intValue) }
        compose.mainClock.advanceTimeBy(2000)
        val pane=compose.onNodeWithTag("glass-selection-capsule").fetchSemanticsNode().boundsInRoot
        val option=compose.onNodeWithTag("diagonal-item-2").fetchSemanticsNode().boundsInRoot
        assertEquals(option.center.x,pane.center.x,2f);assertEquals(listOf(2),calls)
        compose.mainClock.autoAdvance=true
    }
    @Test fun actualFormatZipHistoryAndNavigationAcceptUpAndDownDiagonalReleases() {
        val model=AppModel(RuntimeEnvironment.getApplication()).apply {
            val items=(1..3).map { Media("diagonal-$it","https://youtu.be/123456789ab","Example $it",heights=listOf(1080,720)) }
            inspection=Inspection("Playlist",items,true,listOf(1080,720));selected=items.map { it.id }.toSet()
        }
        compose.setContent { AppContent(model) }
        fun segment(label:String)=compose.onNode(hasTestTag("glass-segment-track") and hasAnyDescendant(hasText(label)))
        fun drag(control:SemanticsNodeInteraction,right:Boolean,upwards:Boolean) {
            control.performTouchInput {
                val start=Offset(if(right) width*.25f else width*.75f,centerY)
                down(start);moveTo(Offset(start.x+if(right) 30f else -30f,centerY),60)
                moveTo(Offset(if(right) width.toFloat() else 0f,if(upwards) -70f else height+70f),180);up()
            }
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("MP4"))
        drag(segment("MP4"),false,true);compose.runOnIdle { assertTrue(model.choice.mp3) }
        drag(segment("MP4"),true,false);compose.runOnIdle { assertFalse(model.choice.mp3) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("ZIP"))
        drag(segment("ZIP"),true,true);compose.runOnIdle { assertTrue(model.choice.zip) }
        drag(segment("ZIP"),false,false);compose.runOnIdle { assertFalse(model.choice.zip) }
        drag(compose.onNodeWithTag("glass-navigation"),true,true);compose.runOnIdle { assertEquals("settings",model.area) }
        drag(compose.onNodeWithTag("glass-navigation"),false,true);compose.runOnIdle { assertEquals("home",model.area) }
        compose.onNodeWithContentDescription("Downloads").performClick()
        drag(segment("Completed"),true,true);compose.onNodeWithText("No completed downloads.").assertIsDisplayed()
        drag(segment("Completed"),false,false);compose.onNodeWithText("Downloads (0)").assertIsSelected()
    }
}
