package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
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
import java.io.File

/** Real Compose layouts; host/software screenshots represent the fallback path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26,28,31,33,35], application = SimpleApp::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassApiMatrixTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun render(area: String = "home", playlist: Boolean = false, dark: Boolean = false, large: Boolean = false) {
        val model = AppModel(RuntimeEnvironment.getApplication()).apply { this.area = area; theme = if (dark) "dark" else "light" }
        val items = (1..if (playlist) 3 else 1).map { Media("fixture-$it", "https://youtu.be/123456789ab", "Example video $it", heights = listOf(1080,720)) }
        model.inspection = Inspection("Example playlist",items,playlist,listOf(1080,720));model.selected=items.map{it.id}.toSet()
        compose.setContent { val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if (large) 1.8f else 1f)) { AppContent(model) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
        compose.onNodeWithText("Ytdlp-G",useUnmergedTree=true).assertIsDisplayed()
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        val dock=compose.onNodeWithTag("glass-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(dock.left>=root.left && dock.right<=root.right && dock.bottom<=root.bottom)
        if(area=="settings") {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Copy diagnostics"))
            compose.onNodeWithText("Copy diagnostics").assertIsDisplayed()
        }
        val name="api-${Build.VERSION.SDK_INT}-$area-${if(playlist) "playlist" else "single"}-${if(dark) "dark" else "light"}${if(large) "-large" else ""}"
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File("build/reports/liquid-glass/$name.png").apply{parentFile?.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        assertFalse(compose.activity.isFinishing)
    }
    @Test fun homeLight()=render()
    @Test fun playlistDark()=render(playlist=true,dark=true)
    @Test fun managerLight()=render(area="downloads")
    @Test fun settingsDark()=render(area="settings",dark=true)
    @Test @Config(sdk=[31],qualifiers="w320dp-h640dp-mdpi") fun largeFontSettings()=render(area="settings",large=true)
}
