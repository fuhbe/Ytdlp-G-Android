package com.fuhbe.ytdlpg

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class GlassScreenTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(locale = "pt-rBR", softButtons = false),
        theme = "android:style/Theme.Material.Light.NoActionBar"
    )
    private val item = MediaItem("https://example.com/video", "Sample playlist", emptyList(), uploader = "Sample artist", duration = 120)
    private val choices = listOf(
        Quality("a:webm", "OPUS · 160 kbps", "audio", 4_000_000),
        Quality("a:m4a", "M4A · 128 kbps", "audio", 3_000_000),
        Quality("mp3", "MP3", "audio", 4_000_000, false),
        Quality("v:720", "720p", "video", 12_000_000)
    )
    private val inspection = Inspection("Sample playlist", "", List(12) { item }, choices, 0, "Sample artist")

    @Test fun editorMatchesTheGlassLayout() {
        paparazzi.snapshot {
            GlassTheme {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                    Header(false) {}
                    EditorContent(EditorState(
                        url = "https://www.youtube.com/playlist?list=example",
                        inspection = inspection, quality = choices[2], kind = "audio", zip = true
                    ))
                }
            }
        }
    }

    @Test fun downloadingHasItsOwnProgressAndCompletedItemsScreen() {
        paparazzi.snapshot {
            GlassTheme {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                    Header(true) {}
                    DownloadContent(
                        DownloadState.Task(inspection, choices[2], null, true),
                        true, "Baixando playlist", 0.42f, 4, "[download] 42% of 7.6MiB at 5.4MiB/s",
                        List(4) { ItemResult(item.copy(title = "Completed track ${it + 1}"),
                            bytes = 4_100_000, uri = android.net.Uri.parse("content://example/track/$it")) }, {}, {}
                    )
                }
            }
        }
    }
}
