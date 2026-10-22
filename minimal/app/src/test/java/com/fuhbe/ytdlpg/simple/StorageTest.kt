package com.fuhbe.ytdlpg.simple

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SimpleApp::class)
class StorageTest {
    @Test fun writesAndVerifiesAStreamingArchiveWithoutDeletingSources() {
        val root = File(RuntimeEnvironment.getApplication().cacheDir, "zip-test").apply { mkdirs() }
        val a = File(root, "a.mp4").apply { writeBytes(ByteArray(200_000) { (it % 251).toByte() }) }
        val b = File(root, "b.mp3").apply { writeText("content".repeat(300)) }
        val archive = File(root, "result.zip")
        var progress = 0f
        Storage.archive(listOf(a, b), archive, {}) { progress = it }
        assertEquals(1f, progress, .00001f)
        ZipFile(archive).use { zip ->
            assertEquals(2, zip.size())
            assertArrayEquals(a.readBytes(), zip.getInputStream(zip.getEntry("001-a.mp4")).readBytes())
        }
        assertTrue(a.exists()); assertTrue(b.exists())
    }
    @Test fun cancellationRemovesOnlyThePartialArchive() {
        val root = File(RuntimeEnvironment.getApplication().cacheDir, "cancel-test").apply { mkdirs() }
        val files = (1..2).map { File(root, "$it.mp4").apply { writeBytes(ByteArray(300_000)) } }
        val archive = File(root, "cancelled.zip")
        var checks = 0
        assertThrows(IllegalStateException::class.java) { Storage.archive(files, archive, { check(checks++ < 3) }, {}) }
        assertFalse(archive.exists()); assertFalse(File(archive.path + ".part").exists())
        assertTrue(files.all { it.exists() })
        assertThrows(IllegalArgumentException::class.java) { Storage.archive(files.take(1), archive, {}, {}) }
    }
    @Test fun queueControlsAndSavedLocationsSurviveDatabaseReopening() {
        val app = RuntimeEnvironment.getApplication()
        val job = QueueJob(title = "Real queue record", items = listOf(Media("id", "https://youtu.be/123456789ab", "Video")), choice = Choice(), folder = "content://documents/tree/selected")
        Store.insert(job)
        Store.edit(job.id) { it.copy(state = "PAUSED", progress = .42f, phase = "Pausado") }
        Store.init(app)
        assertEquals("PAUSED", Store.get(job.id).state)
        assertEquals(.42f, Store.get(job.id).progress, .00001f)
        assertEquals(job.folder, Store.get(job.id).folder)
    }
    @Test fun metadataSeparatesVideoFromAudioAndExcludesDrmAndUnavailableResolutions() {
        val media = Engine.parseMedia(JSONObject("""{"id":"123456789ab","title":"Video","duration":204,"formats":[
          {"url":"https://media/1","vcodec":"avc1","acodec":"none","height":1080},
          {"url":"https://media/2","vcodec":"none","acodec":"opus"},
          {"url":"https://media/3","vcodec":"vp9","acodec":"none","height":4320},
          {"url":"https://media/4","vcodec":"avc1","height":2160,"has_drm":true}]}"""), "https://youtu.be/123456789ab")!!
        assertEquals(listOf(1080), media.heights)
        assertEquals(204L, media.duration)
        assertTrue(media.available)
    }
}
