package com.fuhbe.ytdlpg.simple

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31],application=SimpleApp::class,qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassContrastTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun disabledAnalyzeRetainsItsBehaviorAndReadableForegroundInBothThemes() {
        val model=AppModel(RuntimeEnvironment.getApplication()).apply { theme="light" }
        compose.setContent { AppContent(model) }
        fun foreground():Float {
            compose.onNodeWithText("Analisar link").assertIsNotEnabled()
            val results=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Analisar link",useUnmergedTree=true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
            assertFalse(model.busy);assertTrue(model.url.isBlank())
            return results.single().layoutInput.style.color.luminance()
        }
        assertTrue("Bright glass needs a dark disabled-action label",foreground()<.2f)
        compose.runOnIdle { model.theme="dark" }
        assertTrue("Dark glass needs a light disabled-action label",foreground()>.7f)
    }
}
