package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Must run on a hardware-accelerated Android window. Not a JVM/host GPU claim. */
@RunWith(AndroidJUnit4::class)
class LiquidGlassDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun settled() { instrumentation.waitForIdleSync(); device.waitForIdle(2_000) }
    private fun save(name: String) {
        val path = File(context.getExternalFilesDir(null), "visual-test/$name-api${Build.VERSION.SDK_INT}.png")
        path.parentFile?.mkdirs(); assertTrue(device.takeScreenshot(path))
    }
    @Test fun captureActualInterfaceAndExistingNavigationInBothThemes() {
        assumeTrue("Pause/finish other jobs before visual instrumentation", Store.list().none { it.state in setOf("RUNNING", "QUEUED") })
        val original = Store.jobs.value
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val items = (1..4).map { Media("visual-fixture-$it", "https://youtu.be/123456789ab", "Example $it", "Sample metadata", duration=204, heights=listOf(1080,720)) }
                val app = context.applicationContext as android.app.Application
                val model = AppModel(app).apply {
                    theme="light"; inspection=Inspection("Example playlist",items.take(1),false,listOf(1080,720));selected=setOf(items.first().id)
                }
                scenario.onActivity { assertTrue(it.window.decorView.isHardwareAccelerated); it.setContent { AppContent(model) } }
                settled(); save("home-light")
                scenario.onActivity { model.inspection=Inspection("Example playlist",items,true,listOf(1080,720)); model.selected=items.map{it.id}.toSet() }
                settled(); save("playlist-light")
                // Fixture queue states are assigned only to the UI flow, never persisted or executed.
                scenario.onActivity { Store.jobs.value=listOf(
                    QueueJob(id="visual-active",title="Example playlist",items=items,choice=Choice(mp3=true),folder="",state="RUNNING",progress=.64f,phase="Baixando áudio…",indeterminate=false),
                    QueueJob(id="visual-paused",title="Example video",items=items.take(1),choice=Choice(),folder="",state="PAUSED",progress=.38f,phase="Pausado",indeterminate=false)) }
                val downloads=device.findObject(By.desc("Downloads")); assertNotNull(downloads); downloads.click()
                settled(); scenario.onActivity { assertEquals("downloads",model.area) }; save("downloads-light")
                scenario.onActivity { model.theme="dark" }; settled(); save("downloads-dark")
                val settings=device.findObject(By.desc("Configurações")); assertNotNull(settings); settings.click()
                settled(); scenario.onActivity { assertEquals("settings",model.area) }; save("settings-dark")
            }
        } finally { Store.jobs.value=original }
    }
    @Test @SdkSuppress(minSdkVersion=33) fun nativeShaderCompilesAndActualBackdropDisplacesPixels() {
        // Original Apache-2.0 shader from the exact dependency, including its copyright.
        val text=instrumentation.context.assets.open("backdrop-lens.agsl").bufferedReader().use{it.readText()}
        val shader=RuntimeShader(text)
        assertNotNull(RenderEffect.createRuntimeShaderEffect(shader,"content"))
        val bounds=AtomicReference<RectF>()
        val cell=AtomicReference<Float>()
        val origin=AtomicReference<RectF>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.isHardwareAccelerated)
                activity.setContent {
                    val source=rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().layerBackdrop(source).onGloballyPositioned { val r=it.boundsInWindow();origin.set(RectF(r.left,r.top,r.right,r.bottom)) }) {
                            val step=12.dp.toPx();cell.set(step)
                            for(y in 0..(size.height/step).toInt()) for(x in 0..(size.width/step).toInt())
                                drawRect(if((x+y)%2==0) Color.White else Color.Black,Offset(x*step,y*step),Size(step,step))
                        }
                        Box(Modifier.offset(32.dp,100.dp).size(200.dp,120.dp)
                            .drawBackdrop(source,shape={RoundedCornerShape(18.dp)},effects={lens(10.dp.toPx(),12.dp.toPx(),depthEffect=true)},highlight=null,shadow=null)
                            .onGloballyPositioned { val r=it.boundsInWindow();bounds.set(RectF(r.left,r.top,r.right,r.bottom)) })
                    }
                }
            }
            settled()
            val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val rect=checkNotNull(bounds.get());val step=checkNotNull(cell.get())
            val pattern=checkNotNull(origin.get())
            fun expected(x:Int,y:Int)=if(((x-pattern.left)/step).toInt().plus(((y-pattern.top)/step).toInt())%2==0) 255 else 0
            val cx=rect.centerX().toInt();val cy=rect.centerY().toInt()
            assertTrue("Unrefracted center must match the original source", kotlin.math.abs(android.graphics.Color.red(bitmap.getPixel(cx,cy))-expected(cx,cy))<20)
            var displaced=0
            for(dx in 2..10) for(y in rect.top.toInt()+24 until rect.bottom.toInt()-24) {
                val x=rect.left.toInt()+dx
                val expected=expected(x,y)
                val actual=android.graphics.Color.red(bitmap.getPixel(x,y))
                if(kotlin.math.abs(expected-actual)>100) displaced++
            }
            assertTrue("Actual library lens must displace the source behind its edge",displaced>20)
            save("native-refraction-probe")
        }
    }
}
