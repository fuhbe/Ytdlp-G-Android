package com.fuhbe.ytdlpg.simple

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in PHYSICAL/EMULATOR pixel evidence; building this class is NOT running
 * it. Actual AppContent sample metadata; no network, queue or download actions. */
@RunWith(AndroidJUnit4::class)
class SquareSdfDeviceTest {
 @Test fun api33HardwareNeckSamplesTheRealBackdropThroughItsSdfNormal() {
  assumeTrue("Additional normal-driven AGSL refraction requires API33+",Build.VERSION.SDK_INT>=33)
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val app=instrumentation.targetContext.applicationContext as android.app.Application
  assumeTrue("Pause all real jobs before this isolated visual probe",Store.list().none { it.state in setOf("QUEUED","RUNNING") })
  val device=UiDevice.getInstance(instrumentation)
  val stats=GlassMorphStats();val render=MetaballRenderStats();val strength=mutableStateOf(1f)
  lateinit var scope:CoroutineScope
  val folder=File(app.getExternalFilesDir(null),"square-sdf-test2-probe").apply { mkdirs() }
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   val model=AppModel(app).apply {
    theme="light"
    val m=Media("sample-native-sdf","https://youtu.be/123456789ab","Sample optical probe","Sample channel",heights=listOf(1080,720))
    inspection=Inspection(m.title,listOf(m),false,listOf(1080,720));selected=setOf(m.id)
   }
   scenario.onActivity { activity ->
    assertTrue(activity.window.decorView.isHardwareAccelerated)
    activity.setContent { scope=rememberCoroutineScope();CompositionLocalProvider(LocalGlassMorphStats provides stats,LocalMetaballRenderStats provides render,LocalGlassOpticalStrength provides strength.value) { AppContent(model) } }
   }
   instrumentation.waitForIdleSync();device.waitForIdle(1500)
   val chooser=By.desc("Choose quality")
   if(device.findObject(chooser)==null)assertTrue(UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView("Best quality"))
   checkNotNull(device.findObject(chooser)).click();device.waitForIdle(1500)
   assumeTrue("Current GPU/power/memory tier selected the native SDF path",render.shaderCompilations>0 && !render.rejected)
   scenario.onActivity { scope.launch { stats.state!!.progress.stop();stats.state!!.progress.snapTo(.24f) } }
   instrumentation.waitForIdleSync();device.waitForIdle(800)
   scenario.onActivity { strength.value=0f };instrumentation.waitForIdleSync();device.waitForIdle(800)
   val plain=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
   val captures=render.sourceCaptures
   scenario.onActivity { strength.value=1f };instrumentation.waitForIdleSync();device.waitForIdle(800)
   val refracted=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
   assertTrue("A hardware Canvas must actually have painted",render.hardwareCanvas)
   assertEquals("Changing optics must reuse the frozen ACTUAL activity source",captures,render.sourceCaptures)
   val scene=checkNotNull(stats.scene);val offset=stats.windowOrigin
   var candidates=0;var displaced=0
   val roi=scene.a.rect.inflate(50f).intersect(scene.b.rect.inflate(50f))
   for(y in roi.top.toInt() until roi.bottom.toInt() step 2)for(x in roi.left.toInt() until roi.right.toInt() step 2) {
    val px=(x+offset.x).toInt();val py=(y+offset.y).toInt()
    if(px !in 0 until plain.width || py !in 0 until plain.height)continue
    if(blobDistance(x.toFloat(),y.toFloat(),scene.a)>0f && blobDistance(x.toFloat(),y.toFloat(),scene.b)>0f && scene.distance(x.toFloat(),y.toFloat())<-.5f) {
     candidates++;val a=plain.getPixel(px,py);val b=refracted.getPixel(px,py)
     if(kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))+kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))+kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))>3)displaced++
    }
   }
   for((name,image) in listOf("actual-optics-off" to plain,"actual-optics-on" to refracted))File(folder,"$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
   File(folder,"gpu-neck.json").writeText(JSONObject().put("device",Build.MODEL).put("api",Build.VERSION.SDK_INT)
    .put("hardwareCanvas",render.hardwareCanvas).put("shaderCompilations",render.shaderCompilations).put("sourceCaptures",render.sourceCaptures)
    .put("neckPixels",candidates).put("changedNeckPixels",displaced).put("sampleData",true).put("fpsClaim",false)
    .put("virtualDeviceReported",Build.HARDWARE in listOf("ranchu","goldfish") || Build.MODEL.contains("sdk",true)).toString(2))
   assertTrue("The actual scene must expose a fused SDF neck",candidates>5)
   assertTrue("Real input pixels must change under normal-driven refraction",displaced>3)
   device.pressBack();device.waitForIdle(1000);scenario.onActivity { assertEquals(0,model.choice.height);assertFalse(model.choice.mp3) }
  }
 }
}
