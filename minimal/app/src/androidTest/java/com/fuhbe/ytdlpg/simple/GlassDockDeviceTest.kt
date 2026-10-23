package com.fuhbe.ytdlpg.simple

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android input/window acceptance. Compiling this is not executing it. */
@RunWith(AndroidJUnit4::class)
class GlassDockDeviceTest {
    @Test fun hardwareDockDragAndCancelledOutsideReleasePreserveCallbacks() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val device=UiDevice.getInstance(instrumentation)
        val calls=mutableListOf<String>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.isHardwareAccelerated)
                activity.setContent {
                    var area by remember { mutableStateOf("home") }
                    GlassTheme(false) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),contentAlignment=Alignment.BottomCenter) {
                            GlassNavigationDock(area,{ calls+="home";area="home" },
                                { calls+="downloads";area="downloads" },{ calls+="settings";area="settings" },null,Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync();device.waitForIdle(2000)
            val first=device.findObject(By.desc("Voltar")).visibleBounds
            val last=device.findObject(By.desc("Configurações")).visibleBounds
            assertTrue(device.swipe(first.centerX(),first.centerY(),last.centerX(),last.centerY(),30))
            instrumentation.waitForIdleSync();device.waitForIdle(2000)
            scenario.onActivity { assertEquals(listOf("settings"),calls) }
            // Release above the complete dock after crossing another destination.
            val x=intArrayOf(last.centerX(),first.centerX(),first.centerX())
            val y=intArrayOf(last.centerY(),first.centerY(),first.top-60)
            val points=x.indices.map { android.graphics.Point(x[it],y[it]) }.toTypedArray()
            assertTrue(device.swipe(points,20))
            instrumentation.waitForIdleSync();device.waitForIdle(2000)
            scenario.onActivity { assertEquals(listOf("settings"),calls) }
        }
    }
}
