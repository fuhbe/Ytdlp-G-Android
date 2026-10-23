package com.fuhbe.ytdlpg.simple

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** Real host yt-dlp/FFmpeg HTTP transfers through the production request,
 * batch, progress and validation policies. Does not run Android ARM64 binaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = SimpleApp::class)
class HostPipelineTest {
    companion object {
        private val root = File(checkNotNull(System.getProperty("ytdlpg.root")))
        private lateinit var server: Process
        private lateinit var serverOutput: java.io.BufferedReader
        private lateinit var base: String
        private lateinit var fixtureRoot: File
        @JvmStatic @BeforeClass fun startFixtures() {
            server = ProcessBuilder("python3", File(root, "integration_fixture.py").path).redirectError(File(root, "build/fixture-stderr.log")).start()
            serverOutput = server.inputStream.bufferedReader()
            val metadata = JSONObject(checkNotNull(serverOutput.readLine()) { "Fixture preparation failed; read build/fixture-stderr.log" })
            base = metadata.getString("base"); fixtureRoot = File(metadata.getString("root"))
        }
        @JvmStatic @AfterClass fun stopFixtures() {
            if (::server.isInitialized) {
                server.outputStream.write("stop\n".toByteArray()); server.outputStream.flush()
                if (!server.waitFor(10, TimeUnit.SECONDS)) server.destroyForcibly()
            }
        }
    }
    private fun run(command: List<String>, line: (String) -> Unit = {}): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = StringBuilder(); var failure: Throwable? = null
        val reader = Thread {
            try { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { synchronized(output) { output.append(it).append('\n') }; line(it) } } }
            catch (e: Throwable) { failure = e; process.destroyForcibly() }
        }.apply { start() }
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            val handle = Process::class.java.getMethod("toHandle").invoke(process)
            val type = Class.forName("java.lang.ProcessHandle")
            (type.getMethod("descendants").invoke(handle) as java.util.stream.Stream<*>).use { children ->
                children.forEach { type.getMethod("destroyForcibly").invoke(it) }
            }
            process.destroyForcibly()
            throw AssertionError("Host pipeline timeout")
        }
        reader.join(5_000); assertFalse("Host output reader must drain", reader.isAlive)
        failure?.let { throw it }
        val text = output.toString()
        if (process.exitValue() != 0) File(root, "build/host-process-errors.log").appendText(text + "\n")
        if (process.exitValue() != 0) throw IOException(text.lineSequence().lastOrNull { it.contains("ERROR") } ?: text.takeLast(1000))
        return text
    }
    private suspend fun transfer(job: QueueJob, index: Int, media: Media, progress: (TransferProgress) -> Unit): SavedFile = withContext(Dispatchers.IO) {
        val entry = File(RuntimeEnvironment.getApplication().cacheDir, "${job.id}/$index").apply { mkdirs() }
        val request = DownloadRequest.make(job, media, entry, 4, File(root, "plugins"))
        val protocol = TransferProtocol(job.choice.mp3, media.duration, progress)
        run(listOf("python3", File(root, "app/build/generated/runtime/res/raw/ytdlp").path) + request.buildCommand()) { protocol.line(it) }
        val final = JSONObject(File(entry, "finished.jsonl").readLines().last { it.isNotBlank() })
        val file = File(final.getString("filepath"))
        assertTrue(file.isFile)
        assertTrue("Real FFmpeg progress file", File(entry, "ffmpeg.progress").isFile || !job.choice.mp3)
        val probe = run(listOf("ffprobe", "-v", "fatal", "-count_packets", "-show_streams", "-show_format", "-of", "json", file.path))
        MediaValidator.inspect(probe, job.choice.mp3, media.duration)
        // Decode every output in this host acceptance test, not just headers.
        run(listOf("ffmpeg", "-v", "error", "-xerror", "-i", file.path, "-f", "null", "-"))
        SavedFile(media.id, file.path, bytes = file.length(), title = media.title, thumb = "", height = final.optInt("height", 0))
    }
    private fun job(urls: List<String>, mp3: Boolean, zip: Boolean = false): QueueJob = QueueJob(title = "HTTP fixture playlist", items = urls.mapIndexed { index, url -> Media("item-$index", url, "Item ${index + 1}", duration = 3) },
        choice = Choice(mp3 = mp3, zip = zip), folder = "", state = "RUNNING").also { Store.insert(it) }
    @Test fun sixteenRealMp3TransfersContinuePastUnavailableFifthAndTwelfthAndZipFourteen() = runBlocking {
        val job = job((1..16).map { if (it in listOf(5, 12)) "$base/missing-$it.mp4" else "$base/copy.html?item=$it" }, true, true)
        val result = BatchRunner(job.id).run(2, {}) { index, media, emit -> transfer(job, index, media, emit) }
        assertEquals(result.records.toString(), 14, result.files.size); assertTrue(result.files.any { it.mediaId == "item-15" })
        assertEquals(listOf(4, 11), result.records.filter { it.state == "FAILED" }.map { it.position })
        val zip = File(RuntimeEnvironment.getApplication().cacheDir, "${job.id}.zip")
        Storage.archiveSelected(result.files.map { File(it.path) }, zip, 16, {}, {})
        ZipFile(zip).use { assertEquals(14, it.size()); assertTrue(it.entries().asSequence().all { entry -> !entry.name.endsWith(".part") && entry.name.endsWith(".mp3") }) }
        assertTrue(BatchRunner.summary(result).contains("16 itens encontrados; 14 baixados; 2 não baixados"))
    }
    @Test fun combinedSplitAndIncompatibleMp4FinishWithAudioAndVideoAndOneFailureDoesNotAbort() = runBlocking {
        val job = job(listOf("$base/copy.html", "$base/missing.mp4", "$base/split.mpd", "$base/convert.html"), false, true)
        val phases = java.util.Collections.synchronizedList(mutableListOf<String>())
        val result = BatchRunner(job.id).run(1, {}) { index, media, emit -> transfer(job, index, media) { phases.add(it.phase); emit(it) } }
        assertEquals(result.records.toString(), 3, result.files.size); assertEquals("FAILED", result.records[1].state)
        assertTrue(phases.any { it.contains("Juntando") }); assertTrue(phases.any { it.contains("Convertendo") })
        val copy = result.files.first { it.mediaId == "item-0" }
        fun hash(path: String) = run(listOf("ffmpeg", "-v", "error", "-i", path, "-map", "0:v:0", "-c", "copy", "-f", "hash", "-")).trim()
        assertEquals(hash(File(fixtureRoot, "copy.mp4").path), hash(copy.path))
        assertEquals(hash(File(fixtureRoot, "copy.mp4").path), hash(result.files.first { it.mediaId == "item-2" }.path))
    }
    @Test fun missingFragmentNeverProducesACompletedFileAndOneSuccessCanStillFormSelectedZip() = runBlocking {
        val job = job(listOf("$base/broken-master.m3u8", "$base/copy.html"), false, true)
        val result = BatchRunner(job.id).run(1, {}) { index, media, emit -> transfer(job, index, media, emit) }
        assertEquals(result.records.toString(), 1, result.files.size); assertEquals("FAILED", result.records.first().state)
        assertFalse(File(RuntimeEnvironment.getApplication().cacheDir, "${job.id}/0/finished.jsonl").exists())
        val zip = File(RuntimeEnvironment.getApplication().cacheDir, "partial-success.zip")
        Storage.archiveSelected(result.files.map { File(it.path) }, zip, 2, {}, {})
        ZipFile(zip).use { assertEquals(1, it.size()) }
    }
    @Test fun realHttpDisconnectResumesWithRangeAndTransient503RetriesWithoutDuplicateCompletion() = runBlocking {
        val job = job(listOf("$base/resume.html", "$base/transient.html"), false)
        val result = BatchRunner(job.id).run(1, {}) { index, media, emit -> transfer(job, index, media, emit) }
        assertEquals(result.records.toString(), 2, result.files.size); assertTrue(result.records.all { it.state == "DONE" })
        server.outputStream.write("stats\n".toByteArray()); server.outputStream.flush()
        val stats = JSONObject(serverOutput.readLine()); assertTrue(stats.getInt("ranges") > 0)
        val calls = mutableListOf<Int>()
        BatchRunner(job.id).run(1, {}) { index, media, emit -> calls.add(index); transfer(job, index, media, emit) }
        assertTrue(calls.isEmpty()); assertEquals(2, Store.get(job.id).files.size)
    }
    @Test fun incompatibleMp4KeepsItsOriginalUntilNativeValidationAndCommit() = runBlocking {
        val job = job(listOf("$base/convertmp4.html"), false)
        val result = BatchRunner(job.id).run(1, {}) { index, media, emit -> transfer(job, index, media, emit) }
        assertEquals(result.records.toString(), 1, result.files.size)
        val file = File(result.files.single().path)
        assertTrue(file.name.endsWith(".converted.mp4"))
        assertTrue(file.parentFile!!.listFiles()!!.any { it.extension == "mp4" && it != file && !it.name.endsWith(".glass.mp4") })
    }
    @Test fun corruptMediaIsNotAValidMp4() = runBlocking {
        val job = job(listOf("$base/corrupt.mp4"), false)
        val result = BatchRunner(job.id).run(1, {}) { index, media, emit -> transfer(job, index, media, emit) }
        assertTrue(result.files.isEmpty()); assertEquals("FAILED", result.records.single().state)
    }
}
