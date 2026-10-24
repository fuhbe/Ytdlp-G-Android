package com.fuhbe.ytdlpg.simple

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inspector.WindowInspector
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual AppContent/Popup with sample metadata. SOFTWARE captures, no FPS/GPU claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="en-w411dp-h1100dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Test3CaptureTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 private val menu=GlassMorphStats();private lateinit var model:AppModel
 private var time=0L;private var downTime=0L
 private fun setup(language:String,dark:Boolean,large:Boolean=false) {
  val app=RuntimeEnvironment.getApplication()
  app.getSharedPreferences("ytdlpg-ui",Context.MODE_PRIVATE).edit().putString("language",language).commit()
  model=AppModel(app).apply {
   theme=if(dark)"dark" else "light";url="https://youtu.be/123456789ab"
   val m=Media("sample-test3",url,"Mountain lake at sunrise","Sample channel",thumb="android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.glass_wallpaper_light}",duration=204,heights=listOf(1080,720))
   inspection=Inspection(m.title,listOf(m),false,listOf(1080,720));selected=setOf(m.id);choice=Choice(height=720)
  }
  compose.setContent { CompositionLocalProvider(LocalGlassMorphStats provides menu) {
   val density=LocalDensity.current
   CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.5f else 1f)) { AppContent(model) }
  } }
  compose.waitForIdle();compose.waitUntil(10000) { compose.onAllNodesWithTag(if(dark)"glass-wallpaper-dark" else "glass-wallpaper-light").fetchSemanticsNodes().isNotEmpty() }
  compose.mainClock.autoAdvance=false
 }
 private fun tick() { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
 private fun send(action:Int,p:Offset) {
  if(action==MotionEvent.ACTION_DOWN) { downTime=SystemClock.uptimeMillis();time=downTime };time+=16
  compose.runOnIdle { val e=MotionEvent.obtain(downTime,time,action,p.x,p.y,0);try { compose.activity.window.callback.dispatchTouchEvent(e) }finally { e.recycle() } };tick()
 }
 private fun capture(group:String,name:String) {
  tick()
  compose.runOnIdle {
   val root=compose.activity.window.decorView;val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(image);root.draw(canvas)
   val xy=IntArray(2);root.getLocationOnScreen(xy)
   WindowInspector.getGlobalWindowViews().filter { it!==root && it.width>0 && it.height>0 }.forEach { v->
    val p=IntArray(2);v.getLocationOnScreen(p);canvas.save();canvas.translate((p[0]-xy[0]).toFloat(),(p[1]-xy[1]).toFloat());v.draw(canvas);canvas.restore()
   }
   val folder=File("build/reports/anchored-test3/captures/$group").apply { mkdirs() }
   File(folder,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
   File(folder,"$name.json").writeText(JSONObject().put("version",BuildConfig.VERSION_NAME).put("sampleData",true).put("physicalDevice",false).put("gpuExecution",false)
    .put("renderer","Actual AppContent/Popup windows; Robolectric native Skia SOFTWARE")
    .put("selectedHeight",model.choice.height).put("menuProgress",menu.state?.progress?.value ?: 0f).put("menuSpringRunning",menu.state?.progress?.isRunning ?: false)
    .put("menuExpanded",menu.state?.expanded ?: false).put("menuMounted",menu.state?.mounted ?: false).toString(2))
  }
 }
 private fun actions(language:String,dark:Boolean,large:Boolean=false) {
  setup(language,dark,large);val group="actions-$language-${if(dark)"dark" else "light"}"
  val names=when(language) { "pt-BR"->listOf("Salvar em","Baixar");"es"->listOf("Guardar en","Descargar");else->listOf("Save to","Download") }
  val jobs=Store.list();val choice=model.choice;val folder=model.folder
  for((index,label) in names.withIndex()) {
   // Keep scrolling and touch gestures separate so screenshots isolate the
   // button's anchored matrix instead of confusing normal page scroll with it.
   compose.mainClock.autoAdvance=true;compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label));compose.waitForIdle();compose.mainClock.autoAdvance=false
   val original=compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
   val point=original.center
   capture(group,"$index-00-idle");send(MotionEvent.ACTION_DOWN,point);capture(group,"$index-01-press")
   send(MotionEvent.ACTION_MOVE,point+Offset(260f,1f));capture(group,"$index-02-long-horizontal")
   compose.onAllNodesWithText(label).assertCountEquals(1)
   assertEquals("The fixed label/layout must not travel with the finger",original,compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot)
   send(MotionEvent.ACTION_UP,point+Offset(260f,1f));capture(group,"$index-03-release")
   compose.mainClock.advanceTimeBy(96);capture(group,"$index-04-return")
   compose.mainClock.advanceTimeBy(2000);capture(group,"$index-05-settled")
   assertEquals(original,compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot)
  }
  assertEquals(jobs,Store.list());assertEquals(choice,model.choice);assertEquals(folder,model.folder);assertFalse(model.queuing);assertFalse(model.busy)
  compose.mainClock.autoAdvance=true
 }
 private fun quality(language:String,dark:Boolean) {
  setup(language,dark)
  compose.mainClock.autoAdvance=true;compose.onNodeWithTag("glass-quality-trigger").performScrollTo();compose.waitForIdle();compose.mainClock.autoAdvance=false
  val group="quality-$language-${if(dark)"dark" else "light"}"
  capture(group,"00-closed");compose.onNodeWithTag("glass-quality-trigger").performClick()
  var guard=0
  while((menu.state==null || menu.state!!.progress.value<.97f) && guard++<100) { tick();compose.runOnIdle {
   WindowInspector.getGlobalWindowViews().filter { it.width>0 && it.height>0 }.forEach { it.draw(Canvas(Bitmap.createBitmap(it.width,it.height,Bitmap.Config.ARGB_8888))) }
  } }
  assertTrue(menu.state!!.progress.isRunning);capture(group,"01-visible-spring-running")
  compose.onNodeWithTag("glass-quality-1080").assertIsEnabled().performTouchInput { click() }
  assertEquals(1080,model.choice.height);assertFalse(menu.state!!.expanded);capture(group,"02-first-tap-selected")
  compose.mainClock.advanceTimeBy(96);capture(group,"03-reverse-closing")
  compose.mainClock.advanceTimeBy(2000);capture(group,"04-closed-1080")
  assertFalse(menu.state!!.mounted);assertEquals(1080,model.choice.height);compose.mainClock.autoAdvance=true
 }
 @Test fun actualPortugueseLightActionsStayInPlace()=actions("pt-BR",false)
 @Test fun actualEnglishDarkActionsStayInPlace()=actions("en",true)
 @Test @Config(qualifiers="en-w320dp-h1000dp-mdpi") fun actualSpanishNarrowLargeFontActionsStayInPlace()=actions("es",false,true)
 @Test fun actualPortuguese1080FirstTapAndAnimatedClose()=quality("pt-BR",false)
 @Test fun actualEnglishDark1080FirstTapAndAnimatedClose()=quality("en",true)
}
