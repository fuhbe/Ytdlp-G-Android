package com.fuhbe.ytdlpg.simple

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = SimpleApp::class)
class TransferProtocolTest {
    @Test fun processingUsesRealOutputTimeAndUnknownDurationIsIndeterminate() {
        val updates = mutableListOf<TransferProgress>(); val protocol = TransferProtocol(false, 10) { updates.add(it) }
        protocol.line("YG:{\"downloaded_bytes\":50,\"total_bytes\":100,\"speed\":10,\"eta\":5}")
        assertEquals(10L, updates.last().speed); assertNotNull(updates.last().fraction)
        protocol.line("YGPP:{\"phase\":\"CONVERT\",\"duration\":10}")
        assertNull(updates.last().fraction); assertEquals(0L, updates.last().speed)
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "ffmpeg.progress")
        file.writeText("out_time_us=5000000\nprogress=continue\n"); file.setLastModified(System.currentTimeMillis() + 100)
        protocol.ffmpeg(file); assertEquals(.865f, updates.last().fraction!!, .0001f)
        assertEquals(5_000_000L, protocol.processedMicros)
        val unknown = TransferProtocol(false, 0) { updates.add(it) }
        unknown.line("YGPP:{\"phase\":\"CONVERT\",\"duration\":0}"); unknown.ffmpeg(file)
        assertNull(updates.last().fraction)
    }
    @Test fun truncatedOutputOrIncompatibleCodecsNeverPassValidation() {
        val valid = """{"format":{"duration":"20"},"streams":[{"codec_type":"video","codec_name":"h264","height":1080,"nb_read_packets":"100","nb_frames":"100"},{"codec_type":"audio","codec_name":"aac","nb_read_packets":"100"}]}"""
        MediaValidator.inspect(valid, false, 20)
        for (bad in listOf(valid.replace("h264", "vp9"), valid.replace("\"nb_read_packets\":\"100\"", "\"nb_read_packets\":\"1\""), valid.replace("\"20\"", "\"2\"")))
            assertThrows(TransferException::class.java) { MediaValidator.inspect(bad, false, 20) }
    }
    @Test fun actualCliRequestUsesNativeFragmentsStrictFragmentFailureAndNoIgnoredPostprocessingErrors() {
        val app = RuntimeEnvironment.getApplication(); val media = Media("id", "https://youtu.be/id", "Title")
        val job = QueueJob(title = "One", items = listOf(media), choice = Choice(), folder = "")
        val args = DownloadRequest.make(job, media, app.cacheDir, 4, app.filesDir).buildCommand()
        assertEquals("4", args[args.indexOf("--concurrent-fragments") + 1])
        assertTrue(args.contains("--abort-on-unavailable-fragments")); assertFalse(args.contains("--ignore-errors"))
        assertFalse(args.any { it.contains("aria2") }); assertTrue(args.contains("mp4/mkv"))
    }
}
