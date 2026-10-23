package com.fuhbe.ytdlpg.simple

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.UUID

/** Compiled for a real ARM64 phone and the optimized release APK. Never counted
 * as executed by host CI. Fixtures are generated media, not live YouTube. */
@RunWith(AndroidJUnit4::class)
class NativePipelineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var server: FixtureServer
    @Before fun prepare() {
        Assume.assumeTrue("Finish/pause other downloads before device tests", Store.list().none { it.state in setOf("RUNNING", "QUEUED") })
        server = FixtureServer(instrumentation.context)
    }
    @After fun stop() { if (::server.isInitialized) server.close() }
    private fun media(name: String) = Media(name, server.base + "/$name.html", "Native test $name", duration = 3, heights = listOf(90))
    private suspend fun pipeline(mp3: Boolean, name: String): SavedFile {
        Engine.ready(context)
        val item = media(name)
        val job = QueueJob(title = item.title, items = listOf(item), choice = Choice(mp3 = mp3, height = 90), folder = "")
        val entry = File(context.cacheDir, "native-test-${job.id}").apply { mkdirs() }
        val request = DownloadRequest.make(job, item, entry, 4, File(context.filesDir, "yt_dlp_plugins"))
        Engine.execute(request, job.id)
        val result = JSONObject(File(entry, "finished.jsonl").readLines().last { it.isNotBlank() })
        val file = File(result.getString("filepath"))
        MediaValidator.validate(context, file, item, mp3, job.id + ":validate", job.id)
        // Decode the complete media on the actual bundled Android FFmpeg.
        Engine.native(listOf(File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").path,
            "-v", "error", "-xerror", "-i", file.path, "-f", "null", "-"), job.id + ":decode")
        return SavedFile(item.id, file.path, bytes = file.length(), title = item.title, thumb = "")
    }
    @Test fun realBundledRuntimeProducesMp3AndCompatibleAndConvertedMp4() = runBlocking {
        assertTrue(pipeline(true, "copy").path.endsWith(".mp3"))
        assertTrue(pipeline(false, "copy").path.endsWith(".mp4"))
        assertTrue(pipeline(false, "convert").path.endsWith(".mp4"))
    }
    @Test fun cancellingAnActualFfmpegChildNeverReportsACompletedFile() = runBlocking {
        Engine.ready(context)
        val item = media("convert")
        val job = QueueJob(title = "Native conversion cancel", items = listOf(item), choice = Choice(), folder = "")
        val entry = File(context.cacheDir, "native-cancel-${job.id}").apply { mkdirs() }
        val request = DownloadRequest.make(job, item, entry, 1, File(context.filesDir, "yt_dlp_plugins"))
            .addOption("--postprocessor-args", "ffmpeg_i:-re")
        val converting = CompletableDeferred<Unit>()
        val task = async(Dispatchers.IO) { runCatching { Engine.execute(request, job.id, callback = { _, _, line ->
            if (line.startsWith("YGPP:") && line.contains("CONVERT")) converting.complete(Unit)
        }) } }
        withTimeout(90_000) { converting.await() }
        delay(200); Engine.stopJob(job.id)
        assertTrue(withTimeout(30_000) { task.await() }.isFailure)
        assertFalse(File(entry, "finished.jsonl").exists())
        assertEquals(0, Engine.activeProcesses(job.id))
    }
    @Test fun actualForegroundWorkerContinuesInBackgroundAndZipsOnlyTheSuccessfulItem() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val first = media("copy")
            val job = QueueJob(title = "Native background ZIP test", items = listOf(first, media("missing")), choice = Choice(mp3 = true, zip = true), folder = "")
            Store.insert(job); QueueWorker.kick(context)
            await(90_000) { Engine.activeProcesses(job.id) > 0 }
            UiDevice.getInstance(instrumentation).pressHome()
            await(120_000) { Store.get(job.id).state in setOf("WARNING", "ERROR", "DONE") }
            val result = Store.get(job.id)
            assertEquals(result.error, "WARNING", result.state)
            assertEquals(1, result.files.size); assertEquals("FAILED", result.records[1].state)
            assertTrue(result.archiveUri.isNotBlank())
            context.contentResolver.openInputStream(android.net.Uri.parse(result.archiveUri)).use { input ->
                java.util.zip.ZipInputStream(checkNotNull(input)).use { zip -> assertNotNull(zip.nextEntry); while (zip.read(ByteArray(8192)) >= 0) { }; assertNull(zip.nextEntry) }
            }
        }
    }
    @Test fun actualWorkerPausePreservesPartialsAndResumeCompletes() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val item = media("copy")
            val job = QueueJob(title = "Native pause/resume test", items = listOf(item), choice = Choice(mp3 = true), folder = "")
            Store.insert(job); QueueWorker.kick(context)
            await(90_000) { File(context.filesDir, "jobs/${job.id}").walkTopDown().any { file -> file.name.endsWith(".part") && file.length() > 0 } }
            QueueWorker.control(context, job.id, "pause")
            await(30_000) { Engine.activeProcesses(job.id) == 0 }
            assertEquals("PAUSED", Store.get(job.id).state)
            QueueWorker.control(context, job.id, "resume")
            await(120_000) { Store.get(job.id).state in setOf("DONE", "ERROR") }
            assertEquals(Store.get(job.id).error, "DONE", Store.get(job.id).state)
            assertEquals(1, Store.get(job.id).files.size)
        }
    }
    private fun await(timeout: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) { if (SystemClock.elapsedRealtime() > deadline) throw AssertionError("Device scenario timed out; copy diagnostics and Logcat"); Thread.sleep(100) }
    }
    private class FixtureServer(testContext: Context) : AutoCloseable {
        private val socket = ServerSocket(0)
        val base = "http://127.0.0.1:${socket.localPort}"
        private val copy = Base64.decode(testContext.assets.open("copy.mp4.b64").bufferedReader().readText(), Base64.DEFAULT)
        private val convert = Base64.decode(testContext.assets.open("convert.webm.b64").bufferedReader().readText(), Base64.DEFAULT)
        @Volatile private var running = true
        init { Thread {
            while (running) {
                val client = try { socket.accept() } catch (_: Exception) { break }
                Thread {
                    client.use { connection -> runCatching {
                        val input = connection.getInputStream().bufferedReader()
                        val request = input.readLine().split(' '); val path = request[1].substringBefore('?')
                        var range = 0
                        while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break
                            if (line.startsWith("Range:", true)) range = line.substringAfter("bytes=").substringBefore('-').toIntOrNull() ?: 0
                        }
                        val html = path.endsWith(".html")
                        val body = when (path) {
                            "/copy.html", "/convert.html" -> "<html><title>Native fixture</title><video><source src=\"/${if (path.contains("convert")) "convert.webm" else "copy.mp4"}\" width=\"160\" height=\"90\"></video></html>".toByteArray()
                            "/copy.mp4" -> copy
                            "/convert.webm" -> convert
                            else -> ByteArray(0)
                        }
                        val status = if (body.isEmpty()) "404 Not Found" else if (range > 0) "206 Partial Content" else "200 OK"
                        val output = connection.getOutputStream()
                        val headers = "HTTP/1.0 $status\r\nContent-Type: ${if (html) "text/html" else if (path.endsWith("webm")) "video/webm" else "video/mp4"}\r\nContent-Length: ${body.size - range}\r\nAccept-Ranges: bytes\r\n" + if (range > 0) "Content-Range: bytes $range-${body.size - 1}/${body.size}\r\n" else ""
                        output.write((headers + "Connection: close\r\n\r\n").toByteArray())
                        if (request[0] != "HEAD") {
                            var offset = range
                            while (offset < body.size) { val count = minOf(512, body.size - offset); output.write(body, offset, count); output.flush(); offset += count
                                if (!html) Thread.sleep(40)
                            }
                        }
                    } }
                }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() } }
        override fun close() { running = false; socket.close() }
    }
}
