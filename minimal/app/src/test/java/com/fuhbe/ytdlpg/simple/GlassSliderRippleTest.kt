package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Pixel comparison with Material's actual indication as a positive control.
 * This does not disable or replace the production glass highlight. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassSliderRippleTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun standardRippleIsAbsentButGlassResponseAndOutsideButtonIndicationRemain() {
        val reference=mutableStateOf(false)
        var ordinaryConfig:RippleConfiguration?=null
        compose.setContent { GlassTheme(false) {
            ordinaryConfig=LocalRippleConfiguration.current
            Column {
                GlassSlidingSegments(0,2,{},Modifier.width(360.dp).testTag("ripple-track")) {
                    val items:@Composable RowScope.()->Unit={
                        repeat(2) { i -> Surface(onClick={},color=Color.Transparent,
                            modifier=Modifier.weight(1f).height(56.dp).glassSegmentItem(i)) { Text(if(i==0) "MP3" else "MP4") } }
                    }
                    if(reference.value) CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration(color=Color.Magenta)) { items() }
                    else items()
                }
                CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration(color=Color.Magenta)) {
                    Surface(onClick={},modifier=Modifier.width(360.dp).height(56.dp).testTag("outside-button")) { Text("Outside the sliders") }
                }
            }
        } }
        assertNotNull("Ripple must not be disabled globally",ordinaryConfig)
        val out=File("../../docs/gesture-edge-066/ripple").apply { mkdirs() }
        fun picture(name:String):Bitmap {
            compose.waitForIdle()
            var result:Bitmap?=null
            compose.runOnIdle {
                val view=compose.activity.window.decorView
                result=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(result!!))
                File(out,"$name.png").outputStream().use { result!!.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            return result!!
        }
        val track=compose.onNodeWithTag("ripple-track")
        compose.mainClock.autoAdvance=false
        val idle=picture("idle")
        fun press(name:String):Bitmap {
            track.performTouchInput { down(Offset(width*.25f,centerY)) }
            compose.mainClock.advanceTimeBy(240)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(240))
            val result=picture(name)
            track.performTouchInput { up() };compose.mainClock.advanceTimeBy(1500)
            return result
        }
        val glassOnly=press("glass-only")
        compose.runOnIdle { reference.value=true }
        compose.mainClock.advanceTimeByFrame()
        val withRipple=press("positive-material-reference")
        var colorDelta=0L;var glassChanges=0
        val r=track.fetchSemanticsNode().boundsInRoot
        for(y in r.top.toInt()+8 until r.bottom.toInt()-8)for(x in r.left.toInt()+12 until r.center.x.toInt()-12) {
            val a=glassOnly.getPixel(x,y);val b=withRipple.getPixel(x,y)
            colorDelta+=kotlin.math.abs(android.graphics.Color.red(b)-android.graphics.Color.red(a))+
                kotlin.math.abs(android.graphics.Color.green(b)-android.graphics.Color.green(a))+
                kotlin.math.abs(android.graphics.Color.blue(b)-android.graphics.Color.blue(a))
            if(a!=idle.getPixel(x,y))glassChanges++
        }
        assertTrue("The real glass pressure/reflection response must remain: $glassChanges",glassChanges>100)
        File(out,"positive-control.json").writeText("{\"renderer\":\"Robolectric native Skia software\",\"materialReferencePixelDelta\":$colorDelta,\"glassResponseChangedPixels\":$glassChanges,\"hardwareVerified\":false}")
        // Never treat two identical images as proof that ripple is disabled.
        // Some host RippleDrawable implementations do not render the positive
        // control. Keep functional/configuration checks in the separate test.
        assumeTrue("Host RippleDrawable did not render the positive control; phone pixel acceptance required",colorDelta>1000)
        val button=compose.onNodeWithTag("outside-button")
        val beforeOutside=picture("outside-idle")
        button.performTouchInput { down(center) };compose.mainClock.advanceTimeBy(240)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(240))
        val afterOutside=picture("outside-material-press")
        var outsideChanges=0
        val b=button.fetchSemanticsNode().boundsInRoot
        for(y in b.top.toInt()+8 until b.bottom.toInt()-8)for(x in b.left.toInt()+12 until b.right.toInt()-12)
            if(beforeOutside.getPixel(x,y)!=afterOutside.getPixel(x,y))outsideChanges++
        assertTrue("Ordinary buttons outside these selectors must retain their ripple",outsideChanges>100)
        button.performTouchInput { up() };compose.mainClock.advanceTimeBy(1500)
        compose.mainClock.autoAdvance=true
        File(out,"pixel-result.json").writeText("{\"renderer\":\"Robolectric native Skia software\",\"materialReferencePixelDelta\":$colorDelta,\"glassResponseChangedPixels\":$glassChanges,\"outsideIndicationChangedPixels\":$outsideChanges,\"hardwareVerified\":false}")
    }
    @Test fun disablingOnlySliderIndicationPreservesPressTapAndDragInteractions() {
        val calls=mutableListOf<Int>();val events=mutableListOf<Any>();var outsideClicks=0
        val sliderSource=MutableInteractionSource()
        var inner:RippleConfiguration?=RippleConfiguration();var outside:RippleConfiguration?=null
        compose.setContent { GlassTheme(false) {
            outside=LocalRippleConfiguration.current
            LaunchedEffect(sliderSource) { sliderSource.interactions.collect { events+=it } }
            var selected by remember { mutableIntStateOf(0) }
            Column {
                GlassSlidingSegments(selected,2,{calls+=it;selected=it},Modifier.width(360.dp).testTag("functional-track")) {
                    inner=LocalRippleConfiguration.current
                    repeat(2) { i -> Surface(onClick={calls+=i;selected=i},interactionSource=sliderSource,color=Color.Transparent,
                        modifier=Modifier.weight(1f).height(56.dp).glassSegmentItem(i)) { Text(if(i==0) "MP3" else "MP4") } }
                }
                Surface(onClick={outsideClicks++},modifier=Modifier.width(360.dp).height(56.dp).testTag("ordinary-control")) { Text("Ordinary button") }
            }
        } }
        compose.onNodeWithText("MP4").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        assertNull("Only slider descendants disable the Material indication",inner)
        assertNotNull("The outside Material configuration stays enabled",outside)
        assertTrue("Press interactions must still be emitted",events.any { it is PressInteraction.Press })
        compose.onNodeWithText("MP4").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(1),calls)
        assertTrue(events.any { it is PressInteraction.Release })
        compose.onNodeWithTag("functional-track").performTouchInput {
            down(Offset(width*.75f,centerY));moveTo(Offset(0f,centerY),220);up()
        }
        assertEquals(listOf(1,0),calls)
        assertTrue("Owned drag must cancel the child's click instead of double-dispatching",events.any { it is PressInteraction.Cancel })
        compose.onNodeWithTag("ordinary-control").performTouchInput { click() }
        assertEquals(1,outsideClicks)
    }
}
