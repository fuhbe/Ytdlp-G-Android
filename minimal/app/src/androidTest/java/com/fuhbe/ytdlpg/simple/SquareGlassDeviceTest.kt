package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Run on a real hardware-accelerated API33+ window. Compilation is NOT a pass.
 * Compare the SAME production component with lens disabled/enabled, then move
 * it onto another source region. Images and differences are device evidence. */
@RunWith(AndroidJUnit4::class)
class SquareGlassDeviceTest {
    @Test @SdkSuppress(minSdkVersion=33) fun movingProductionPuckActuallyRefractsItsCapturedBackdrop() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val device=UiDevice.getInstance(instrumentation)
        val calls=AtomicInteger(0)
        val selected=mutableIntStateOf(0)
        val useLens=mutableStateOf(false)
        val rects=listOf(AtomicReference<RectF>(),AtomicReference<RectF>())
        val output=File(context.getExternalFilesDir(null),"square-glass-device").apply { mkdirs() }
        // Exercise the very shader shipped by the unchanged native dependency.
        RuntimeShader(instrumentation.context.assets.open("backdrop-lens.agsl").bufferedReader().use { it.readText() })
        fun capture(name:String):Bitmap {
            instrumentation.waitForIdleSync();device.waitForIdle(1000);SystemClock.sleep(300)
            return checkNotNull(instrumentation.uiAutomation.takeScreenshot()).also { bitmap ->
                File(output,"$name-api${Build.VERSION.SDK_INT}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.isHardwareAccelerated)
                activity.setContent {
                    val source=rememberLayerBackdrop()
                    MaterialTheme {
                        CompositionLocalProvider(LocalGlassRendering provides if(useLens.value) GlassRendering.LENS else GlassRendering.BLUR,
                            LocalGlassBackdrop provides source,LocalGlassDark provides false) {
                            Box(Modifier.fillMaxSize()) {
                                Canvas(Modifier.fillMaxSize().layerBackdrop(source)) {
                                    val step=12.dp.toPx()
                                    for(y in 0..(size.height/step).toInt()) for(x in 0..(size.width/step).toInt())
                                        drawRect(if((x+y)%2==0) Color.White else Color(0xFF092448),Offset(x*step,y*step),Size(step,step))
                                }
                                GlassSlidingSegments(selected.intValue,2,{selected.intValue=it;calls.incrementAndGet()},
                                    Modifier.padding(top=120.dp,start=16.dp,end=16.dp).fillMaxWidth()
                                        .semantics { contentDescription="Native Square glass" }) {
                                    repeat(2) { index -> Box(Modifier.weight(1f).height(64.dp).glassSegmentItem(index)
                                        .onGloballyPositioned { val r=it.boundsInWindow();rects[index].set(RectF(r.left,r.top,r.right,r.bottom)) }) }
                                }
                            }
                        }
                    }
                }
            }
            fun compare(index:Int) {
                scenario.onActivity { useLens.value=false }
                val plain=capture("$index-blur-control")
                scenario.onActivity { useLens.value=true }
                val lens=capture("$index-native-lens")
                val rect=checkNotNull(rects[index].get())
                val density=context.resources.displayMetrics.density
                var edgeChanges=0;var centerChanges=0;var centerSamples=0
                for(y in (rect.top+18*density).toInt() until (rect.bottom-18*density).toInt()) {
                    for(dx in (3*density).toInt()..(7*density).toInt()) {
                        val x=rect.left.toInt()+dx
                        if(abs(android.graphics.Color.red(plain.getPixel(x,y))-android.graphics.Color.red(lens.getPixel(x,y)))>20) edgeChanges++
                    }
                    val x=rect.centerX().toInt();centerSamples++
                    if(abs(android.graphics.Color.red(plain.getPixel(x,y))-android.graphics.Color.red(lens.getPixel(x,y)))>20) centerChanges++
                }
                assertTrue("The native edge must differ from identical blur-only material",edgeChanges>12)
                assertTrue("Native lens must preserve the center source",centerChanges<centerSamples/3)
                File(output,"$index-pixels.json").writeText("{\"edgeChanges\":$edgeChanges,\"centerChanges\":$centerChanges,\"centerSamples\":$centerSamples}")
            }
            capture("initial");compare(0)
            val control=checkNotNull(device.findObject(By.desc("Native Square glass")))
            val a=rects[0].get();val b=rects[1].get()
            assertNotNull(control)
            device.swipe(a.centerX().toInt(),a.centerY().toInt(),b.centerX().toInt(),b.centerY().toInt(),30)
            capture("after-drag")
            assertEquals(1,selected.intValue);assertEquals(1,calls.get())
            compare(1)
        }
    }
}
