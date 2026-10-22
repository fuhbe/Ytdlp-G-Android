package com.fuhbe.ytdlpg

import org.junit.Assert.*
import org.junit.Test

class MediaPolicyTest {
    private fun audio(ext: String = "webm", codec: String = "opus", bytes: Long = 2_000_000) =
        Format("audio", ext, 0, 134, false, true, codec, bytes)
    private fun video(height: Int) = Format("v$height", "mp4", height, 0, true, false, bytes = 8_000_000)
    private fun item(vararg formats: Format) = MediaItem("https://example.com/video", "Example", formats.toList(), duration = 60)

    @Test fun shareTrackingDoesNotChangeTheVideoRequest() {
        assertEquals("https://www.youtube.com/watch?v=xFrGuyw1V8s",
            normalizeMediaUrl(" https://youtu.be/xFrGuyw1V8s?si=tracking&t=12 "))
        assertEquals("https://www.youtube.com/watch?v=xFrGuyw1V8s",
            normalizeMediaUrl("https://www.youtube.com/shorts/xFrGuyw1V8s"))
    }

    @Test fun playlistSelectionSurvivesNormalization() {
        assertEquals("https://www.youtube.com/playlist?list=PL_example",
            normalizeMediaUrl("https://www.youtube.com/watch?v=xFrGuyw1V8s&list=PL_example&index=3"))
    }

    @Test fun nonYoutubeLinksKeepTheirRequiredQuery() {
        val url = "https://example.com/movie.mp4?access=a%2Fb"
        assertEquals(url, normalizeMediaUrl(url))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidYoutubeIdCannotCreateAnExtractorRequest() { normalizeMediaUrl("https://youtu.be/invalid") }

    @Test fun playlistsExposeIntersectionAndNotTheUnionOfFormats() {
        val choices = commonQualities(listOf(
            item(video(2160), video(720), audio()),
            item(video(1080), video(720), audio())
        ))
        assertEquals(listOf("v:720"), choices.filter { it.kind == "video" }.map { it.key })
    }

    @Test fun silentVideoIsNotAdvertisedAsVideoAndAudio() {
        assertTrue(commonQualities(listOf(item(video(720)))).isEmpty())
    }

    @Test fun webmAudioIsLabelledByItsCodecAndActualBitrate() {
        val choice = commonQualities(listOf(item(audio()))).first { it.key == "a:webm" }
        assertEquals("OPUS · 134 kbps", choice.label)
        assertEquals(2_000_000L, choice.bytes)
    }

    @Test fun unknownSizesDoNotProduceMisleadingPlaylistEstimates() {
        val choice = commonQualities(listOf(item(audio()), item(audio(bytes = 0)))).first { it.key == "a:webm" }
        assertEquals(0L, choice.bytes)
    }

    @Test fun mp3EstimateUsesTheEncodingTargetAndNotTheSourceBitrate() {
        val choice = commonQualities(listOf(item(audio()))).first { it.key == "mp3" }
        assertFalse(choice.original)
        assertEquals(2_400_000L, choice.bytes)
    }

    @Test fun defaultsAvoidAutomaticallyDownloading4kAndSelectExplicitMp3Conversion() {
        val choices = commonQualities(listOf(item(video(2160), video(720), audio())))
        assertEquals("v:720", preferredQuality(choices, "video")?.key)
        assertEquals("mp3", preferredQuality(choices, "audio")?.key)
    }
}
