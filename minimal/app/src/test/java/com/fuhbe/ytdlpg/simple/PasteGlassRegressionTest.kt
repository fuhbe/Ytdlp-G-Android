package com.fuhbe.ytdlpg.simple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class,qualifiers="en-w320dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PasteGlassRegressionTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 private fun checkPaste(dark:Boolean,large:Boolean) {
  val app=RuntimeEnvironment.getApplication();val model=AppModel(app).apply { theme=if(dark)"dark" else "light" }
  compose.setContent {
   val density=LocalDensity.current
   CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.8f else 1f)) { AppContent(model) }
  }
  compose.waitForIdle()
  val clipboard=app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  val originals=listOf("https://youtu.be/123456789ab","https://www.youtube.com/watch?v=123456789ab&list=PL_SAMPLE&index=2&literal=a+b%26c&unicode=áé&tail="+"abc".repeat(20))
  val jobs=Store.list().map { it.id }
  for(text in originals) {
   compose.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("Sample",text)) }
   compose.onNodeWithContentDescription("Paste link").performClick();compose.waitForIdle()
   assertEquals("The ORIGINAL clipboard callback must preserve every character",text,model.url)
   val button=compose.onNodeWithContentDescription("Paste link").fetchSemanticsNode().boundsInRoot
   val field=compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
   assertTrue("Compact control retains a >=48dp touch target",button.width>=47f && button.height>=47f)
   assertTrue("Paste must retain its inset from the field's end rim",field.right-button.right>=7f)
   assertTrue(button.left>field.left+40f);assertTrue(button.right<=field.right)
   assertFalse(model.busy);assertFalse(model.queuing);assertEquals(jobs,Store.list().map { it.id })
  }
 }
 @Test fun narrowLightShortAndLongUrlsKeepOriginalPasteAndInset()=checkPaste(false,false)
 @Test fun narrowDarkShortAndLongUrlsKeepOriginalPasteAndInset()=checkPaste(true,false)
 @Test fun narrowLargeFontKeepsThePasteTargetAndUnmodifiedContent()=checkPaste(false,true)
}
