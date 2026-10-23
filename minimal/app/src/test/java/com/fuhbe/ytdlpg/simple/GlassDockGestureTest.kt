package com.fuhbe.ytdlpg.simple

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31],application=SimpleApp::class,qualifiers="w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class GlassDockGestureTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val calls=mutableListOf<String>()
    private lateinit var inputMode:InputModeManager
    private fun show(initial:String="home",large:Boolean=false) {
        compose.setContent {
            inputMode=LocalInputModeManager.current
            var area by remember { mutableStateOf(initial) }
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large) 2f else 1f)) {
                GlassTheme(false) {
                    Box(Modifier.fillMaxSize().padding(bottom=24.dp),contentAlignment=Alignment.BottomCenter) {
                        GlassNavigationDock(area,
                            { calls+="home";area="home" },{ calls+="downloads";area="downloads" },
                            { calls+="settings";area="settings" },source=null,modifier=Modifier.fillMaxWidth())
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag("glass-navigation-capsule").assertCountEquals(1)
    }
    private fun dock()=compose.onNodeWithTag("glass-navigation")
    @Test fun normalTapsAndSelectedIndicatorKeepTheirOriginalCallbacks() {
        show()
        compose.onNodeWithContentDescription("Back").assertHasNoClickAction()
        compose.onNodeWithContentDescription("Downloads").performClick()
        compose.onNodeWithContentDescription("Downloads").assertIsSelected().assertHasNoClickAction()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("downloads","settings","home"),calls)
    }
    @Test @Config(sdk=[26,28,31,33,35]) fun horizontalDragDispatchesOnlyTheReleasedDestinationAcrossApis() {
        show()
        dock().performTouchInput { swipe(Offset(width/6f,centerY),Offset(width*5/6f,centerY),300) }
        assertEquals(listOf("settings"),calls)
        compose.onNodeWithContentDescription("Settings").assertIsSelected()
        dock().performTouchInput { swipe(Offset(width*5/6f,centerY),Offset(width/2f,centerY),300) }
        assertEquals(listOf("settings","downloads"),calls)
    }
    @Test fun reverseDragUsesTheSameHomeCallback() {
        show("settings")
        dock().performTouchInput { swipe(Offset(width*5/6f,centerY),Offset(width/6f,centerY),300) }
        assertEquals(listOf("home"),calls)
    }
    @Test fun cancelledDragReturnsToTheCurrentSelection() {
        show()
        dock().performTouchInput { down(Offset(width/6f,centerY));moveTo(Offset(width*5/6f,centerY),200);cancel() }
        assertTrue(calls.isEmpty())
        compose.onNodeWithContentDescription("Back").assertIsSelected()
    }
    @Test fun capturedDragReleasedOutsideTheDockKeepsItsHorizontalDestination() {
        show()
        dock().performTouchInput {
            down(Offset(width/6f,centerY));moveTo(Offset(width*5/6f,centerY),200)
            moveTo(Offset(width*5/6f,-20f),100);up()
        }
        assertEquals(listOf("settings"),calls)
        compose.onNodeWithContentDescription("Settings").assertIsSelected()
    }
    @Test fun releaseBeyondRightEdgeSelectsTheExtremeDestination() {
        show()
        dock().performTouchInput { down(Offset(width/6f,centerY));moveTo(Offset(width+20f,centerY),300);up() }
        assertEquals(listOf("settings"),calls)
        compose.onNodeWithContentDescription("Settings").assertIsSelected()
    }
    @Test fun verticalGestureDoesNotChangeDestinations() {
        show()
        dock().performTouchInput { swipe(Offset(width/2f,centerY),Offset(width/2f,-80f),300) }
        assertTrue(calls.isEmpty())
    }
    @Test fun gestureStartingOutsideTheDockCannotNavigate() {
        show()
        compose.onRoot().performTouchInput { swipe(Offset(20f,100f),Offset(width-20f,100f),300) }
        assertTrue(calls.isEmpty())
    }
    @Test fun returningToTheCurrentDestinationDoesNotInvokeItsCallback() {
        show()
        dock().performTouchInput { down(Offset(width/6f,centerY));moveTo(Offset(width*5/6f,centerY),200);moveTo(Offset(width/6f,centerY),200);up() }
        assertTrue(calls.isEmpty())
    }
    @Test fun keyboardEnterStillUsesTheFocusedDestination() {
        show()
        compose.runOnIdle { compose.activity.window.decorView.dispatchWindowFocusChanged(true);inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithContentDescription("Downloads").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithContentDescription("Downloads").assertIsFocused()
        compose.onNodeWithContentDescription("Downloads").performKeyInput { keyDown(Key.Enter);keyUp(Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf("downloads"),calls)
    }
    @Test @Config(qualifiers="w320dp-h640dp-mdpi") fun largeFontAndSmallViewportKeepTheDockTouchable() {
        show(large=true)
        dock().assertIsDisplayed()
        dock().performTouchInput { swipe(Offset(width/6f,centerY),Offset(width*5/6f,centerY),300) }
        assertEquals(listOf("settings"),calls)
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
    @Test fun capsuleMovesThroughIntermediatePositionsAndSettles() {
        show()
        compose.mainClock.autoAdvance=false
        val before=compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        val middle=compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(1500)
        val after=compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Capsule must actually move: $before -> $middle -> $after, calls=$calls",middle>before)
        assertTrue("Spring must have an intermediate frame: $before -> $middle -> $after",middle<after)
        compose.mainClock.autoAdvance=true
    }
    @Test fun invalidCoordinatesAndBoundaryHitsAreHandledSafely() {
        val size=IntSize(300,60)
        assertEquals(0,glassDockTarget(Offset(0f,0f),size))
        assertEquals(1,glassDockTarget(Offset(100f,30f),size))
        assertEquals(2,glassDockTarget(Offset(299.9f,59.9f),size))
        for(p in listOf(Offset(-1f,0f),Offset(300f,30f),Offset(30f,-1f),Offset(30f,60f),Offset(Float.NaN,0f))) assertNull(glassDockTarget(p,size))
        assertNull(glassDockTarget(Offset.Zero,IntSize.Zero))
    }
    @Test fun capsuleFollowsTheFingerBeforeDispatchingTheRelease() {
        show()
        val before=compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot.left
        dock().performTouchInput { down(Offset(width/6f,centerY));moveTo(Offset(width*5/6f,centerY),300) }
        compose.waitForIdle()
        val during=compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot.left
        assertTrue(during>before)
        assertTrue(calls.isEmpty())
        dock().performTouchInput { up() }
        assertEquals(listOf("settings"),calls)
    }
    @Test fun theReservedSystemNavigationAreaIsNotIntercepted() {
        show()
        compose.onRoot().performTouchInput { swipe(Offset(20f,height-1f),Offset(width-20f,height-1f),300) }
        assertTrue(calls.isEmpty())
    }
}
