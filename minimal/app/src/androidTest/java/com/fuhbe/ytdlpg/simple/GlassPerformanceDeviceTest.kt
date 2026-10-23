package com.fuhbe.ytdlpg.simple

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/** Real window/HWUI instrumentation. Compiling it provides no frame/GPU result. */
@RunWith(AndroidJUnit4::class)
class GlassPerformanceDeviceTest {
    @Test @SdkSuppress(minSdkVersion=31) fun recordActualWindowFramesAndOpticalBackendWhileDragging() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as android.app.Application
        val device=UiDevice.getInstance(instrumentation)
        assumeTrue("Pause real jobs before the visual performance probe",Store.list().none { it.state in setOf("RUNNING","QUEUED") })
        val stats=GlassRenderStats()
        val optics=mutableStateOf(1f)
        val totals=Collections.synchronizedList(mutableListOf<Long>())
        val gpu=Collections.synchronizedList(mutableListOf<Long>())
        val handler=HandlerThread("GlassFrameMetrics").apply { start() }
        var droppedCallbacks=0
        val listener=Window.OnFrameMetricsAvailableListener { _,frame,dropped ->
            totals.add(frame.getMetric(FrameMetrics.TOTAL_DURATION))
            frame.getMetric(FrameMetrics.GPU_DURATION).takeIf { it>=0 }?.let { gpu.add(it) }
            droppedCallbacks+=dropped
        }
        val folder=File(app.getExternalFilesDir(null),"glass-066-probe").apply { mkdirs() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var refresh=60f
                val model=AppModel(app).apply {
                    theme="light"
                    val media=Media("visual-perf-fixture","https://youtu.be/123456789ab","Visual probe","Fixture metadata",duration=204,heights=listOf(1080,720))
                    inspection=Inspection("Visual probe",listOf(media),false,listOf(1080,720));selected=setOf(media.id)
                }
                scenario.onActivity {
                    assertTrue(it.window.decorView.isHardwareAccelerated)
                    refresh=it.display?.refreshRate ?: 60f
                    it.setContent { CompositionLocalProvider(LocalGlassRenderStats provides stats,LocalGlassOpticalStrength provides optics.value) { AppContent(model) } }
                    it.window.addOnFrameMetricsAvailableListener(listener,Handler(handler.looper))
                }
                instrumentation.waitForIdleSync();device.waitForIdle(2000)
                // Initial layout/capture is outside the measurement interval.
                device.findObject(By.desc("Downloads")).click();device.waitForIdle(1000)
                device.findObject(By.desc("Voltar")).click();device.waitForIdle(1000)
                totals.clear();gpu.clear()
                // Compare real UI pixels with the identical source/selection.
                // This is evidence only when the device test is actually run.
                scenario.onActivity { optics.value=0f };device.waitForIdle(1000);android.os.SystemClock.sleep(500)
                val plain=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                scenario.onActivity { optics.value=1f };device.waitForIdle(1000);android.os.SystemClock.sleep(500)
                val refracted=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val region=checkNotNull(device.findObject(By.desc("Voltar"))).visibleBounds
                var changed=0
                for(y in region.top+6 until region.bottom-6)for(x in region.left+6 until region.right-6)
                    if(kotlin.math.abs(android.graphics.Color.blue(plain.getPixel(x,y))-android.graphics.Color.blue(refracted.getPixel(x,y)))>25)changed++
                for((name,image) in listOf("optics-off" to plain,"optics-on" to refracted))
                    File(folder,"$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                File(folder,"optical-pixels.json").writeText("{\"changedControlPixels\":$changed,\"hardwareWindow\":true}")
                assertTrue("Actual source text/icon pixels must be displaced",changed>20)
                totals.clear();gpu.clear();droppedCallbacks=0
                val beforeRecords=stats.sourceRecords;val beforeReadbacks=stats.readbacks
                repeat(8) {
                    val a=checkNotNull(device.findObject(By.desc("Voltar"))).visibleBounds
                    val b=checkNotNull(device.findObject(By.desc("Downloads"))).visibleBounds
                    assertTrue(device.swipe(a.centerX(),a.centerY(),b.centerX(),b.centerY(),36))
                    device.waitForIdle(1000)
                    assertTrue(device.swipe(b.centerX(),b.centerY(),a.centerX(),a.centerY(),36))
                    device.waitForIdle(1000)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                val drained=java.util.concurrent.CountDownLatch(1)
                Handler(handler.looper).post { drained.countDown() };assertTrue(drained.await(5,java.util.concurrent.TimeUnit.SECONDS))
                val values=synchronized(totals) { totals.filter { it>0 }.sorted() }
                val gpuValues=synchronized(gpu) { gpu.filter { it>0 }.sorted() }
                assertTrue("A real frame sample is required",values.size>=20)
                fun percentile(v:List<Long>,p:Double)=if(v.isEmpty()) JSONObject.NULL else v[((v.size-1)*p).toInt()]/1_000_000.0
                val budget=1_000_000_000.0/refresh
                val data=JSONObject().put("api",Build.VERSION.SDK_INT).put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL)
                    .put("refreshHz",refresh).put("frames",values.size).put("totalP50Ms",percentile(values,.5)).put("totalP95Ms",percentile(values,.95))
                    .put("totalP99Ms",percentile(values,.99)).put("gpuP95Ms",percentile(gpuValues,.95)).put("overRefreshBudget",values.count { it>budget })
                    .put("droppedMetricCallbacks",droppedCallbacks).put("hardwareMeshCanvas",stats.hardwareMesh).put("meshDraws",stats.meshDraws)
                    .put("sourceRecords",stats.sourceRecords-beforeRecords).put("readbacks",stats.readbacks-beforeReadbacks)
                    .put("virtualDeviceReported",Build.HARDWARE in listOf("goldfish","ranchu") || Build.MODEL.contains("sdk",true))
                    .put("fpsGuarantee",false)
                File(folder,"frame-metrics.json").writeText(data.toString(2))
                assertTrue(device.takeScreenshot(File(folder,"actual-screen.png")))
                if(Build.VERSION.SDK_INT<=32) {
                    assertTrue("API31/32 optical mesh must actually draw",stats.meshDraws>0)
                    assertTrue("GPU-path evidence requires a hardware Canvas",stats.hardwareMesh)
                }
            }
        } finally { handler.quitSafely() }
    }
}
