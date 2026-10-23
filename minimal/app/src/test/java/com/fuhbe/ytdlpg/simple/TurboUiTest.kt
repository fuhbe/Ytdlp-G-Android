package com.fuhbe.ytdlpg.simple

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = SimpleApp::class, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TurboUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun model() = AppModel(RuntimeEnvironment.getApplication()).apply { area = "downloads"; theme = "dark" }
    @Test fun unknownConversionProgressIsIndeterminateAndDoesNotDisplayZeroPercent() {
        val item = Media("a", "https://youtu.be/a", "Conversion fixture")
        Store.insert(QueueJob(title = item.title, items = listOf(item), choice = Choice(), folder = "", state = "RUNNING",
            phase = "Convertendo codecs para MP4…", progress = .7f, indeterminate = true))
        compose.setContent { AppContent(model()) }
        compose.onNodeWithText("Convertendo codecs para MP4…").assertIsDisplayed()
        compose.onAllNodesWithText("0%", substring = true).assertCountEquals(0)
    }
    @Test fun warningsOpenAPerItemReportAndOnlyEligibleFailuresOfferRetry() {
        val items = (1..3).map { Media("$it", "https://youtu.be/$it", "Item $it") }
        Store.insert(QueueJob(title = "Playlist fixture", items = items, choice = Choice(mp3 = true), folder = "", state = "WARNING",
            progress = 1f, phase = "Playlist finalizada — 3 itens encontrados; 1 baixados; 2 não baixados",
            records = listOf(ItemRecord(0, "1", state = "DONE"), ItemRecord(1, "2", state = "FAILED", reason = "Vídeo indisponível", retryable = false),
                ItemRecord(2, "3", state = "FAILED", reason = "Falha de conexão", retryable = true))))
        compose.setContent { AppContent(model()) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Review unsuccessful items (2)"))
        compose.onNodeWithText("Review unsuccessful items (2)").performClick()
        compose.onNodeWithText("2. Item 2").assertIsDisplayed()
        compose.onNodeWithText("Vídeo indisponível").assertIsDisplayed()
        compose.onNodeWithText("Retry (1)").assertIsDisplayed()
    }
}
