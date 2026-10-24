package com.fuhbe.ytdlpg.simple

import android.os.*
import android.view.*
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

/** Opt-in REAL device probe. Compiling this class is not running or passing it.
 * No network/download operations; sample metadata rendered by actual AppContent. */
@RunWith(AndroidJUnit4::class)
class Glass067DeviceTest {
    @Test fun buttonPullAndQualityMorphReportRealWindowFrames() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as android.app.Application
        assumeTrue("Pause real jobs before the isolated UI probe",Store.list().none { it.state in setOf("RUNNING","QUEUED") })
        val device=UiDevice.getInstance(instrumentation)
        val samples=Collections.synchronizedList(mutableListOf<Long>())
        val gpu=Collections.synchronizedList(mutableListOf<Long>())
        val thread=HandlerThread("Glass067Frames").apply { start() }
        val listener=Window.OnFrameMetricsAvailableListener { _,f,_ ->
            f.getMetric(FrameMetrics.TOTAL_DURATION).takeIf { it>0 }?.let(samples::add)
            if(Build.VERSION.SDK_INT>=31)f.getMetric(FrameMetrics.GPU_DURATION).takeIf { it>0 }?.let(gpu::add)
        }
        val out=File(app.getExternalFilesDir(null),"glass-067-probe").apply { mkdirs() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val model=AppModel(app).apply {
                    theme="light";url="https://youtu.be/123456789ab"
                }
                var hz=60f
                scenario.onActivity {
                    assertTrue("Hardware window required",it.window.decorView.isHardwareAccelerated)
                    hz=it.display?.refreshRate ?: 60f
                    it.setContent { AppContent(model) };it.window.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper))
                }
                fun settle() { instrumentation.waitForIdleSync();device.waitForIdle(1200) }
                fun reveal(text:String) { if(device.findObject(By.text(text))==null)assertTrue(UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(text));settle() }
                settle();reveal("Analyze link");samples.clear();gpu.clear()
                repeat(6) {
                    val b=checkNotNull(device.findObject(By.text("Analyze link"))).visibleBounds
                    assertTrue(device.swipe(b.centerX(),b.centerY(),b.centerX()+85,b.centerY()-30,28));settle()
                    scenario.onActivity { assertTrue("A pull must never analyze",model.error.isEmpty());assertNull(model.inspection);assertFalse(model.busy) }
                }
                assertTrue(device.takeScreenshot(File(out,"button-settled.png")))
                scenario.onActivity {
                    val m=Media("sample-probe","https://youtu.be/123456789ab","Sample landscape","Sample channel",heights=listOf(1080,720))
                    model.inspection=Inspection(m.title,listOf(m),false,listOf(1080,720));model.selected=setOf(m.id)
                }
                settle();reveal("Best quality");checkNotNull(device.findObject(By.desc("Choose quality"))).click();settle()
                assertTrue(device.takeScreenshot(File(out,"quality-open.png")))
                assertNull(device.findObject(By.text("2160p")))
                checkNotNull(device.findObject(By.text("720p"))).click();settle()
                scenario.onActivity { assertEquals(720,model.choice.height);assertFalse(model.choice.mp3) }
                checkNotNull(device.findObject(By.desc("Choose quality"))).click();settle();device.pressBack();settle()
                scenario.onActivity { assertEquals(720,model.choice.height);it.window.removeOnFrameMetricsAvailableListener(listener) }
                val latch=java.util.concurrent.CountDownLatch(1);Handler(thread.looper).post { latch.countDown() };assertTrue(latch.await(5,java.util.concurrent.TimeUnit.SECONDS))
                val f=synchronized(samples) { samples.sorted() };val g=synchronized(gpu) { gpu.sorted() }
                assertTrue("An actual frame sample is required",f.size>=20)
                fun percentile(v:List<Long>,q:Double):Any=if(v.isEmpty())JSONObject.NULL else v[((v.size-1)*q).toInt()]/1e6
                File(out,"frames.json").writeText(JSONObject().put("api",Build.VERSION.SDK_INT).put("device",Build.MODEL)
                    .put("refreshHz",hz).put("frames",f.size).put("p50Ms",percentile(f,.5)).put("p95Ms",percentile(f,.95))
                    .put("overRefreshBudget",f.count { it>1e9/hz }).put("gpuP95Ms",percentile(g,.95))
                    .put("scope","Activity window including scrolling; Popup window GPU duration is not separately sampled")
                    .put("virtualDeviceReported",Build.HARDWARE in listOf("ranchu","goldfish") || Build.MODEL.contains("sdk",true))
                    .put("sampleData",true).put("fpsGuarantee",false).toString(2))
            }
        } finally { thread.quitSafely() }
    }
}
