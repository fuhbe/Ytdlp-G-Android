package com.fuhbe.ytdlpg.simple

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
@OptIn(ExperimentalTestApi::class)
class Glass067ReducedMotionTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>(
        effectContext=object:MotionDurationScale { override val scaleFactor=0f })
    @Test fun buttonAndMenuSettleAndRemainActionableWithoutAnimation() {
        val button=ElasticButtonStats();val menu=GlassMorphStats();var clicks=0;var quality=1080
        compose.setContent { GlassTheme(false) {
            CompositionLocalProvider(LocalElasticButtonStats provides button,LocalGlassMorphStats provides menu) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    GlassButton({clicks++},Modifier.height(56.dp).testTag("button")) { Text("Analyze link") }
                    GlassQualityPicker(listOf(0,1080,720),quality) { quality=it }
                }
            }
        } }
        compose.onNodeWithTag("button").performTouchInput { down(center);moveBy(Offset(70f,20f));up() }
        compose.waitForIdle();assertEquals(0,clicks);assertEquals(0f,button.motion!!.level,.001f)
        compose.onNodeWithTag("button").performClick();assertEquals(1,clicks)
        compose.onNodeWithTag("glass-quality-trigger").performClick();compose.waitForIdle()
        assertEquals(1f,menu.state!!.progress.value,.001f)
        compose.onNodeWithTag("glass-quality-720").performClick();compose.waitForIdle()
        assertEquals(720,quality);assertFalse(menu.state!!.mounted)
        compose.onNodeWithTag("glass-quality-trigger").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle();assertFalse(menu.state!!.mounted)
        compose.onNodeWithTag("button").performClick();assertEquals(2,clicks)
    }
}
