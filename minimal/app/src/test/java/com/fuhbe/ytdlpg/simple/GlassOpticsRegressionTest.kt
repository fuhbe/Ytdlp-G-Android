package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.fuhbe.ytdlpg.simple.squareglass.DampedDragAnimation
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
class GlassOpticsRegressionTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun reproducePreviousSpringLagAndCompareTheSameInputOnTheSameClock() {
        lateinit var old:LegacyGlassMotionFixture
        lateinit var fixed:DampedDragAnimation
        compose.setContent {
            val scope=rememberCoroutineScope()
            old=remember { LegacyGlassMotionFixture(scope,.5f) }
            fixed=remember { DampedDragAnimation(scope,.5f) }
        }
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { old.press();fixed.press();old.updateValue(.8f);fixed.updateValue(.8f,1000) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertEquals(.8f,fixed.value,0f)
            assertTrue("Reproduce the original animation chasing the finger",kotlin.math.abs(old.value-.8f)>.02f)
            File("build/reports/glass-066").apply { mkdirs() }.resolve("spring-lag.json").writeText(
                "{\"clock\":\"deterministic Compose test clock, not measured device latency\",\"target\":0.8,\"advancedMs\":32,\"oldValue\":${old.value},\"fixedValue\":${fixed.value}}")
            old.animateToValue(.8f);fixed.animateToValue(.8f)
        }
        compose.mainClock.advanceTimeBy(2000);compose.mainClock.autoAdvance=true
    }
    @Test fun everyInputSampleIsAppliedBeforeAnyAnimationFrame() {
        lateinit var motion:DampedDragAnimation
        compose.setContent { val scope=rememberCoroutineScope();motion=remember { DampedDragAnimation(scope,.5f) } }
        compose.mainClock.autoAdvance=false
        compose.runOnIdle {
            motion.press()
            repeat(240) { i ->
                val target=(i%101)/100f
                motion.updateValue(target,1000L+i*8)
                assertEquals("Held input must not chase a spring",target,motion.value,0f)
            }
            motion.animateToValue(.25f)
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(.25f,motion.value,.002f);assertEquals(1f,motion.scaleX,.002f) }
        compose.mainClock.autoAdvance=true
    }
    @Test fun heldMotionDoesNotRecomposeLabelsOrReadBackTheSourcePerSample() {
        val stats=GlassRenderStats()
        compose.setContent {
            var selected by remember { mutableIntStateOf(0) }
            GlassTheme(false) {
                CompositionLocalProvider(LocalGlassRenderStats provides stats,LocalGlassRendering provides GlassRendering.BLUR) {
                    GlassSlidingSegments(selected,2,{selected=it},Modifier.fillMaxWidth().testTag("motion-track")) {
                        repeat(2) { index -> Surface(onClick={selected=index},color=androidx.compose.ui.graphics.Color.Transparent,modifier=Modifier.weight(1f).height(56.dp).glassSegmentItem(index)) { Text(if(index==0) "MP3" else "MP4") } }
                    }
                }
            }
        }
        compose.waitForIdle()
        fun render() {
            compose.runOnIdle {
                val view=compose.activity.window.decorView
                view.draw(Canvas(Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)))
            }
            compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        }
        repeat(4) { render() }
        compose.waitUntil(10_000) { stats.readbacks>0 }
        assertTrue("The actual source layer must have been recorded",stats.sourceRecords>0)
        assertTrue("The cached mesh source must be populated",stats.readbacks>0)
        val snapshot=checkNotNull(stats.lastSnapshot)
        assertTrue("Actual source glyphs must be present in the snapshot",(0 until snapshot.width).any { android.graphics.Color.alpha(snapshot.getPixel(it,snapshot.height/2))>0 })
        val optical=File("../../docs/visual-glass-066/optical-probe").apply { mkdirs() }
        File(optical,"actual-control-source.png").outputStream().use { snapshot.compress(Bitmap.CompressFormat.PNG,100,it) }
        val track=compose.onNodeWithTag("motion-track")
        track.performTouchInput { down(Offset(width*.25f,centerY));moveTo(Offset(width*.35f,centerY),50) }
        compose.waitForIdle()
        val compositions=stats.compositions;val records=stats.sourceRecords;val copies=stats.readbacks
        repeat(60) { i ->
            track.performTouchInput { moveTo(Offset(width*(.35f+i*.005f),centerY),16) }
            render()
        }
        compose.waitForIdle()
        assertEquals("No composition per pointer update",compositions,stats.compositions)
        assertEquals("No source recording per pointer update",records,stats.sourceRecords)
        assertEquals("No GPU readback during held motion",copies,stats.readbacks)
        File("build/reports/glass-066").apply { mkdirs() }.resolve("invalidation.json").writeText(
            "{\"inputSamples\":60,\"extraCompositions\":${stats.compositions-compositions},\"extraSourceRecords\":${stats.sourceRecords-records},\"extraReadbacks\":${stats.readbacks-copies},\"hostSoftwareOnly\":true}")
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val output=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(output))
            File(optical,"actual-control-held.png").outputStream().use { output.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        track.performTouchInput { cancel() }
    }
    @Test fun actualMeshMovesSourceTextPixelsAndKeepsTheExteriorUnchanged() {
        val width=384;val height=96
        val source=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(source);canvas.drawColor(android.graphics.Color.rgb(225,240,250))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.BLUE;textSize=32f }
        canvas.drawText("MP3 Downloads MP4",12f,60f,paint)
        paint.color=android.graphics.Color.BLACK
        for(x in 0 until width step 12)canvas.drawLine(x.toFloat(),0f,x.toFloat(),height.toFloat(),paint)
        val lens=GlassMeshLens();val pane=Rect(110f,0f,274f,96f)
        lens.update(width,height,pane,1f)
        val result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmapMesh(source,lens.columns,lens.rows,lens.vertices,0,null,0,paint)
        var changed=0;var exterior=0
        for(y in 3 until height-3)for(x in 3 until width-3) {
            val a=source.getPixel(x,y);val b=result.getPixel(x,y)
            if(a!=b && x in 125..259)changed++
            if(a!=b && (x<80 || x>304))exterior++
        }
        assertTrue("The rendered source pixels, not just a gradient, must move",changed>100)
        assertEquals("Outside optical region must remain unchanged",0,exterior)
        val directory=File("../../docs/visual-glass-066/optical-probe").apply { mkdirs() }
        for((name,image) in listOf("unwarped" to source,"mesh-warped" to result))
            File(directory,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        File(directory,"result.json").writeText("{\"renderer\":\"Robolectric native Skia software Canvas\",\"changedInteriorPixels\":$changed,\"changedExteriorPixels\":$exterior,\"hardwareVerified\":false}")
    }
    @Test fun meshIsFiniteAndReusableForReversedAndOffEdgeDrags() {
        val lens=GlassMeshLens();val storage=lens.vertices
        lens.update(400,80,Rect(80f,0f,240f,80f),1f,0f)
        for(y in 0..lens.rows)for(x in 0..lens.columns) {
            val i=(y*(lens.columns+1)+x)*2
            assertEquals(400f*x/lens.columns,lens.vertices[i],.0001f)
            assertEquals(80f*y/lens.rows,lens.vertices[i+1],.0001f)
        }
        for(x in listOf(-50f,0f,100f,300f,450f)) {
            lens.update(400,80,Rect(x-80,-4f,x+80,84f),.7f)
            assertSame(storage,lens.vertices)
            assertTrue(lens.vertices.all { it.isFinite() })
            for(y in 0..lens.rows)for(i in 0 until lens.columns) {
                val left=(y*(lens.columns+1)+i)*2
                assertTrue("Mesh must never fold",lens.vertices[left+2]>lens.vertices[left])
            }
        }
    }
}
