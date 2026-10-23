package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
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
import java.io.File
import kotlin.math.roundToInt

/** Actual AppContent frame sequences, not mockup artwork or a download demo.
 * Software captures cannot prove AGSL or the real device frame rate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="w411dp-h1060dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SquareGlassMotionTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun record(dark:Boolean) {
        val model=AppModel(RuntimeEnvironment.getApplication()).apply {
            theme=if(dark) "dark" else "light"
            val items=(1..3).map { Media("motion-$it","https://youtu.be/123456789ab","Example video $it","Example channel",duration=204,heights=listOf(1080,720)) }
            inspection=Inspection("Example playlist",items,true,listOf(1080,720));selected=items.map { it.id }.toSet()
        }
        compose.setContent { AppContent(model) };compose.waitForIdle()
        val directory=File("../../docs/visual-square-065/motion/${if(dark) "dark" else "light"}").apply { mkdirs() }
        fun drag(name:String,control:SemanticsNodeInteraction,fromLabel:String,toLabel:String) {
            val bounds=control.fetchSemanticsNode().boundsInRoot
            val from=compose.onNodeWithText(fromLabel).fetchSemanticsNode().boundsInRoot.center-bounds.topLeft
            val to=compose.onNodeWithText(toLabel).fetchSemanticsNode().boundsInRoot.center-bounds.topLeft
            fun frame(index:Int,state:String) {
                compose.waitForIdle()
                compose.runOnIdle {
                    val view=compose.activity.window.decorView
                    val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    val top=(bounds.top-8).roundToInt().coerceAtLeast(0)
                    val bottom=(bounds.bottom+8).roundToInt().coerceAtMost(view.height)
                    val crop=Bitmap.createBitmap(bitmap,0,top,view.width,bottom-top)
                    File(directory,"$name-$index-$state.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG,100,it) }
                }
            }
            compose.mainClock.autoAdvance=false
            frame(0,"idle")
            control.performTouchInput { down(from);moveTo(from+(to-from)*.35f,100) }
            compose.mainClock.advanceTimeBy(96);frame(1,"pressed")
            control.performTouchInput { moveTo(from+(to-from)*.7f,80) }
            compose.mainClock.advanceTimeBy(64);frame(2,"drag")
            control.performTouchInput { moveTo(to,80) }
            compose.mainClock.advanceTimeBy(80);frame(3,"target-held")
            control.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(64);frame(4,"release")
            compose.mainClock.advanceTimeBy(1500);frame(5,"settled")
            compose.mainClock.autoAdvance=true
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("MP4"))
        drag("format",compose.onAllNodesWithTag("glass-segment-track")[0],"MP4","MP3")
        compose.runOnIdle { assertTrue(model.choice.mp3) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("ZIP"))
        drag("files",compose.onAllNodesWithTag("glass-segment-track")[1],"Normal","ZIP")
        compose.runOnIdle { assertTrue(model.choice.zip) }
        drag("navigation",compose.onNodeWithTag("glass-navigation"),"Home","Downloads")
        compose.runOnIdle { assertEquals("downloads",model.area) }
        drag("history",compose.onNodeWithTag("glass-segment-track"),"Downloads (0)","Completed")
        compose.onNodeWithText("No completed downloads.").assertIsDisplayed()
    }
    @Test fun lightThemeFrames()=record(false)
    @Test fun darkThemeFrames()=record(true)
}
