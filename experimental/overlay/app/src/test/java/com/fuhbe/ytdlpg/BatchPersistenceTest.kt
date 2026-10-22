package com.fuhbe.ytdlpg

import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.AudioPreferences
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.VideoPreferences
import com.deniscerri.ytdl.database.models.Format as CoreFormat
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat

class BatchPersistenceTest {
    private fun download() = DownloadItem(
        id = 12345, url = "https://example.org/media", title = "Example", author = "Artist",
        thumb = "", duration = "1:00", type = DownloadType.video,
        format = CoreFormat(format_id = "best", _height = "1080"),
        container = "Default", downloadSections = "", allFormats = mutableListOf(),
        downloadPath = "/private/batch/media/0", website = "example.org", downloadSize = "",
        playlistTitle = "Playlist", audioPreferences = AudioPreferences(embedThumb = false),
        videoPreferences = VideoPreferences(embedSubs = false, addChapters = false),
        extraCommands = "-f \"bv[height=1080]+ba/b[height=1080]\"", customFileNameTemplate = "%(id)s.%(ext)s",
        SaveThumb = false, status = "Queued", downloadStartTime = 0, logID = null)

    @Test fun processRestartRetainsQueueIdsQualityFolderAndCompletedExports() {
        val media = MediaItem("https://example.org/media", "Example", emptyList())
        val quality = Quality("v:1080", "MP4 · 1080p", "video", original = false)
        val inspection = Inspection("Playlist", "", listOf(media), listOf(quality), 0)
        val original = Batch("batch", inspection, quality, "content://provider/tree/music", true,
            listOf(BatchEntry(media, download(), paths = listOf("/private/output.mp4"),
                outputUri = "content://downloads/123", saved = true)), started = true,
            progress = 0.9f, archiveUri = "content://downloads/123")
        val restored = Gson().fromJson(Gson().toJson(original), Batch::class.java)
        assertEquals(original.id, restored.id)
        assertEquals(original.folder, restored.folder)
        assertEquals(original.quality, restored.quality)
        assertEquals(12345L, restored.entries.single().download.id)
        assertEquals(1080, restored.entries.single().download.format.height)
        assertTrue(restored.entries.single().saved)
        assertEquals("content://downloads/123", restored.entries.single().outputUri)
        assertEquals(original.entries.single().download.extraCommands, restored.entries.single().download.extraCommands)
    }

    @Test fun originalAudioAndVideoContainersUseRealSuffixesRatherThanDisplayNames() {
        // NewPipe's display name is "WebM Opus" and is not a valid yt-dlp ext filter.
        assertEquals("webm", MediaFormat.WEBMA_OPUS.suffix)
        assertEquals("m4a", MediaFormat.M4A.suffix)
        assertEquals("mp4", MediaFormat.MPEG_4.suffix)
        assertNotEquals(MediaFormat.WEBMA_OPUS.name, MediaFormat.WEBMA_OPUS.suffix)
    }
}
