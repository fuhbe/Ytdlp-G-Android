package com.fuhbe.ytdlpg.simple

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = SimpleApp::class)
class BatchRunnerTest {
    private fun job(count: Int = 16): QueueJob = QueueJob(title = "Playlist", items = (1..count).map { Media("$it", "https://youtu.be/example$it", "Item $it") },
        choice = Choice(mp3 = true), folder = "", state = "RUNNING").also { Store.insert(it) }
    private fun saved(media: Media) = File(RuntimeEnvironment.getApplication().cacheDir, media.id + ".mp3").let {
        it.writeBytes(byteArrayOf(1, 2, 3)); SavedFile(media.id, it.path, bytes = it.length(), title = media.title, thumb = "")
    }
    @Test fun permanentFailureFiveAndTwelveDoNotStopItemsSixToSixteen() = runBlocking {
        val job = job(); val order = mutableListOf<Int>()
        val result = BatchRunner(job.id).run(1, {}) { index, media, progress ->
            order.add(index + 1); progress(TransferProgress("Baixando", .5f))
            if (index in listOf(4, 11)) throw IOException("Video unavailable")
            saved(media)
        }
        assertEquals((1..16).toList(), order); assertEquals(14, result.files.size)
        assertEquals(listOf(4, 11), result.records.filter { it.state == "FAILED" }.map { it.position })
        assertTrue(result.records.filter { it.state == "FAILED" }.all { !it.retryable && it.code == "UNAVAILABLE" })
        assertTrue(BatchRunner.summary(result).contains("14 baixados; 2 não baixados"))
    }
    @Test fun diskFullStopsTheBatchAndPreservesCompletedFilesAndPendingItems() = runBlocking {
        val job = job(); var calls = 0
        try {
            BatchRunner(job.id).run(1, {}) { index, media, _ -> calls++; if (index == 1) throw IOException("ENOSPC"); saved(media) }
            fail("Disk failure cannot be success")
        } catch (e: TransferException) { assertEquals("NO_SPACE", e.failure.code) }
        val result = Store.get(job.id)
        assertEquals(2, calls); assertEquals(1, result.files.size)
        assertEquals(14, result.records.count { it.state == "PENDING" })
    }
    @Test fun retryTargetsOnlyEligibleFailuresAndNeverDownloadsCompletedFilesAgain() = runBlocking {
        val job = job(4)
        BatchRunner(job.id).run(1, {}) { index, media, _ ->
            if (index == 1) throw IOException("postprocessing: FFmpeg failed")
            if (index == 2) throw IOException("Video unavailable")
            saved(media)
        }
        Store.edit(job.id) { QueueActions.apply(it.copy(state = "WARNING"), "retry").copy(state = "RUNNING") }
        val calls = mutableListOf<Int>()
        val result = BatchRunner(job.id).run(2, {}) { index, media, _ -> calls.add(index); saved(media) }
        assertEquals(listOf(1), calls); assertEquals(3, result.files.size)
        assertEquals(1, result.records.count { it.state == "FAILED" })
    }
    @Test fun concurrencyIsBoundedAndGlobalProgressNeverMovesBackOnStageChanges() = runBlocking {
        val job = job(6); val active = AtomicInteger(); val peak = AtomicInteger(); val progress = java.util.Collections.synchronizedList(mutableListOf<Float>())
        BatchRunner(job.id).run(2, {}) { _, media, emit ->
            peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            emit(TransferProgress("Baixando", .70f)); progress.add(Store.get(job.id).progress)
            emit(TransferProgress("Convertendo")); progress.add(Store.get(job.id).progress)
            delay(10); active.decrementAndGet(); saved(media)
        }
        assertEquals(2, peak.get())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(Store.get(job.id).records.all { it.state == "DONE" })
    }
    @Test fun pauseOrCancellationWinsOverAProcessFailure() = runBlocking {
        val job = job(3)
        try {
            BatchRunner(job.id).run(1, { if (Store.get(job.id).state != "RUNNING") throw BatchInterrupted() }) { _, _, _ ->
                Store.edit(job.id) { it.copy(state = "PAUSED") }; throw IOException("process killed")
            }; fail()
        } catch (_: BatchInterrupted) { }
        assertEquals("PAUSED", Store.get(job.id).state); assertTrue(Store.get(job.id).files.isEmpty())
    }
    @Test fun oldQueueJsonMigrationKeepsIdsUrisAndPartialPaths() {
        val json = """{"id":"old-id","title":"Old","items":[{"id":"a","url":"https://youtu.be/a","title":"A"}],"choice":{"mp3":true},"folder":"content://chosen","state":"PAUSED","files":[{"mediaId":"a","path":"/old/partial.mp3","uri":"content://saved","bytes":42,"title":"A","thumb":"","height":0}],"created":123,"progress":0.4,"current":0,"speed":0,"eta":0,"phase":"Pausado","error":"","archiveUri":""}"""
        val job = Store.decode(json)
        assertEquals("old-id", job.id); assertEquals("content://chosen", job.folder)
        assertEquals("content://saved", job.files.first().uri); assertEquals("/old/partial.mp3", job.files.first().path)
        assertEquals(1, job.foundTotal); assertTrue(job.records.isEmpty()); assertEquals(123L, job.created)
        assertEquals("DONE", BatchRunner.records(job).single().state)
    }
}
