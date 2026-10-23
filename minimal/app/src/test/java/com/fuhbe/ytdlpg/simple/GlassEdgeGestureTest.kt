package com.fuhbe.ytdlpg.simple

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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

/** Real pointer streams against the unchanged production drawing/layout. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="w800dp-h1100dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassEdgeGestureTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val width=mutableStateOf(360.dp)
    private val selected=mutableIntStateOf(0)
    private val calls=mutableListOf<Int>()
    private fun show(count:Int=3,rtl:Boolean=false,unequal:Boolean=false) {
        compose.setContent { GlassTheme(false) {
            CompositionLocalProvider(LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                GlassSlidingSegments(selected.intValue,count,{calls+=it;selected.intValue=it},Modifier.width(width.value).testTag("edge-track")) {
                    repeat(count) { index ->
                        Surface(onClick={calls+=index;selected.intValue=index},color=Color.Transparent,
                            modifier=Modifier.weight(if(unequal) (index+1).toFloat() else 1f).height(56.dp)
                                .glassSegmentItem(index).testTag("edge-item-$index")) { Text("Option $index") }
                    }
                }
            }
        } }
        compose.waitForIdle()
    }
    private fun track()=compose.onNodeWithTag("edge-track")
    private fun reset(index:Int,w:Int=360) { compose.runOnIdle { width.value=w.dp;selected.intValue=index;calls.clear() };compose.waitForIdle() }
    private fun dragFromSelection(end:(Float)->Float) {
        val bounds=track().fetchSemanticsNode().boundsInRoot
        val start=compose.onNodeWithTag("edge-item-${selected.intValue}").fetchSemanticsNode().boundsInRoot.center-bounds.topLeft
        track().performTouchInput { down(start);moveTo(Offset(end(width.toFloat()),centerY),220);up() }
    }
    @Test fun shellRightBoundaryReproducesTheReportedReturn() {
        show(count=2);reset(0)
        dragFromSelection { it }
        assertEquals("Releasing at the real shell's right boundary must select MP4",1,selected.intValue)
        assertEquals(listOf(1),calls)
    }
    @Test @Config(sdk=[26,32,35]) fun bothEdgesIncludePaddingAndOvershootFromEveryInitialOptionAndWidth() {
        show()
        for(w in listOf(280,360,600)) for(initial in 0..2) for(right in listOf(false,true)) for(delta in listOf(-1f,0f,40f)) {
            reset(initial,w)
            val expected=if(right) 2 else 0
            dragFromSelection { if(right) it+delta else -delta }
            assertEquals("width=$w initial=$initial right=$right delta=$delta",expected,selected.intValue)
            assertEquals(if(initial==expected) emptyList<Int>() else listOf(expected),calls)
        }
    }
    @Test fun aDragCanStartInsideEitherInternalPaddingMargin() {
        show()
        for(w in listOf(280,360,600)) for(right in listOf(false,true)) {
            reset(1,w)
            track().performTouchInput {
                down(Offset(if(right) 1f else width-1f,centerY))
                moveTo(Offset(if(right) width.toFloat() else 0f,centerY),240);up()
            }
            assertEquals(if(right) 2 else 0,selected.intValue)
            assertEquals(listOf(if(right) 2 else 0),calls)
        }
    }
    @Test fun targetCalculationIncludesTheRealMarginsAndKeepsIntermediateTolerance() {
        val bounds=listOf(2 to Rect(8f,0f,88f,56f),1 to Rect(100f,0f,200f,56f),0 to Rect(212f,0f,312f,56f))
        val size=IntSize(320,56)
        for(x in listOf(-80f,-4f,0f,4f,8f)) assertEquals(2,glassDragTarget(Offset(x,28f),size,bounds,150f))
        for(x in listOf(312f,319f,320f,324f,400f)) assertEquals(0,glassDragTarget(Offset(x,28f),size,bounds,150f))
        assertEquals(2,glassDragTarget(Offset(0f,-4f),size,bounds,150f))
        assertEquals(0,glassDragTarget(Offset(320f,60f),size,bounds,150f))
        assertEquals(1,glassDragTarget(Offset(90f,28f),size,bounds,48f))
        assertEquals(0,glassDragTarget(Offset(320f,61f),size,bounds,150f))
        assertEquals(2,glassDragTarget(Offset(0f,-5f),size,bounds,150f))
        assertNull(glassDragTarget(Offset(Float.NaN,28f),size,bounds,150f))
        assertNull(glassDragTarget(Offset(Float.POSITIVE_INFINITY,28f),size,bounds,150f))
        assertNull(glassDragTarget(Offset(0f,28f),IntSize.Zero,bounds,150f))
    }
    @Test fun rtlAndUnequalWidthsUsePhysicalExtremeOptions() {
        show(rtl=true,unequal=true)
        for(w in listOf(280,360,600)) for(initial in 0..2) for(right in listOf(false,true)) {
            reset(initial,w);dragFromSelection { if(right) it+8f else -8f }
            val expected=if(right) 0 else 2
            assertEquals(expected,selected.intValue)
            assertEquals(if(initial==expected) emptyList<Int>() else listOf(expected),calls)
        }
    }
    @Test fun releaseUsesTheFinalFingerPositionEvenWithoutAFinalMoveEvent() {
        show();reset(1)
        track().performTouchInput {
            down(Offset(width*.5f,centerY));moveTo(Offset(width*.6f,centerY),100)
            updatePointerTo(0,Offset(width.toFloat(),centerY));up()
        }
        assertEquals(2,selected.intValue);assertEquals(listOf(2),calls)
    }
    @Test fun elasticReturnSettlesAtTheConfirmedNewOptionAtBothEnds() {
        show()
        for(right in listOf(true,false)) {
            reset(1)
            compose.mainClock.autoAdvance=false
            dragFromSelection { if(right) it else 0f }
            val expected=if(right) 2 else 0
            repeat(8) {
                compose.mainClock.advanceTimeBy(32)
                assertEquals("The release animation must never select the previous option",expected,selected.intValue)
                val pane=compose.onNodeWithTag("glass-selection-capsule").fetchSemanticsNode().boundsInRoot
                val middle=compose.onNodeWithTag("edge-item-1").fetchSemanticsNode().boundsInRoot.center.x
                assertTrue(if(right) pane.center.x>middle else pane.center.x<middle)
            }
            compose.mainClock.advanceTimeBy(2000)
            val pane=compose.onNodeWithTag("glass-selection-capsule").fetchSemanticsNode().boundsInRoot
            val destination=compose.onNodeWithTag("edge-item-$expected").fetchSemanticsNode().boundsInRoot
            assertEquals(destination.center.x,pane.center.x,2f)
            assertEquals(listOf(expected),calls)
            compose.mainClock.autoAdvance=true
        }
    }
    @Test fun cancelledDragsNeverCommitButVerticalEscapeKeepsHorizontalSelection() {
        show();reset(1)
        track().performTouchInput { down(center);moveTo(Offset(width.toFloat(),centerY),200);cancel() }
        assertEquals(1,selected.intValue);assertTrue(calls.isEmpty())
        track().performTouchInput { down(center);moveTo(Offset(0f,centerY),200);moveTo(Offset(0f,-30f),100);up() }
        assertEquals(0,selected.intValue);assertEquals(listOf(0),calls)
    }
    private fun actualWindowEdges() {
        val screen=RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp
        width.value=screen.dp
        show()
        assertEquals(screen.toFloat(),track().fetchSemanticsNode().boundsInRoot.width,2f)
        for(initial in 0..2) for(right in listOf(false,true)) {
            reset(initial,screen);dragFromSelection { if(right) it else 0f }
            assertEquals(if(right) 2 else 0,selected.intValue)
        }
    }
    @Test @Config(qualifiers="w320dp-h640dp-mdpi") fun edgesOnSmallPhoneWindow()=actualWindowEdges()
    @Test @Config(qualifiers="w411dp-h891dp-mdpi") fun edgesOnRegularPhoneWindow()=actualWindowEdges()
    @Test @Config(qualifiers="w800dp-h1100dp-mdpi") fun edgesOnTabletWindow()=actualWindowEdges()
    @Test fun actualFormatZipHistoryAndNavigationKeepBothEdgeSelections() {
        val model=AppModel(RuntimeEnvironment.getApplication()).apply {
            val items=(1..3).map { Media("edge-$it","https://youtu.be/123456789ab","Example $it",heights=listOf(1080,720)) }
            inspection=Inspection("Playlist",items,true,listOf(1080,720));selected=items.map { it.id }.toSet()
        }
        compose.setContent { AppContent(model) }
        fun segment(label:String)=compose.onNode(hasTestTag("glass-segment-track") and hasAnyDescendant(hasText(label)))
        fun edge(control:SemanticsNodeInteraction,right:Boolean) {
            control.performTouchInput { down(center);moveTo(Offset(if(right) width.toFloat() else 0f,centerY),240);up() }
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("MP4"))
        edge(segment("MP4"),false);compose.runOnIdle { assertTrue(model.choice.mp3) }
        edge(segment("MP4"),true);compose.runOnIdle { assertFalse(model.choice.mp3) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("ZIP"))
        edge(segment("ZIP"),true);compose.runOnIdle { assertTrue(model.choice.zip) }
        edge(segment("ZIP"),false);compose.runOnIdle { assertFalse(model.choice.zip) }
        edge(compose.onNodeWithTag("glass-navigation"),true);compose.runOnIdle { assertEquals("settings",model.area) }
        edge(compose.onNodeWithTag("glass-navigation"),false);compose.runOnIdle { assertEquals("home",model.area) }
        compose.onNodeWithContentDescription("Downloads").performClick()
        edge(segment("Completed"),true)
        compose.onNodeWithText("No completed downloads.").assertIsDisplayed()
        edge(segment("Completed"),false)
        compose.onNodeWithText("Downloads (0)").assertIsSelected()
    }
}
