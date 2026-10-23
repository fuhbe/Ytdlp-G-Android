package com.fuhbe.ytdlpg.simple

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/** Actual device acceptance, NOT a host test or a claim made by compiling. */
@RunWith(AndroidJUnit4::class)
class GlassTotalDeviceTest {
    @Test fun allFourControlsAndTheQualityPopupKeepTheirCallbacksOnARealWindow() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as android.app.Application
        assumeTrue("Pause active jobs before the isolated visual probe",Store.list().none { it.state in setOf("RUNNING","QUEUED") })
        val device=UiDevice.getInstance(instrumentation)
        val frames=Collections.synchronizedList(mutableListOf<Long>())
        val gpu=Collections.synchronizedList(mutableListOf<Long>())
        val thread=HandlerThread("GlassTotalFrames").apply { start() }
        val listener=Window.OnFrameMetricsAvailableListener { _,metric,_ ->
            frames.add(metric.getMetric(FrameMetrics.TOTAL_DURATION))
            if(Build.VERSION.SDK_INT>=31) metric.getMetric(FrameMetrics.GPU_DURATION).takeIf { it>0 }?.let { gpu.add(it) }
        }
        val out=File(app.getExternalFilesDir(null),"glass-total-066-probe").apply { mkdirs() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val model=AppModel(app).apply {
                    theme="light"
                    val items=(1..3).map { Media("total-probe-$it","https://youtu.be/123456789ab","Visual probe $it",heights=listOf(1080,720)) }
                    inspection=Inspection("Visual probe playlist",items,true,listOf(1080,720));selected=items.map { it.id }.toSet()
                }
                scenario.onActivity {
                    assertTrue("Hardware window required for this measurement",it.window.decorView.isHardwareAccelerated)
                    it.setContent { AppContent(model) };it.window.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper))
                }
                fun settle() { instrumentation.waitForIdleSync();device.waitForIdle(1500) }
                fun reveal(text:String) {
                    if(device.findObject(By.text(text))==null)
                        assertTrue(UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(text))
                    settle()
                }
                fun drag(a:String,b:String) {
                    val first=checkNotNull(device.findObject(By.text(a))).visibleBounds
                    val last=checkNotNull(device.findObject(By.text(b))).visibleBounds
                    assertTrue(device.swipe(first.centerX(),first.centerY(),last.centerX(),last.centerY(),30));settle()
                }
                settle();reveal("MP4")
                repeat(3) { drag("MP4","MP3");scenario.onActivity { assertTrue(model.choice.mp3) };drag("MP3","MP4") }
                reveal("Normal");drag("Normal","ZIP");scenario.onActivity { assertTrue(model.choice.zip) }
                drag("ZIP","Normal");scenario.onActivity { assertFalse(model.choice.zip) }
                reveal("Best quality")
                checkNotNull(device.findObject(By.desc("Choose quality"))).click();settle()
                assertTrue(device.takeScreenshot(File(out,"quality-open.png")))
                assertNull(device.findObject(By.text("2160p")))
                checkNotNull(device.findObject(By.text("720p"))).click();settle()
                scenario.onActivity { assertEquals(720,model.choice.height);assertFalse(model.choice.mp3) }
                val home=checkNotNull(device.findObject(By.desc("Back"))).visibleBounds
                val downloads=checkNotNull(device.findObject(By.desc("Downloads"))).visibleBounds
                assertTrue(device.swipe(home.centerX(),home.centerY(),downloads.centerX(),downloads.centerY(),36));settle()
                scenario.onActivity { assertEquals("downloads",model.area) }
                drag("Downloads (0)","Completed")
                assertNotNull(device.findObject(By.text("No completed downloads.")))
                assertTrue(device.takeScreenshot(File(out,"completed.png")))
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                val drained=java.util.concurrent.CountDownLatch(1)
                Handler(thread.looper).post { drained.countDown() };assertTrue(drained.await(5,java.util.concurrent.TimeUnit.SECONDS))
                val f=synchronized(frames) { frames.filter { it>0 }.sorted() }
                val g=synchronized(gpu) { gpu.sorted() }
                assertTrue("Need an actual frame sample",f.size>=20)
                fun p(v:List<Long>,q:Double):Any=if(v.isEmpty()) JSONObject.NULL else v[((v.size-1)*q).toInt()]/1_000_000.0
                File(out,"all-controls-frames.json").writeText(JSONObject()
                    .put("api",Build.VERSION.SDK_INT).put("device",Build.MODEL).put("frames",f.size)
                    .put("totalP50Ms",p(f,.5)).put("totalP95Ms",p(f,.95)).put("gpuP95Ms",p(g,.95))
                    .put("hardwareWindow",true).put("fpsGuarantee",false)
                    .put("virtualDeviceReported",Build.HARDWARE in listOf("goldfish","ranchu") || Build.MODEL.contains("sdk",true))
                    .put("sample","All control interactions including transitions and scrolling; no touch-to-photon claim").toString(2))
            }
        } finally { thread.quitSafely() }
    }
}
