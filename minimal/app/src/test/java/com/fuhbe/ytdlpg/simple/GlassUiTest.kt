package com.fuhbe.ytdlpg.simple

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Fixture media is only for UI verification. No download success is simulated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SimpleApp::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun model(playlist: Boolean = false): AppModel {
        val model = AppModel(RuntimeEnvironment.getApplication())
        val items = (1..if (playlist) 6 else 1).map { Media("test-$it", "https://youtu.be/123456789ab", if (playlist) "Música para estudar com natureza $it" else "Natureza incrível em 4K — paisagens que relaxam", "Canal de referência", duration = 204, heights = listOf(2160, 1080, 720)) }
        model.inspection = Inspection(if (playlist) "Músicas para relaxar e estudar" else items.first().title, items, playlist, listOf(2160, 1080, 720))
        model.selected = items.map { it.id }.toSet()
        model.url = items.first().url
        return model
    }
    private fun render(model: AppModel, name: String, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { AppContent(model) }
        }
        compose.waitForIdle()
        val output = File("build/reports/glass/$name.png").apply { parentFile.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Ytdlp-G", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun individualScreenHasRealMp4SelectionAndNoZip() {
        render(model(), "individual-light")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("MP4"))
        compose.onNodeWithText("MP4").assertIsDisplayed()
        compose.onAllNodesWithText("ZIP", substring = true).assertCountEquals(0)
    }
    @Test fun playlistSelectionsControlWhetherZipIsAvailable() {
        val model = model(true)
        render(model, "playlist-light")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("ZIP"))
        compose.onNodeWithText("ZIP").assertIsDisplayed()
        compose.runOnIdle { model.selected = setOf(model.inspection!!.items.first().id); model.choice = Policy.choice(model.choice, 1) }
        compose.onAllNodesWithText("ZIP").assertCountEquals(0)
    }
    @Test @Config(qualifiers = "w320dp-h640dp-mdpi") fun smallScreenAndLargeFontsCanReachTheDownloadButton() {
        val model = model()
        model.theme = "dark"
        render(model, "small-dark-large-font", 1.8f)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Baixar"))
        compose.onNodeWithText("Baixar", useUnmergedTree = true).assertIsDisplayed()
        val bounds = compose.onNodeWithText("Baixar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= root.left && bounds.right <= root.right)
        assertTrue(bounds.top >= root.top && bounds.bottom <= root.bottom)
    }
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi") fun tabletUsesReadableCenteredContent() {
        render(model(true), "tablet-playlist")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Baixar playlist"))
        compose.onNodeWithText("Baixar playlist", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w720dp-h400dp-mdpi") fun landscapeRemainsScrollable() {
        render(model(), "landscape")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Baixar"))
        compose.onNodeWithText("Baixar", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun managerShowsActualStoredStateAndRetryControl() {
        val model = model()
        model.area = "downloads"
        val items = model.inspection!!.items
        Store.insert(QueueJob(title = items.first().title, items = items, choice = Choice(), folder = "", state = "ERROR", progress = .42f, error = "HTTP 403", phase = "Falha no download"))
        render(model, "manager-error")
        compose.onNodeWithText("HTTP 403").assertIsDisplayed()
        compose.onNodeWithContentDescription("Retomar ${items.first().title}").assertIsDisplayed()
    }
}
