package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Captures actual application/Popup windows, never substitutes drawn UI.
 * Sample metadata only; native Skia SOFTWARE host, NOT a physical GPU capture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="en-w411dp-h1100dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Glass067CaptureTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val buttons=ElasticButtonStats();private val menu=GlassMorphStats()
    private fun show(dark:Boolean,inspected:Boolean=false):AppModel {
        val model=AppModel(RuntimeEnvironment.getApplication()).apply {
            theme=if(dark)"dark" else "light"
            url="https://youtu.be/123456789ab"
            if(inspected) {
                val m=Media("sample-landscape","https://youtu.be/123456789ab","Mountain lake at sunrise","Sample channel",
                    thumb="android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.glass_wallpaper_light}",duration=204,heights=listOf(1080,720))
                inspection=Inspection(m.title,listOf(m),false,listOf(1080,720));selected=setOf(m.id);choice=Choice(height=1080)
            }
        }
        compose.setContent { CompositionLocalProvider(LocalElasticButtonStats provides buttons,LocalGlassMorphStats provides menu) { AppContent(model) } }
        compose.waitForIdle();compose.mainClock.autoAdvance=false;return model
    }
    private fun frame(group:String,name:String) {
        compose.runOnIdle {
            val root=compose.activity.window.decorView
            val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(image)
            root.draw(canvas)
            val origin=IntArray(2);root.getLocationOnScreen(origin)
            for(view in WindowInspector.getGlobalWindowViews().filter { it!==root && it.width>0 && it.height>0 }) {
                val xy=IntArray(2);view.getLocationOnScreen(xy)
                canvas.save();canvas.translate((xy[0]-origin[0]).toFloat(),(xy[1]-origin[1]).toFloat());view.draw(canvas);canvas.restore()
            }
            val folder=File("build/reports/glass-067/captures/$group").apply { mkdirs() }
            File(folder,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            val p=menu.state?.progress?.value ?: 0f
            File(folder,"$name.json").writeText(JSONObject().put("version",BuildConfig.VERSION_NAME).put("sampleData",true)
                .put("physicalDevice",false).put("renderer","Actual Compose windows; Robolectric native Skia software")
                .put("progress",p).put("neckConnected",menu.placement?.let { glassMorphFrame(it,p,1f).connected } ?: false)
                .put("buttonPressure",buttons.motion?.level ?: 0f).toString(2))
        }
    }
    private fun button(dark:Boolean) {
        val model=show(dark);val group="button-${if(dark)"dark" else "light"}"
        val button=compose.onNodeWithText("Analyze link")
        frame(group,"00-idle")
        button.performTouchInput { down(center) };compose.runOnIdle { assertTrue(buttons.motion!!.touching);assertEquals(1f,buttons.motion!!.level,0f) };frame(group,"01-pressed")
        button.performTouchInput { moveBy(Offset(55f,0f),90) };frame(group,"02-horizontal-pull")
        button.performTouchInput { moveBy(Offset(30f,-34f),90) };frame(group,"03-diagonal-pull")
        button.performTouchInput { up() };frame(group,"04-release")
        compose.mainClock.advanceTimeBy(64);frame(group,"05-return-64ms")
        compose.mainClock.advanceTimeBy(96);frame(group,"06-return-160ms")
        compose.mainClock.advanceTimeBy(1800);frame(group,"07-settled")
        assertFalse(model.busy);assertNull(model.inspection);assertEquals("",model.status);assertEquals("",model.error)
        compose.mainClock.autoAdvance=true
    }
    private fun morph(dark:Boolean) {
        val model=show(dark,true);val group="menu-${if(dark)"dark" else "light"}"
        compose.onNodeWithTag("glass-quality-trigger").performScrollTo();compose.waitForIdle()
        frame(group,"00-closed")
        compose.onNodeWithTag("glass-quality-trigger").performClick();compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle();frame(group,"01-opening")
        compose.mainClock.advanceTimeBy(32);frame(group,"02-bridge-start")
        compose.mainClock.advanceTimeBy(32);frame(group,"03-bridge-middle")
        compose.mainClock.advanceTimeBy(48);frame(group,"04-panel-formation")
        compose.mainClock.advanceTimeBy(2000);frame(group,"05-open")
        compose.onNodeWithTag("glass-quality-1080").assertIsSelected()
        compose.onAllNodesWithTag("glass-quality-2160").assertCountEquals(0)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(48);frame(group,"06-closing-bridge")
        compose.mainClock.advanceTimeBy(48);frame(group,"07-closing-merge")
        compose.mainClock.advanceTimeBy(2000);frame(group,"08-closed")
        assertFalse(menu.state!!.mounted);assertEquals(1080,model.choice.height);assertFalse(model.choice.mp3)
        compose.mainClock.autoAdvance=true
    }
    @Test fun actualButtonLightSequence()=button(false)
    @Test fun actualButtonDarkSequence()=button(true)
    @Test fun actualQualityLightSequence()=morph(false)
    @Test fun actualQualityDarkSequence()=morph(true)
}
