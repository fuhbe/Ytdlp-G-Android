package com.fuhbe.ytdlpg.simple

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
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
class SquareGlassReducedMotionTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>(
        effectContext=object:MotionDurationScale { override val scaleFactor=0f })
    @Test fun zeroDurationScaleSettlesWithoutBlockingInputOrLeavingAStalePane() {
        var value=0
        compose.setContent {
            var selected by remember { mutableIntStateOf(0) }
            GlassTheme(false) {
                GlassSlidingSegments(selected,2,{selected=it;value=it},Modifier.fillMaxWidth()) {
                    listOf("MP3","MP4").forEachIndexed { index,label ->
                        Surface(onClick={selected=index;value=index},modifier=Modifier.weight(1f).height(48.dp).glassSegmentItem(index).testTag("item-$index")) { Text(label) }
                    }
                }
            }
        }
        compose.onNodeWithText("MP4").performClick();compose.waitForIdle()
        assertEquals(1,value)
        val pane=compose.onNodeWithTag("glass-selection-capsule").fetchSemanticsNode().boundsInRoot
        val selected=compose.onNodeWithTag("item-1").fetchSemanticsNode().boundsInRoot
        assertEquals(selected.left,pane.left,1f);assertEquals(selected.width,pane.width,1f)
        compose.onNodeWithText("MP3").performClick();compose.waitForIdle();assertEquals(0,value)
    }
}
