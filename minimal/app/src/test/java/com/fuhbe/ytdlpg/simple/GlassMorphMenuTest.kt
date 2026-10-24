package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.*
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
class GlassMorphMenuTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val choices=mutableStateOf(listOf(0,1080,720))
    private val selected=mutableIntStateOf(1080)
    private val calls=mutableListOf<Int>()
    private val top=mutableStateOf(160.dp)
    private val stats=GlassMorphStats()
    private fun content(rtl:Boolean=false,sampled:Boolean=false,large:Boolean=false):@Composable ()->Unit={
        val density=LocalDensity.current
        CompositionLocalProvider(LocalLayoutDirection provides if(rtl)LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalDensity provides Density(density.density,if(large)1.8f else density.fontScale)) {
            GlassTheme(false) { CompositionLocalProvider(LocalGlassMorphStats provides stats,
                LocalGlassRendering provides if(sampled)GlassRendering.BLUR else GlassRendering.TINT) {
                GlassOverlayCapture { capture,_ ->
                    Column(Modifier.fillMaxSize().then(capture).padding(horizontal=16.dp)) {
                        Text("Actual underlying screen text")
                        Spacer(Modifier.height(top.value))
                        GlassQualityPicker(choices.value,selected.intValue) { calls+=it;selected.intValue=it }
                        Text("Content behind the opening menu")
                    }
                }
            } }
        }
    }
    private fun show(rtl:Boolean=false,sampled:Boolean=false,large:Boolean=false) { compose.setContent(content(rtl,sampled,large));compose.waitForIdle() }
    private fun open() { compose.onNodeWithTag("glass-quality-trigger").performClick();compose.waitForIdle() }
    // Popup creation/measure runs on Android's view traversal, separately from
    // Compose's manual animation clock. Drive BOTH before sampling the spring.
    private fun beginManual() {
        compose.onNodeWithTag("glass-quality-trigger").performClick()
        repeat(5) {
            compose.waitForIdle()
            compose.runOnIdle { android.view.inspector.WindowInspector.getGlobalWindowViews().filter { it.width>0 && it.height>0 }.forEach { it.draw(Canvas(Bitmap.createBitmap(it.width,it.height,Bitmap.Config.ARGB_8888))) } }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.waitForIdle()
    }
    private fun close() { compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() };compose.waitForIdle() }
    @Test @Config(sdk=[26,31,32,35]) fun actualChoicesHighlightAndCallbackRemainExact() {
        show();open()
        compose.onNodeWithTag("glass-quality-panel").assertIsDisplayed()
        for(v in choices.value)compose.onNodeWithTag("glass-quality-$v").assertIsDisplayed()
        compose.onAllNodesWithTag("glass-quality-2160").assertCountEquals(0)
        compose.onNodeWithTag("glass-quality-1080").assertIsSelected()
        compose.onNodeWithTag("glass-quality-720").performClick()
        assertEquals(listOf(720),calls);assertEquals(720,selected.intValue)
        compose.onAllNodesWithTag("glass-morph-window").assertCountEquals(0)
        open();compose.onNodeWithTag("glass-quality-720").assertIsSelected();close();assertEquals(listOf(720),calls)
    }
    @Test fun actualBackAndOutsidePointerDismissWithoutChangingSelection() {
        show();open();close();assertTrue(calls.isEmpty());assertFalse(stats.state!!.mounted)
        open();compose.onNodeWithTag("glass-morph-window").performTouchInput { click(Offset(width-3f,height-3f)) }
        compose.waitForIdle();assertFalse(stats.state!!.mounted);assertEquals(1080,selected.intValue);assertTrue(calls.isEmpty())
    }
    @Test fun sameReleasedOptionCannotDispatchTwiceDuringAnimatedClosure() {
        show();open();val node=compose.onNodeWithTag("glass-quality-720").fetchSemanticsNode()
        val action=node.config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { action();action() }
        compose.waitForIdle();assertEquals(listOf(720),calls)
    }
    @Test fun formingItemsCannotActOrClickThrough() {
        show();compose.mainClock.autoAdvance=false
        beginManual()
        val p=stats.placement!!
        compose.onNodeWithTag("glass-morph-window").performTouchInput { click(Offset(p.panel.center.x,p.panel.top+30f)) }
        assertTrue(calls.isEmpty());assertTrue(stats.state!!.expanded)
        compose.onNodeWithTag("glass-morph-window").performTouchInput { click(Offset(width-3f,height-3f)) }
        compose.mainClock.advanceTimeBy(2000);assertFalse(stats.state!!.mounted);assertTrue(calls.isEmpty())
        compose.mainClock.autoAdvance=true
    }
    @Test fun rapidOpenCloseOpenUsesOneWindowAndOneCurrentProgress() {
        show();compose.mainClock.autoAdvance=false
        beginManual()
        assertTrue(stats.state!!.progress.value in .01f.. .99f)
        compose.onNodeWithTag("glass-quality-trigger").performClick();compose.waitForIdle();compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("glass-quality-trigger").performClick();compose.waitForIdle();compose.mainClock.advanceTimeBy(2000)
        assertTrue(stats.state!!.mounted);assertTrue(stats.state!!.expanded);assertEquals(1f,stats.state!!.progress.value,.001f)
        compose.onAllNodesWithTag("glass-morph-window").assertCountEquals(1)
        compose.runOnIdle { stats.state!!.close() };compose.mainClock.advanceTimeBy(2000)
        compose.onAllNodesWithTag("glass-morph-window").assertCountEquals(0)
        assertTrue(calls.isEmpty());compose.mainClock.autoAdvance=true
    }
    @Test fun optionsCanChangeWhileOpenWithoutInventingQualitiesOrRetainingStaleActions() {
        show();open();compose.runOnIdle { choices.value=listOf(0,1440,480) }
        compose.onAllNodesWithTag("glass-quality-720").assertCountEquals(0)
        compose.onNodeWithTag("glass-quality-1440").assertIsDisplayed().performClick()
        assertEquals(listOf(1440),calls);assertEquals(1440,selected.intValue)
    }
    @Test fun physicalRtlMenuBoundsMatchThePlacementInsteadOfDoubleMirroring() {
        show(rtl=true);open()
        val menu=compose.onNodeWithTag("glass-quality-panel").fetchSemanticsNode().boundsInRoot
        val expected=stats.placement!!.panel
        assertEquals(expected.left,menu.left,1f);assertEquals(expected.width,menu.width,1f)
        compose.onNodeWithTag("glass-quality-720").performClick();assertEquals(listOf(720),calls)
    }
    @Test @Config(qualifiers="w320dp-h480dp-mdpi") fun longLargeFontListScrollsToLastActualItem() {
        top.value=10.dp;choices.value=listOf(0,2160,1440,1080,720,480,360,240,144)
        show(large=true);open();compose.onNodeWithTag("glass-quality-144").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(144),calls)
    }
    @Test fun sourceRecordsOnceAndNeverRecapturesTheMorphItself() {
        show(sampled=true);compose.mainClock.autoAdvance=false
        beginManual()
        fun draw()=compose.runOnIdle { android.view.inspector.WindowInspector.getGlobalWindowViews().filter { it.width>0 && it.height>0 }.forEach { it.draw(Canvas(Bitmap.createBitmap(it.width,it.height,Bitmap.Config.ARGB_8888))) } }
        draw();compose.mainClock.advanceTimeByFrame();draw()
        val source=stats.source!!;assertEquals(1,source.users);assertEquals(1,source.frozenUsers);assertTrue(source.ready)
        val records=source.records
        repeat(12) { compose.mainClock.advanceTimeBy(16);draw() }
        assertEquals("Transition progress must not record the underlying UI per frame",records,source.records)
        val extraRecords=source.records-records
        compose.runOnIdle { stats.state!!.close() };compose.mainClock.advanceTimeBy(2000)
        assertEquals(0,source.users);assertEquals(0,source.frozenUsers)
        File("build/reports/glass-067/morph-source.json").apply { parentFile.mkdirs() }.writeText("{\"frames\":12,\"extraSourceRecords\":${extraRecords},\"releasedSourceUsers\":${source.users},\"cpuBitmapReadbacks\":0,\"hostSoftwareOnly\":true}")
        compose.mainClock.autoAdvance=true
    }
    @Test fun restoredCompositionDoesNotRestoreAnInvisibleBlockingWindow() {
        val restore=StateRestorationTester(compose);restore.setContent(content());open()
        restore.emulateSavedInstanceStateRestore();compose.waitForIdle()
        compose.onAllNodesWithTag("glass-morph-window").assertCountEquals(0)
        compose.onNodeWithTag("glass-quality-trigger").assertIsDisplayed();assertEquals(1080,selected.intValue);assertTrue(calls.isEmpty())
    }
    @Test fun clippedAwayAnchorCancelsTheOverlaySafely() {
        show();open();compose.runOnIdle { top.value=2000.dp };compose.waitForIdle()
        compose.onAllNodesWithTag("glass-morph-window").assertCountEquals(0);assertTrue(calls.isEmpty())
    }
}
