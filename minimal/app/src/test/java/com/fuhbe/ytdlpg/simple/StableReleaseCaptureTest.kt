package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.inspector.WindowInspector
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

/** Genuine final application windows with explicitly documented sample metadata.
 * This fixture never analyzes a URL, starts a download or opens a saved file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="en-w411dp-h1100dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StableReleaseCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun sample(i:Int)=Media("stable-sample-$i","https://youtu.be/123456789ab",
        listOf("Mountain lake at sunrise","Quiet forest walk","Evening by the river")[i-1],
        "Sample channel",thumb="android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.glass_wallpaper_light}",
        duration=(180+i*24).toLong(),heights=listOf(2160,1440,1080,720))
    private fun capture(screen:String,dark:Boolean) {
        assertEquals("0.6.7-test2",BuildConfig.VERSION_NAME)
        val app=RuntimeEnvironment.getApplication()
        val model=AppModel(app).apply { theme=if(dark) "dark" else "light" }
        val items=(1..3).map(::sample)
        model.url=items.first().url
        if(screen !in listOf("home","downloads","completed","settings","about")) {
            val playlist=screen=="playlist"
            val shown=if(playlist) items else items.take(1)
            model.inspection=Inspection(if(playlist) "Calm landscapes" else shown.first().title,shown,playlist,listOf(2160,1440,1080,720))
            model.selected=shown.map { it.id }.toSet()
            model.choice=Choice(mp3=screen=="mp3" || playlist,height=1080,zip=playlist)
        }
        if(screen in listOf("downloads","completed")) {
            model.area="downloads"
            Store.insert(QueueJob(id="stable-running",title="Calm landscapes",items=items,
                choice=Choice(mp3=true,zip=true),folder="",state="RUNNING",progress=.64f,current=1,
                phase="Downloading audio…",speed=2_400_000,eta=18,indeterminate=false,
                records=items.mapIndexed { i,m-> ItemRecord(i,m.id,state=if(i==0) "DONE" else if(i==1) "RUNNING" else "PENDING",indeterminate=false) }))
            Store.insert(QueueJob(id="stable-paused",title=items[1].title,items=listOf(items[1]),
                choice=Choice(height=1080),folder="",state="PAUSED",progress=.38f,
                phase="Paused — partial data preserved",indeterminate=false))
            val saved=SavedFile(items.last().id,"/sample/river.mp4",bytes=18_400_000,title=items.last().title,thumb=items.last().thumb,height=1080)
            Store.insert(QueueJob(id="stable-done",title=saved.title,items=listOf(items.last()),
                choice=Choice(height=1080),folder="",state="DONE",progress=1f,current=1,
                phase="Completed",files=listOf(saved),indeterminate=false))
        }
        if(screen in listOf("settings","about"))model.area="settings"
        compose.setContent { AppContent(model) }
        compose.waitForIdle()
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
        when(screen) {
            "home"->compose.onNodeWithText("Analyze link").assertIsDisplayed()
            "mp3"->compose.onNodeWithText("MP3").assertIsSelected()
            "mp4"->compose.onNodeWithText("MP4").assertIsSelected()
            "quality"->{
                compose.onNodeWithTag("glass-quality-trigger").performClick()
                compose.onNodeWithTag("glass-quality-panel").assertIsDisplayed()
                compose.onNodeWithTag("glass-quality-1080").assertIsSelected()
            }
            "completed"->{
                compose.onNodeWithTag("glass-segment-track").performTouchInput { click(androidx.compose.ui.geometry.Offset(width*.75f,centerY)) }
                compose.onNodeWithText("Quiet forest walk").assertDoesNotExist()
                compose.onNodeWithText("Evening by the river").assertIsDisplayed()
            }
            "settings"->compose.onAllNodesWithText("Appearance").assertCountEquals(1)
            "about"->{
                compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Licenses and source code"))
                compose.onNodeWithText("Ytdlp-G 0.6.7-test2").assertIsDisplayed()
            }
        }
        compose.waitForIdle()
        repeat(2) {
            compose.runOnIdle {
                WindowInspector.getGlobalWindowViews().filter { it.width>0 && it.height>0 }.forEach { view ->
                    view.draw(Canvas(Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)))
                }
            }
            compose.waitForIdle()
        }
        // Draw the actual WindowManager roots, including the separate Popup
        // window at its measured position; never redraw custom interface shapes.
        compose.runOnIdle {
            val root=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            root.draw(canvas)
            val roots=WindowInspector.getGlobalWindowViews().filter { it!==root && it.width>0 && it.height>0 }
            val origin=IntArray(2);root.getLocationOnScreen(origin)
            if(screen=="quality")assertTrue("The real quality popup must exist",roots.isNotEmpty())
            for(view in roots) {
                val location=IntArray(2);view.getLocationOnScreen(location)
                canvas.save();canvas.translate((location[0]-origin[0]).toFloat(),(location[1]-origin[1]).toFloat())
                view.draw(canvas);canvas.restore()
            }
            val out=File("build/reports/release-066/${screen}-${if(dark) "dark" else "light"}.png").apply { parentFile.mkdirs() }
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        assertFalse(compose.activity.isFinishing)
    }
    @Test fun homeLight()=capture("home",false)
    @Test fun homeDark()=capture("home",true)
    @Test fun mp3Light()=capture("mp3",false)
    @Test fun mp3Dark()=capture("mp3",true)
    @Test fun mp4Light()=capture("mp4",false)
    @Test fun mp4Dark()=capture("mp4",true)
    @Test fun qualityLight()=capture("quality",false)
    @Test fun qualityDark()=capture("quality",true)
    @Test fun playlistLight()=capture("playlist",false)
    @Test fun playlistDark()=capture("playlist",true)
    @Test fun downloadsLight()=capture("downloads",false)
    @Test fun downloadsDark()=capture("downloads",true)
    @Test fun completedLight()=capture("completed",false)
    @Test fun completedDark()=capture("completed",true)
    @Test fun settingsLight()=capture("settings",false)
    @Test fun settingsDark()=capture("settings",true)
    @Test fun aboutLight()=capture("about",false)
    @Test fun aboutDark()=capture("about",true)
}
