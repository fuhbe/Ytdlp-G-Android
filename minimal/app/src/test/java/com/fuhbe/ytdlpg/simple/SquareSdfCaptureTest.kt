package com.fuhbe.ytdlpg.simple

import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inspector.WindowInspector
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** ACTUAL application windows + sample metadata, native Skia SOFTWARE.
 * Logical frame capture, never a fabricated device/AGSL performance recording. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="en-w411dp-h1100dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SquareSdfCaptureTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 private val buttons=ElasticButtonStats();private val menu=GlassMorphStats();private val rendering=MetaballRenderStats()
 private var time=0L;private var downTime=0L
 private fun setup(dark:Boolean,quality:Boolean):AppModel {
  val model=AppModel(RuntimeEnvironment.getApplication()).apply {
   theme=if(dark)"dark" else "light";url="https://youtu.be/123456789ab"
   if(quality) {
    val m=Media("sample-lake","https://youtu.be/123456789ab","Mountain lake at sunrise","Sample channel",thumb="android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.glass_wallpaper_light}",duration=204,heights=listOf(1080,720))
    inspection=Inspection(m.title,listOf(m),false,listOf(1080,720));selected=setOf(m.id);choice=Choice(height=1080)
   }
  }
  compose.setContent { CompositionLocalProvider(LocalElasticButtonStats provides buttons,LocalGlassMorphStats provides menu,LocalMetaballRenderStats provides rendering) { AppContent(model) } }
  compose.waitForIdle()
  compose.waitUntil(10000) { compose.onAllNodesWithTag(if(dark)"glass-wallpaper-dark" else "glass-wallpaper-light").fetchSemanticsNodes().isNotEmpty() }
  repeat(2) { draw();compose.waitForIdle() };compose.mainClock.autoAdvance=false;return model
 }
 private fun draw()=compose.runOnIdle { WindowInspector.getGlobalWindowViews().filter { it.width>0 && it.height>0 }.forEach { it.draw(Canvas(Bitmap.createBitmap(it.width,it.height,Bitmap.Config.ARGB_8888))) } }
 private fun send(action:Int,p:Offset) {
  if(action==MotionEvent.ACTION_DOWN) { downTime=SystemClock.uptimeMillis();time=downTime };time+=16
  compose.runOnIdle { val event=MotionEvent.obtain(downTime,time,action,p.x,p.y,0);try { compose.activity.window.callback.dispatchTouchEvent(event) }finally { event.recycle() } }
 }
 private fun frame(group:String,name:String) {
  // A physical window event schedules composition/render-node work. Drive the
  // actual frame before capturing it; never photograph an uncommitted overlay.
  compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
  compose.runOnIdle {
   val root=compose.activity.window.decorView
   val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(image);root.draw(canvas)
   val origin=IntArray(2);root.getLocationOnScreen(origin)
   for(view in WindowInspector.getGlobalWindowViews().filter { it!==root && it.width>0 && it.height>0 }) {
    val xy=IntArray(2);view.getLocationOnScreen(xy);canvas.save();canvas.translate((xy[0]-origin[0]).toFloat(),(xy[1]-origin[1]).toFloat());view.draw(canvas);canvas.restore()
   }
   val out=File("build/reports/square-sdf-test2/captures/$group").apply { mkdirs() }
   File(out,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
   fun blob(b:SdfBlob)=JSONObject().put("rect",JSONArray(listOf(b.rect.left,b.rect.top,b.rect.right,b.rect.bottom))).put("radius",b.radius).put("visible",b.visible)
   val data=JSONObject().put("version",BuildConfig.VERSION_NAME).put("physicalDevice",false).put("sampleData",true)
    .put("renderer","Actual AppContent/Popup windows; Robolectric native Skia SOFTWARE; not GPU refraction evidence")
    .put("rawSpring",menu.state?.progress?.value ?: 0f).put("closing",menu.state?.let { !it.expanded } ?: false)
    .put("buttonPressure",buttons.motion?.level ?: 0f).put("buttonDelta",JSONArray(listOf(buttons.motion?.delta?.x ?: 0f,buttons.motion?.delta?.y ?: 0f)))
    .put("sdfSourceCaptures",rendering.sourceCaptures).put("sdfEvaluations",rendering.sdfEvaluations)
   menu.scene?.let { data.put("blobA",blob(it.a)).put("blobB",blob(it.b)).put("blend",it.blend).put("phase",it.state.phase.name) }
   File(out,"$name.json").writeText(data.toString(2))
  }
 }
 private fun buttons(dark:Boolean) {
  val model=setup(dark,false);val group="button-${if(dark)"dark" else "light"}"
  val target=compose.onNodeWithText("Analyze link").fetchSemanticsNode().boundsInRoot.center
  frame(group,"00-idle");send(MotionEvent.ACTION_DOWN,target);frame(group,"01-pressed")
  for((i,d) in listOf(Offset(110f,0f),Offset(180f,0f),Offset(0f,420f),Offset(-150f,420f),Offset(180f,-125f)).withIndex()) {
   send(MotionEvent.ACTION_MOVE,target+d);frame(group,"0${i+2}-long-pull")
   assertTrue(buttons.motion!!.touching);assertEquals(d.x,buttons.motion!!.delta.x,.1f);assertEquals(d.y,buttons.motion!!.delta.y,.1f)
  }
  send(MotionEvent.ACTION_UP,target+Offset(180f,-125f));frame(group,"07-release")
  compose.mainClock.advanceTimeBy(64);frame(group,"08-spring-64ms")
  compose.mainClock.advanceTimeBy(128);frame(group,"09-spring-192ms")
  compose.mainClock.advanceTimeBy(2000);frame(group,"10-settled")
  assertNull(model.inspection);assertFalse(model.busy);assertEquals("",model.error);compose.mainClock.autoAdvance=true
 }
 private fun morph(dark:Boolean) {
  val model=setup(dark,true);val group="menu-${if(dark)"dark" else "light"}"
  compose.onNodeWithTag("glass-quality-trigger").performScrollTo();compose.waitForIdle();frame(group,"00-closed")
  compose.onNodeWithTag("glass-quality-trigger").performClick()
  repeat(4) { compose.waitForIdle();draw();compose.mainClock.advanceTimeByFrame() }
  var count=1
  for(target in listOf(.04f,.12f,.22f,.32f,.42f,.6f,.8f,.95f)) {
   var guard=0
   while(menu.state!!.progress.value<target && guard++<100) { compose.mainClock.advanceTimeBy(16);compose.waitForIdle();draw() }
   frame(group,String.format("%02d-opening",count++))
  }
  compose.mainClock.advanceTimeBy(2000);frame(group,"09-settled-open")
  compose.onNodeWithTag("glass-quality-1080").assertIsSelected();compose.onAllNodesWithTag("glass-quality-2160").assertCountEquals(0)
  compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
  for(target in listOf(.9f,.75f,.6f,.45f,.3f,.15f,.03f)) {
   var guard=0
   while(menu.state!!.progress.value>target && guard++<100) { compose.mainClock.advanceTimeBy(16);compose.waitForIdle();draw() }
   frame(group,String.format("%02d-closing",++count))
  }
  var bounceGuard=0
  while(menu.state!!.progress.value>=0f && menu.state!!.progress.isRunning && bounceGuard++<80) { compose.mainClock.advanceTimeBy(16);compose.waitForIdle();draw() }
  frame(group,"17-closing-undershoot")
  compose.mainClock.advanceTimeBy(2000);frame(group,"18-closed")
  assertFalse(menu.state!!.mounted);assertEquals(1080,model.choice.height);assertFalse(model.choice.mp3)
  assertTrue(rendering.sdfEvaluations>1000);compose.mainClock.autoAdvance=true
 }
 @Test fun actualWindowLongPullLight()=buttons(false)
 @Test fun actualWindowLongPullDark()=buttons(true)
 @Test fun actualSdfMorphLight()=morph(false)
 @Test fun actualSdfMorphDark()=morph(true)
 @Test fun actualPasteKeepsTheExactLongUrlAndCompactInset() {
  val model=setup(false,false);frame("paste","00-before")
  val text="https://www.youtube.com/watch?v=123456789ab&list=PL_SAMPLE&literal=a+b%26c&tail="+"abcdef".repeat(10)
  compose.runOnIdle { (RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Sample",text)) }
  compose.onNodeWithContentDescription("Paste link").performClick();compose.mainClock.advanceTimeBy(2000);compose.waitForIdle()
  frame("paste","01-after");assertEquals(text,model.url);assertFalse(model.busy);compose.mainClock.autoAdvance=true
 }
}
