package com.fuhbe.ytdlpg.simple
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32],application=SimpleApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MorphSafetyTest {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 @Test fun emptyInitialOptionsNeverCreateAnInvisibleBlockingWindow() {
  var calls=0
  compose.setContent { GlassTheme(false) { Column(Modifier.fillMaxSize().padding(16.dp)) { GlassQualityPicker(emptyList(),0) { calls++ } } } }
  compose.onNodeWithTag("glass-quality-trigger").performClick();compose.waitForIdle()
  compose.onAllNodesWithTag("glass-morph-window").assertCountEquals(0);assertEquals(0,calls)
 }
 @Test fun losingAllOptionsDuringFlightCancelsWithoutSelectingAnything() {
  val values=mutableStateOf(listOf(0,1080,720));var calls=0
  compose.setContent { GlassTheme(false) { Column(Modifier.fillMaxSize().padding(16.dp)) { GlassQualityPicker(values.value,0) { calls++ } } } }
  compose.onNodeWithTag("glass-quality-trigger").performClick()
  compose.runOnIdle { values.value=emptyList() };compose.waitForIdle()
  compose.onAllNodesWithTag("glass-morph-window").assertCountEquals(0);assertEquals(0,calls)
 }
}
