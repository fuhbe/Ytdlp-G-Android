package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class GlassTotalInteractionTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val bounds=listOf(2 to Rect(0f,0f,100f,60f),1 to Rect(100f,0f,200f,60f),0 to Rect(200f,0f,300f,60f))
    @Test fun illuminationIsContinuousNormalizedAndWorksInPhysicalRtlOrder() {
        var previous=0f
        for(x in 50..150) {
            val values=bounds.map { glassIllumination(x.toFloat(),it.first,bounds) }
            assertEquals(1f,values.sum(),.00001f)
            assertTrue(values.all { it in 0f..1f })
            assertTrue(values[1]>=previous);previous=values[1]
        }
        assertEquals(.5f,glassIllumination(100f,2,bounds),.0001f)
        assertEquals(.5f,glassIllumination(100f,1,bounds),.0001f)
        assertEquals(0f,glassIllumination(100f,0,bounds),0f)
    }
    @Test fun generousSnappingRecognizesMiddleAndLastBeforeTheirOldRectBoundary() {
        val size=IntSize(300,60)
        assertEquals(1,glassDragTarget(Offset(90f,30f),size,bounds,50f))
        assertEquals(0,glassDragTarget(Offset(190f,30f),size,bounds,150f))
        assertEquals(1,glassDragTarget(Offset(210f,30f),size,bounds,250f))
        assertEquals(2,glassDragTarget(Offset(110f,30f),size,bounds,150f))
        assertEquals(0,glassDragTarget(Offset(190f,-1f),size,bounds,50f))
        assertNull(glassDragTarget(Offset(Float.NaN,30f),size,bounds,50f))
        val gapped=listOf(0 to Rect(0f,0f,90f,60f),1 to Rect(110f,0f,200f,60f))
        assertEquals(1,glassDragTarget(Offset(105f,30f),size,gapped,45f))
    }
    @Test fun edgeRubberBandIsContinuousBoundedAndRelaxesAtTheDestination() {
        assertEquals(150f,glassRubberCenter(150f,50f,250f,100f),0f)
        val left=glassRubberCenter(0f,50f,250f,100f)
        val right=glassRubberCenter(300f,50f,250f,100f)
        assertTrue(left in 41f..50f);assertTrue(right in 250f..259f)
        assertEquals(50f,glassRubberCenter(50f,50f,250f,100f),0f)
        assertEquals(300f-left,right,.0001f)
    }
    @Test @Config(sdk=[26,32,35]) fun wholeTrackCanAcquireTheCapsuleAndCommitOnceAtTolerantMiddle() {
        val calls=mutableListOf<Int>()
        compose.setContent {
            var selected by remember { mutableIntStateOf(2) }
            GlassTheme(false) {
                GlassSlidingSegments(selected,3,{calls+=it;selected=it},Modifier.fillMaxWidth().testTag("total-track")) {
                    repeat(3) { i -> Surface(onClick={calls+=i;selected=i},color=Color.Transparent,
                        modifier=Modifier.weight(1f).height(56.dp).glassSegmentItem(i)) { Text("Option $i") } }
                }
            }
        }
        // Acquire near the first edge despite the selected capsule being at the
        // opposite end. Release before the original first/middle boundary.
        val track=compose.onNodeWithTag("total-track")
        track.performTouchInput { down(Offset(6f,centerY));moveTo(Offset(width*.30f,centerY),200) }
        assertTrue(calls.isEmpty())
        track.performTouchInput { up() }
        assertEquals(listOf(1),calls)
    }
    @Test @Config(sdk=[26,32,35]) fun qualityPanelKeepsExactOptionsAndSelectsOnceThenDismisses() {
        val calls=mutableListOf<Int>()
        compose.setContent { GlassTheme(false) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            GlassQualityPicker(listOf(0,1440,720),0) { calls+=it }
        } } }
        compose.onNodeWithTag("glass-quality-trigger").performClick()
        compose.onNodeWithTag("glass-quality-panel").assertIsDisplayed()
        compose.onNodeWithTag("glass-quality-1440").assertIsDisplayed()
        compose.onAllNodesWithTag("glass-quality-1080").assertCountEquals(0)
        compose.onAllNodesWithTag("glass-quality-2160").assertCountEquals(0)
        compose.onNodeWithTag("glass-quality-720").performClick()
        compose.waitForIdle()
        assertEquals(listOf(720),calls)
        compose.onAllNodesWithTag("glass-quality-panel").assertCountEquals(0)
        compose.onNodeWithTag("glass-quality-trigger").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf(720),calls)
        compose.onAllNodesWithTag("glass-quality-panel").assertCountEquals(0)
    }
    @Test fun qualityPositionFitsShortWindowsAndHonorsRtlAnchor() {
        val provider=GlassQualityPosition(6)
        val pos=provider.calculatePosition(IntRect(90,350,310,404),IntSize(360,440),LayoutDirection.Rtl,IntSize(240,210))
        assertEquals(70,pos.x);assertEquals(134,pos.y);assertEquals(1f,provider.originY,0f)
        val narrow=provider.calculatePosition(IntRect(0,0,60,54),IntSize(160,280),LayoutDirection.Ltr,IntSize(152,200))
        assertTrue(narrow.x>=0 && narrow.y>=0 && narrow.y+200<=280)
    }
    @Test @Config(qualifiers="w320dp-h480dp-mdpi") fun largeFontQualityPopupCanScrollToTheLastExistingResolution() {
        val calls=mutableListOf<Int>()
        compose.setContent { GlassTheme(false) {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(1f,1.8f)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    GlassQualityPicker(listOf(0,2160,1440,1080,720,480,360,240,144),0) { calls+=it }
                }
            }
        } }
        compose.onNodeWithTag("glass-quality-trigger").performClick()
        compose.onNodeWithTag("glass-quality-144").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(144),calls)
        compose.onAllNodesWithTag("glass-quality-panel").assertCountEquals(0)
    }
    @Test fun qualityPanelProducesActualOpenAndCloseFramesInBothThemes() {
        val dark=mutableStateOf(false)
        compose.setContent { GlassTheme(dark.value) {
            Box(Modifier.fillMaxSize().padding(16.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
                Column { GlassQualityPicker(listOf(0,2160,1440,1080,720,480,360),1080) {} }
            }
        } }
        val out=File("../../docs/visual-glass-total-066/quality").apply { mkdirs() }
        fun frame(name:String) {
            compose.waitForIdle()
            compose.runOnIdle {
                val views=android.view.inspector.WindowInspector.getGlobalWindowViews().filter { it.width>0 && it.height>0 }
                assertTrue("A real popup/root view must be rendered",views.isNotEmpty())
                views.forEachIndexed { index,v ->
                    val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
                    v.draw(Canvas(bitmap))
                    File(out,"$name-window$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                }
            }
        }
        for(theme in listOf("light","dark")) {
            compose.runOnIdle { dark.value=theme=="dark" }
            compose.mainClock.autoAdvance=false
            compose.onNodeWithTag("glass-quality-trigger").performClick()
            compose.mainClock.advanceTimeBy(64);frame("$theme-opening")
            compose.mainClock.advanceTimeBy(1200);frame("$theme-open")
            compose.onNodeWithTag("glass-quality-1080").assertIsSelected()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeBy(64);frame("$theme-closing")
            compose.mainClock.advanceTimeBy(1200)
            compose.onAllNodesWithTag("glass-quality-panel").assertCountEquals(0)
            compose.mainClock.autoAdvance=true
        }
    }
    @Test fun qualityBackdropCapturesTheRealScreenOnlyWhileThePopupIsOpen() {
        lateinit var overlay:GlassOverlaySource
        compose.setContent { GlassTheme(false) {
            CompositionLocalProvider(LocalGlassRendering provides GlassRendering.BLUR) {
                GlassOverlayCapture { capture,_ ->
                    overlay=checkNotNull(LocalGlassOverlaySource.current)
                    Column(Modifier.fillMaxSize().then(capture).padding(16.dp)) {
                        Text("Real underlying content")
                        GlassQualityPicker(listOf(0,1080,720),0) {}
                    }
                }
            }
        } }
        compose.runOnIdle { assertEquals(0,overlay.users);assertFalse(overlay.ready) }
        compose.onNodeWithTag("glass-quality-trigger").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            // Exercise the popup's separate window, not just a mock Backdrop.
            android.view.inspector.WindowInspector.getGlobalWindowViews().filter { it.width>0 && it.height>0 }.forEach { v ->
                v.draw(Canvas(Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)))
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1,overlay.users);assertTrue(overlay.ready) }
        compose.onNodeWithTag("glass-quality-720").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0,overlay.users);assertFalse(overlay.ready) }
    }
    @Test fun actualMeshGlyphPixelsIlluminateProgressivelyWithoutRecapturingTheTexture() {
        val source=Bitmap.createBitmap(300,60,Bitmap.Config.ARGB_8888)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE;textSize=32f }
        Canvas(source).drawText("MP3      MP4",22f,42f,paint)
        val texture=source.copy(Bitmap.Config.ARGB_8888,false)
        val rects=listOf(0 to Rect(0f,0f,150f,60f),1 to Rect(150f,0f,300f,60f))
        val ink=GlassSegmentInk(Color.Black,Color.Blue);val lens=GlassMeshLens()
        val out=File("../../docs/visual-glass-total-066/optics").apply { mkdirs() }
        val energies=mutableListOf<Int>()
        for((name,x) in listOf("initial" to 75f,"middle" to 150f,"target" to 225f)) {
            val result=Bitmap.createBitmap(300,60,Bitmap.Config.ARGB_8888)
            val pane=Rect(0f,0f,300f,60f)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(result.asImageBitmap()),androidx.compose.ui.geometry.Size(300f,60f)) {
                lens.drawGlyphs(this,source,pane,.5f,1f,rects,x,ink)
            }
            var blue=0
            for(y in 0 until 60)for(px in 0 until 150) {
                val c=result.getPixel(px,y)
                if(android.graphics.Color.alpha(c)>200) blue+=android.graphics.Color.blue(c)
            }
            energies+=blue
            File(out,"glyph-$name.png").outputStream().use { result.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        assertTrue(energies[0]>energies[1] && energies[1]>energies[2])
        assertTrue("Cached glyph pixels must not be mutated",texture.sameAs(source))
        File(out,"progressive-ink.json").writeText("{\"renderer\":\"Native Skia software, not device GPU\",\"leftBlueEnergy\":$energies,\"sourceUnchanged\":true}")
    }
    @Test fun actualControlCacheSeparatesGlyphsFromBackgroundAndFreezesBothDuringDrag() {
        val stats=GlassRenderStats()
        compose.setContent { GlassTheme(false) {
            CompositionLocalProvider(LocalGlassRendering provides GlassRendering.BLUR,LocalGlassRenderStats provides stats) {
                GlassSlidingSegments(0,2,{},Modifier.fillMaxWidth().testTag("cached-track")) {
                    repeat(2) { index -> Box(Modifier.weight(1f).height(56.dp).glassSegmentItem(index)) {
                        Text(if(index==0) "Actual MP3" else "Actual MP4")
                    } }
                }
            }
        } }
        fun render() {
            compose.runOnIdle { val v=compose.activity.window.decorView;v.draw(Canvas(Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888))) }
            compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        }
        repeat(4) { render() }
        compose.waitUntil(10_000) { stats.lastGlyphSnapshot!=null }
        val glyphs=checkNotNull(stats.lastGlyphSnapshot);val backing=checkNotNull(stats.lastSnapshot)
        var visible=0;var transparent=0
        for(y in 0 until glyphs.height)for(x in 0 until glyphs.width) {
            if(android.graphics.Color.alpha(glyphs.getPixel(x,y))>200)visible++
            if(android.graphics.Color.alpha(glyphs.getPixel(x,y))==0)transparent++
        }
        assertTrue("Real captured glyphs must exist",visible>40)
        assertTrue("Glyph texture must preserve transparency",transparent>visible)
        assertFalse("Background and glyph texture must be separate",backing.sameAs(glyphs))
        val track=compose.onNodeWithTag("cached-track")
        track.performTouchInput { down(Offset(width*.25f,centerY));moveTo(Offset(width*.35f,centerY),100) }
        render();val reads=stats.readbacks;val records=stats.sourceRecords
        repeat(10) { i ->track.performTouchInput { moveTo(Offset(width*(.35f+i*.02f),centerY),16) };render() }
        assertEquals(reads,stats.readbacks);assertEquals(records,stats.sourceRecords)
        assertSame(glyphs,stats.lastGlyphSnapshot);assertSame(backing,stats.lastSnapshot)
        val out=File("../../docs/visual-glass-total-066/optics").apply { mkdirs() }
        File(out,"actual-control-neutral-glyphs.png").outputStream().use { glyphs.compress(Bitmap.CompressFormat.PNG,100,it) }
        track.performTouchInput { cancel() }
    }
}
