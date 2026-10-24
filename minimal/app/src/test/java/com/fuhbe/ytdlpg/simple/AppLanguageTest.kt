package com.fuhbe.ytdlpg.simple

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="en-w320dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppLanguageTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 @Before fun resetLanguage() { RuntimeEnvironment.getApplication().getSharedPreferences("ytdlpg-ui",Context.MODE_PRIVATE).edit().clear().commit() }
 @Test fun threeGlassChoicesTranslateImmediatelyAndDoNotChangeQueuedDataOrActions() {
  val app=RuntimeEnvironment.getApplication();val media=Media("sample","https://youtu.be/123456789ab","English • Português • Español")
  val job=QueueJob(title=media.title,items=listOf(media),choice=Choice(height=720,zip=false),folder="content://sample/folder",state="PAUSED",progress=.42f,phase="Partial download")
  Store.insert(job);val stored=Store.list();val model=AppModel(app).apply { area="settings";url="https://youtu.be/123456789ab?text=áé";choice=Choice(height=1080);selected=setOf(media.id) }
  val originalChoice=model.choice;val originalUrl=model.url;val originalSelected=model.selected
  compose.setContent { AppContent(model) };compose.waitForIdle()
  for((tag,settings,home,analyze) in listOf(listOf("pt-BR","Configurações","Início","Analisar link"),listOf("es","Ajustes","Inicio","Analizar enlace"),listOf("en","Settings","Home","Analyze link"))) {
   compose.onNodeWithTag("ui-language-$tag").performScrollTo().performClick();compose.waitForIdle()
   compose.onNodeWithTag("ui-language-$tag").assertIsSelected()
   compose.onAllNodesWithText(settings,useUnmergedTree=true)[0].assertExists()
   compose.onNodeWithText(home,useUnmergedTree=true).assertExists()
   capture("settings-$tag")
   compose.runOnIdle { model.area="home" };compose.waitForIdle()
   compose.onNodeWithText(analyze).assertIsDisplayed();capture("home-$tag")
   assertEquals(stored,Store.list());assertEquals(originalChoice,model.choice);assertEquals(originalUrl,model.url);assertEquals(originalSelected,model.selected)
   assertFalse(model.busy);assertFalse(model.queuing)
   compose.runOnIdle { model.area="settings" };compose.waitForIdle()
  }
 }
 @Test fun savedLanguageSurvivesNewUiStateAndInvalidValuesSafelyUseEnglish() {
  val app=RuntimeEnvironment.getApplication()
  val state=UiLanguageState(app);assertEquals(AppLanguage.ENGLISH,state.language)
  state.select(AppLanguage.PORTUGUESE);assertEquals(AppLanguage.PORTUGUESE,UiLanguageState(app).language)
  state.select(AppLanguage.SPANISH);assertEquals(AppLanguage.SPANISH,UiLanguageState(app).language)
  assertEquals(AppLanguage.ENGLISH,AppLanguage.from("unexpected"))
 }
 @Test fun translatedLongLabelsAndFormattedCountsFitWithLargeFontsWithoutChangingQualityChoices() {
  val app=RuntimeEnvironment.getApplication();UiLanguageState(app).select(AppLanguage.SPANISH)
  compose.setContent { AppLanguageProvider { GlassTheme(true) {
   val density=LocalDensity.current
   CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
    Column { LanguageSettingsCard();Text(uiText("%1\$d of %2\$d selected",3,7));GlassQualityPicker(listOf(0,720),720,{}) }
   }
  } } };compose.waitForIdle()
  compose.onNodeWithText("3 de 7 seleccionados").assertExists()
  compose.onNodeWithText("Hasta la resolución seleccionada").assertExists()
  for(tag in listOf("en","pt-BR","es")) {
   val node=compose.onNodeWithTag("ui-language-$tag").fetchSemanticsNode().boundsInRoot
   assertTrue(node.width<=320f);assertTrue(node.height>=48f)
  }
 }
 private fun capture(name:String) {
  compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
  compose.runOnIdle { val view=compose.activity.window.decorView;val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b))
   File("build/reports/square-sdf-test2/languages/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
  }
 }
}
