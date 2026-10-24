package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class ElasticGlassButtonTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var calls=0
    private val enabled=mutableStateOf(true)
    private val stats=ElasticButtonStats()
    private lateinit var scroll:ScrollState
    private lateinit var inputMode:InputModeManager
    private fun show(scrollable:Boolean=false,dark:Boolean=false,sampled:Boolean=false) {
        compose.setContent {
            inputMode=LocalInputModeManager.current
            scroll=rememberScrollState()
            GlassTheme(dark) { CompositionLocalProvider(LocalElasticButtonStats provides stats,
                LocalGlassRendering provides if(sampled)GlassRendering.BLUR else GlassRendering.TINT) {
                Column(Modifier.fillMaxSize().then(if(scrollable)Modifier.verticalScroll(scroll) else Modifier)) {
                    Spacer(Modifier.height(180.dp))
                    GlassButton({calls++},Modifier.width(320.dp).height(60.dp).testTag("elastic-button"),enabled.value,emphasized=true) { Text("Analyze link") }
                    Spacer(Modifier.height(1400.dp))
                }
            } }
        }
        compose.waitForIdle()
    }
    private fun button()=compose.onNodeWithTag("elastic-button")
    @Test fun tapAccessibilityAndKeyboardUseExactlyTheSameSingleCallback() {
        show();button().performTouchInput { click() };assertEquals(1,calls)
        button().performClick();assertEquals(2,calls)
        compose.runOnIdle { compose.activity.window.decorView.dispatchWindowFocusChanged(true);inputMode.requestInputMode(InputMode.Keyboard) }
        button().performSemanticsAction(SemanticsActions.RequestFocus)
        button().performKeyInput { keyDown(Key.Enter);keyUp(Key.Enter) };compose.waitForIdle();assertEquals(3,calls)
        button().performKeyInput { keyDown(Key.Spacebar);keyUp(Key.Spacebar) };compose.waitForIdle();assertEquals(4,calls)
    }
    @Test @Config(sdk=[26,32,35]) fun directionalPullDeformsOnlyMaterialAndNeverClicks() {
        show();val target=button().fetchSemanticsNode().boundsInRoot
        val label=compose.onNodeWithText("Analyze link").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance=false
        for(delta in listOf(Offset(100f,0f),Offset(-100f,0f),Offset(0f,80f),Offset(85f,-70f))) {
            button().performTouchInput { down(center);moveTo(center+delta,160) }
            compose.runOnIdle { assertTrue(stats.motion!!.touching);assertTrue(stats.motion!!.level>.9f);assertTrue(stats.motion!!.delta.getDistance()>20f) }
            assertEquals(target,button().fetchSemanticsNode().boundsInRoot)
            assertEquals(label,compose.onNodeWithText("Analyze link").fetchSemanticsNode().boundsInRoot)
            button().performTouchInput { up() };assertEquals(0,calls)
            compose.mainClock.advanceTimeBy(1800)
            compose.runOnIdle { assertFalse(stats.motion!!.touching);assertEquals(0f,stats.motion!!.level,.001f) }
        }
        assertEquals(4,stats.returnSprings);compose.mainClock.autoAdvance=true
    }
    @Test fun smallVerticalJitterRetainsAValidTap() {
        show();button().performTouchInput { down(center);moveBy(Offset(2f,3f));up() };assertEquals(1,calls)
    }
    @Test fun draggingOutsideAndReturningInsideCannotTurnIntoAClick() {
        show();button().performTouchInput { down(center);moveTo(Offset(width+80f,-70f),140);moveTo(center,140);up() };assertEquals(0,calls)
        button().performTouchInput { click() };assertEquals(1,calls)
    }
    @Test fun actualCancelAndSecondPointerResetWithoutAction() {
        show()
        button().performTouchInput { down(center);moveBy(Offset(70f,20f));cancel() };assertEquals(0,calls)
        button().performTouchInput { down(0,center);down(1,Offset(centerX+20f,centerY));up(1);up(0) };assertEquals(0,calls)
        compose.waitForIdle();assertEquals(0f,stats.motion!!.level,.001f)
        button().performClick();assertEquals(1,calls)
    }
    @Test fun stationaryLongPressDoesNotActBeforeTheNormalRelease() {
        show();button().performTouchInput { down(center);advanceEventTime(800) };assertEquals(0,calls)
        button().performTouchInput { up() };assertEquals(1,calls)
    }
    @Test fun aVerticalAncestorScrollKeepsItsPriority() {
        show(scrollable=true)
        button().performTouchInput { down(center);moveBy(Offset(1f,-140f),240);up() }
        compose.waitForIdle();assertTrue(scroll.value>30);assertEquals(0,calls);assertEquals(0f,stats.motion!!.level,.001f)
    }
    @Test fun disabledAndLoadingTransitionsCancelInput() {
        enabled.value=false;show();button().assertIsNotEnabled().performTouchInput { click() };assertEquals(0,calls)
        compose.runOnIdle { enabled.value=true };button().performTouchInput { down(center) }
        compose.runOnIdle { enabled.value=false };button().performTouchInput { up() }
        compose.waitForIdle();assertEquals(0,calls);assertEquals(0f,stats.motion!!.level,.001f)
    }
    @Test fun moveSamplesDoNotComposeCaptureReadBackOrLaunchSprings() {
        show(sampled=true)
        compose.runOnIdle { val v=compose.activity.window.decorView;v.draw(Canvas(Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888))) }
        compose.waitForIdle();button().performTouchInput { down(center) }
        compose.waitUntil(5000) { stats.readbacks>0 || stats.captureUnavailable }
        val compositions=stats.compositions;val records=stats.sourceRecords;val reads=stats.readbacks;val springs=stats.returnSprings
        repeat(60) { i->button().performTouchInput { moveTo(Offset(centerX+i*1.5f,centerY+(i%7)-3),16) } }
        assertEquals(compositions,stats.compositions);assertEquals(records,stats.sourceRecords);assertEquals(reads,stats.readbacks);assertEquals(springs,stats.returnSprings)
        button().performTouchInput { up() };assertEquals(0,calls);assertEquals(springs+1,stats.returnSprings)
        File("build/reports/glass-067/button-motion.json").apply { parentFile.mkdirs() }.writeText("{\"samples\":60,\"extraCompositions\":${stats.compositions-compositions},\"extraSourceRecords\":${stats.sourceRecords-records},\"extraReadbacks\":${stats.readbacks-reads},\"returnSpringsOnRelease\":1,\"hostSoftwareOnly\":true,\"meshCaptureUnavailable\":${stats.captureUnavailable}}")
    }
    @Test fun realRenderedDarkOutlineChangesWithoutDuplicateLabels() {
        show(dark=true);compose.mainClock.autoAdvance=false
        fun frame(name:String):Bitmap {
            var result:Bitmap?=null
            compose.runOnIdle { val v=compose.activity.window.decorView;result=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(result!!)) }
            File("build/reports/glass-067/button/$name.png").apply { parentFile.mkdirs() }.outputStream().use { result!!.compress(Bitmap.CompressFormat.PNG,100,it) }
            return result!!
        }
        val before=frame("idle");button().performTouchInput { down(center);moveBy(Offset(100f,-50f),160) };val after=frame("held")
        compose.onAllNodesWithText("Analyze link").assertCountEquals(1)
        var changed=0;for(y in 172..248)for(x in 0 until 335)if(before.getPixel(x,y)!=after.getPixel(x,y))changed++
        assertTrue("The actual material outline/highlight must change",changed>100)
        button().performTouchInput { cancel() };compose.mainClock.advanceTimeBy(1800);frame("settled")
        assertEquals(0,calls);compose.mainClock.autoAdvance=true
    }
}
