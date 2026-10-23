package com.fuhbe.ytdlpg.simple

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = SimpleApp::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun model(playlist: Boolean = false): AppModel {
        val items = (1..if (playlist) 3 else 1).map { Media("item-$it", "https://youtu.be/123456789ab", "Example $it", heights = listOf(1080, 720)) }
        return AppModel(RuntimeEnvironment.getApplication()).apply {
            inspection = Inspection("Examples", items, playlist, listOf(1080, 720))
            selected = items.map { it.id }.toSet()
            url = items.first().url
        }
    }
    private fun scrollTo(label: String) = compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
    @Test fun relocatedNavigationUsesTheExistingDestinationsAndBackBehavior() {
        val model = model()
        compose.setContent { AppContent(model) }
        compose.onNodeWithContentDescription("Downloads").performClick()
        compose.runOnIdle { assertEquals("downloads", model.area) }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.runOnIdle { assertEquals("home", model.area); assertNotNull(model.inspection) }
        compose.onNodeWithContentDescription("Configurações").performClick()
        compose.runOnIdle { assertEquals("settings", model.area) }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.runOnIdle { assertEquals("home", model.area) }
    }
    @Test fun formatQualityAndZipKeepTheirOriginalModelValuesAndSelectionRules() {
        val model = model(true)
        compose.setContent { AppContent(model) }
        scrollTo("MP3"); compose.onNodeWithText("MP3").performClick()
        compose.runOnIdle { assertTrue(model.choice.mp3) }
        compose.onNodeWithText("MP4").performClick()
        scrollTo("Melhor qualidade")
        compose.onNodeWithContentDescription("Escolher qualidade").performClick()
        compose.onNodeWithText("720p").performClick()
        compose.runOnIdle { assertFalse(model.choice.mp3); assertEquals(720, model.choice.height) }
        scrollTo("ZIP"); compose.onNodeWithText("ZIP").performClick()
        compose.runOnIdle { assertTrue(model.choice.zip); model.selected = setOf("item-1"); model.choice = Policy.choice(model.choice, 1) }
        compose.onAllNodesWithText("ZIP").assertCountEquals(0)
        compose.runOnIdle { assertFalse(model.choice.zip) }
    }
    @Test @Config(qualifiers = "w320dp-h640dp-mdpi") fun downloadActionRemainsAboveTheDockOnSmallScreens() {
        compose.setContent { AppContent(model()) }
        scrollTo("Baixar")
        val download = compose.onNodeWithText("Baixar").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("glass-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Dock must not cover the download action", download.bottom <= dock.top)
    }
    @Test fun destinationStillLaunchesTheAndroidDocumentTreePicker() {
        val model = model()
        compose.setContent { AppContent(model) }
        scrollTo("Pasta de destino")
        compose.onNodeWithContentDescription("Alterar pasta").performClick()
        compose.runOnIdle {
            val started = shadowOf(compose.activity).nextStartedActivityForResult
            assertNotNull(started)
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, started.intent.action)
        }
    }
    @Test fun pauseResumeCancelAndEligibleRetryDispatchTheExactOriginalIds() {
        val item = Media("a", "https://youtu.be/123456789ab", "A")
        val active = QueueJob(id = "active", title = "A", items = listOf(item), choice = Choice(), folder = "", state = "RUNNING")
        val paused = active.copy(id = "paused", title = "B", items = listOf(item.copy(title = "B")), state = "PAUSED")
        val failed = active.copy(id = "failed", title = "C", items = listOf(item.copy(title = "C")), state = "ERROR", records = listOf(ItemRecord(0,"a",state="FAILED",reason="Network",retryable=true)))
        val calls = mutableListOf<Pair<String, String>>()
        compose.setContent { GlassTheme(false) { DownloadsScreen(listOf(active, paused, failed)) { id, action -> calls += id to action } } }
        compose.onNodeWithContentDescription("Pausar A").performClick()
        compose.onNodeWithContentDescription("Cancelar A").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Retomar B"))
        compose.onNodeWithContentDescription("Retomar B").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Ver não baixados (1)"))
        compose.onNodeWithText("Ver não baixados (1)").performClick()
        compose.onNodeWithText("Tentar novamente (1)").performClick()
        assertEquals(listOf("active" to "pause", "active" to "cancel", "paused" to "resume", "failed" to "retry"), calls)
    }
    @Test @Config(sdk = [33]) fun exactDependencyLensShaderCompilesAndRefractsRealPixels() {
        val field = Class.forName("com.kyant.backdrop.ShadersKt").getDeclaredField("RoundedRectRefractionShaderString")
        field.isAccessible = true
        // Known host-native Skia limitation must not be mistaken for a library error.
        try { RuntimeShader("uniform shader content; half4 main(float2 p) { return content.eval(p); }") }
        catch (e: IllegalArgumentException) {
            if (e.message.orEmpty().contains("cannot swizzle value of type 'shader'"))
                org.junit.Assume.assumeNoException("Host Skia cannot compile even the minimal child-shader control; run the native device test", e)
            else throw e
        }
        val shader = RuntimeShader(field.get(null) as String)
        shader.setFloatUniform("size", 64f, 64f)
        shader.setFloatUniform("offset", 0f, 0f)
        shader.setFloatUniform("cornerRadii", 12f, 12f, 12f, 12f)
        shader.setFloatUniform("refractionHeight", 10f)
        shader.setFloatUniform("refractionAmount", -12f)
        shader.setFloatUniform("depthEffect", 1f)
        val input = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) for (x in 0 until 64) input.setPixel(x, y, if ((x/4+y/4)%2==0) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        shader.setInputShader("content", BitmapShader(input, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        val output = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        Canvas(output).drawRect(0f,0f,64f,64f,Paint().apply { this.shader = shader })
        assertEquals(input.getPixel(32,32), output.getPixel(32,32))
        val changed = (1..10).sumOf { x -> (12..52).count { y -> input.getPixel(x,y) != output.getPixel(x,y) } }
        assertTrue("The actual AGSL lens must displace pixels at an edge", changed > 20)
    }
    @Test fun renderingCapabilitiesRespectApiHardwareMemoryAndPowerSaver() {
        assertEquals(GlassRendering.TINT, glassRendering(26,true,false,256,false))
        assertEquals(GlassRendering.TINT, glassRendering(28,true,false,256,false))
        assertEquals(GlassRendering.BLUR, glassRendering(31,true,false,256,false))
        assertEquals(GlassRendering.BLUR, glassRendering(32,true,false,256,false))
        assertEquals(GlassRendering.LENS, glassRendering(33,true,false,256,false))
        assertEquals(GlassRendering.TINT, glassRendering(35,false,false,256,false))
        assertEquals(GlassRendering.TINT, glassRendering(35,true,true,256,false))
        assertEquals(GlassRendering.TINT, glassRendering(35,true,false,128,false))
        assertEquals(GlassRendering.TINT, glassRendering(35,true,false,256,true))
    }
}
