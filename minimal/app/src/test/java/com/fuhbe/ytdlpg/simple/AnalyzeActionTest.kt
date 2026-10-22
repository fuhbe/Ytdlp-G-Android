package com.fuhbe.ytdlpg.simple

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Controlled failures test UI recovery. No YouTube download is represented here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = SimpleApp::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalyzeActionTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private class Backend : MediaBackend {
        var result = CompletableDeferred<Inspection>()
        var updateResult = CompletableDeferred<String>()
        val started = CompletableDeferred<Unit>()
        val updateStarted = CompletableDeferred<Unit>()
        override suspend fun inspect(context: Context, links: List<String>, progress: (String) -> Unit): Inspection {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            started.complete(Unit)
            return result.await()
        }
        override suspend fun update(context: Context): String {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            updateStarted.complete(Unit)
            return updateResult.await()
        }
    }
    private fun awaitState(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            // Resume viewModelScope on Robolectric's paused Android main looper.
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
    @Test fun analyzeClickSurvivesTheOldZipErrorAndCanRetrySuccessfully() {
        val backend = Backend()
        val model = AppModel(RuntimeEnvironment.getApplication(), backend)
        model.url = "https://youtu.be/123456789ab"
        compose.setContent { AppContent(model) }
        compose.onNodeWithText("Analisar link").performClick()
        awaitState { backend.started.isCompleted }
        compose.onNodeWithText("Cancelar análise").assertIsDisplayed()
        backend.result.completeExceptionally(ExceptionInInitializerError(RuntimeException("not a concrete class: secret")))
        awaitState { !model.busy }
        compose.onNodeWithText("RUNTIME_LINKAGE", substring = true).assertIsDisplayed()
        assertFalse(compose.activity.isFinishing)
        assertNull(model.inspection)
        backend.result = CompletableDeferred()
        compose.onNodeWithText("Analisar link").performClick()
        val video = Media("fixture", model.url, "Prévia após nova análise", heights = listOf(1080))
        backend.result.complete(Inspection(video.title, listOf(video), false, listOf(1080)))
        awaitState { model.inspection != null && !model.busy }
        compose.onNodeWithText(video.title).assertIsDisplayed()
        assertTrue(model.error.isEmpty())
    }
    @Test fun updateClickSurvivesLinkageFailureAndKeepsDiagnosticsAvailable() {
        val backend = Backend()
        val model = AppModel(RuntimeEnvironment.getApplication(), backend).apply { area = "settings" }
        compose.setContent { AppContent(model) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Verificar e atualizar yt-dlp"))
        compose.onNodeWithText("Verificar e atualizar yt-dlp").performClick()
        awaitState { backend.updateStarted.isCompleted }
        backend.updateResult.completeExceptionally(NoClassDefFoundError("secretNativeDetails"))
        awaitState { !model.updating }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("RUNTIME_LINKAGE", substring = true))
        compose.onNodeWithText("RUNTIME_LINKAGE", substring = true).assertIsDisplayed()
        assertFalse(compose.activity.isFinishing)
        val report = Diagnostics.snapshot(RuntimeEnvironment.getApplication())
        assertTrue(report.contains("NoClassDefFoundError"))
        assertFalse(report.contains("secretNativeDetails"))
    }
    @Test fun cancellingAnalysisDoesNotPublishLateMetadataOrAnError() {
        val backend = Backend()
        val model = AppModel(RuntimeEnvironment.getApplication(), backend).apply { url = "https://youtu.be/123456789ab" }
        compose.setContent { AppContent(model) }
        compose.onNodeWithText("Analisar link").performClick()
        awaitState { backend.started.isCompleted }
        compose.onNodeWithText("Cancelar análise").performClick()
        backend.result.complete(Inspection("Late fixture", emptyList(), false, emptyList()))
        compose.waitForIdle()
        assertFalse(model.busy); assertNull(model.inspection); assertTrue(model.error.isEmpty())
        compose.onNodeWithText("Analisar link").assertIsDisplayed()
    }
    @Test fun processHttpFailureIsAVisibleErrorNotASuccessOrActivityClose() {
        val backend = Backend()
        val model = AppModel(RuntimeEnvironment.getApplication(), backend).apply { url = "https://youtu.be/123456789ab" }
        compose.setContent { AppContent(model) }
        compose.onNodeWithText("Analisar link").performClick()
        backend.result.completeExceptionally(java.io.IOException("HTTP Error 403: Forbidden"))
        awaitState { !model.busy }
        compose.onNodeWithText("403", substring = true).assertIsDisplayed()
        assertNull(model.inspection); assertFalse(compose.activity.isFinishing)
    }
}
