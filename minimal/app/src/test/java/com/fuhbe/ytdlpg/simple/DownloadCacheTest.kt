package com.fuhbe.ytdlpg.simple

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = SimpleApp::class)
class DownloadCacheTest {
    @Test fun integrityRetryPreservesRejectedSourceAndPartialsButFreesTheFinalFilename() {
        val entry = File(RuntimeEnvironment.getApplication().cacheDir, "integrity-retry").apply { mkdirs() }
        val corrupt = File(entry, "output.mp4").apply { writeText("corrupt fixture") }
        val partial = File(entry, "output.webm.part").apply { writeText("resumable fixture") }
        DownloadCache.rejectCompleted(entry)
        assertFalse(corrupt.exists())
        assertEquals("resumable fixture", partial.readText())
        assertEquals("corrupt fixture", entry.listFiles()!!.single { it.isDirectory }.resolve("output.mp4").readText())
    }
    @Test fun validatedOutputsCanBeReusedWithoutNetworkButChangedOrPartialFilesCannot() {
        val entry = File(RuntimeEnvironment.getApplication().cacheDir, "validated-cache").apply { mkdirs() }
        val file = File(entry, "done.mp4").apply { writeText("controlled validation marker fixture") }
        val media = Media("id", "https://youtu.be/id", "Item")
        assertNull(DownloadCache.load(entry, media, Choice()))
        DownloadCache.save(entry, media, Choice(), JSONObject().put("filepath", file.path).put("height", 180))
        assertEquals(file.path, DownloadCache.load(entry, media, Choice())!!.getString("filepath"))
        assertNull(DownloadCache.load(entry, media, Choice(height = 720)))
        assertNull(DownloadCache.load(entry, media.copy(id = "another"), Choice()))
        file.appendText("changed"); assertNull(DownloadCache.load(entry, media, Choice()))
    }
    @Test fun cleanupRemovesIntermediatesOnlyAfterTheCommitHookAndKeepsFinalAndRecoveryMarkers() = kotlinx.coroutines.runBlocking {
        val entry = File(RuntimeEnvironment.getApplication().cacheDir, "cleanup-test").apply { mkdirs() }
        val final = File(entry, "final.mp4").apply { writeText("fixture") }
        val original = File(entry, "original.webm").apply { writeText("source fixture") }
        val marker = File(entry, "validated.json").apply { writeText("marker") }
        val item = Media("id", "https://youtu.be/id", "Item")
        val job = QueueJob(title = "One", items = listOf(item), choice = Choice(), folder = "", state = "RUNNING")
        Store.insert(job)
        BatchRunner(job.id).run(1, {}, onSaved = {
            assertEquals("DONE", Store.get(job.id).records.single().state)
            assertTrue(original.exists()); DownloadCache.cleanIntermediates(it)
        }) { _, _, _ -> SavedFile(item.id, final.path, bytes = final.length(), title = item.title, thumb = "") }
        assertTrue(final.exists()); assertTrue(marker.exists()); assertFalse(original.exists())
    }
    @Test fun cancelledOrPermanentFailuresDoNotBecomeRetryTargets() {
        val items = listOf(Media("a", "https://youtu.be/a", "A"), Media("b", "https://youtu.be/b", "B"))
        val job = QueueJob(title = "Two", items = items, choice = Choice(), folder = "", state = "WARNING",
            records = listOf(ItemRecord(0, "a", state = "DONE"), ItemRecord(1, "b", state = "FAILED", retryable = false)))
        assertEquals(job, QueueActions.apply(job, "retry"))
    }
}
