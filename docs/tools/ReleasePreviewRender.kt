package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Documentation-only UI renders. Sample metadata never invokes a downloader. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SimpleApp::class, qualifiers = "w411dp-h1060dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleasePreviewRender {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun media(i: Int) = Media("preview-$i", "https://youtu.be/123456789ab", "Natureza e paisagens — episódio $i", "Canal de exemplo", duration = 204, heights = listOf(2160, 1080, 720))
    private fun show(name: String, playlist: Boolean = false, dark: Boolean = false, downloads: Boolean = false) {
        val model = AppModel(RuntimeEnvironment.getApplication())
        val items = (1..if (playlist || downloads) 4 else 1).map(::media)
        model.theme = if (dark) "dark" else "light"
        if (downloads) {
            model.area = "downloads"
            Store.insert(QueueJob(id = "preview-running", title = "Músicas para relaxar e estudar", items = items, choice = Choice(mp3 = true, zip = true), folder = "", state = "RUNNING", progress = .64f, current = 2, phase = "Baixando áudio…", speed = 2_400_000, eta = 18, indeterminate = false,
                records = items.mapIndexed { i, m -> ItemRecord(i, m.id, state = if (i < 2) "DONE" else if (i == 2) "RUNNING" else "PENDING", indeterminate = false) }))
            Store.insert(QueueJob(id = "preview-paused", title = "Natureza em 4K", items = listOf(items.first()), choice = Choice(height = 1080), folder = "", state = "PAUSED", progress = .38f, phase = "Pausado — dados parciais preservados", indeterminate = false))
            val file = SavedFile(items.last().id, "/preview/example.mp4", bytes = 18_400_000, title = items.last().title, thumb = "", height = 1080)
            Store.insert(QueueJob(id = "preview-completed", title = file.title, items = listOf(items.last()), choice = Choice(), folder = "", state = "DONE", progress = 1f, current = 1, phase = "Concluído", files = listOf(file), indeterminate = false))
        } else {
            model.inspection = Inspection(if (playlist) "Músicas para relaxar e estudar" else items.first().title, items, playlist, listOf(2160, 1080, 720))
            model.selected = items.map { it.id }.toSet()
            model.url = items.first().url
            model.choice = Choice(mp3 = playlist, zip = playlist)
        }
        compose.setContent { AppContent(model) }
        compose.waitForIdle()
        val output = File("../../docs/screenshots/$name.png").apply { parentFile.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun singleVideo() = show("single-video")
    @Test fun playlist() = show("playlist", playlist = true)
    @Test fun downloadManager() = show("downloads", downloads = true)
    @Test fun darkTheme() = show("dark-theme", dark = true, downloads = true)
    @Test fun launcherIcon() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val icon = androidx.core.content.ContextCompat.getDrawable(RuntimeEnvironment.getApplication(), R.drawable.ic_launcher)!!
        icon.setBounds(0, 0, 512, 512)
        icon.draw(android.graphics.Canvas(bitmap))
        File("../../docs/branding/icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
