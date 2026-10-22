package com.fuhbe.ytdlpg

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object BundledRuntime {
    const val VERSION = "2026.10.21"
    const val SHA256 = "b7af77f686e95f7845ce8d09d21957718febb8b48b30555adb9dd2292ad7bf14"
    private var ready = false

    @Synchronized
    fun initialize(context: Context) {
        if (ready) return
        YoutubeDL.init(context)
        // Library initialization only installs its zipapp on a fresh install. Replace the
        // old installed copy as well, so upgrades do not retain the 2025 extractor.
        val target = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
        val actual = if (target.exists()) target.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32 * 1024)
            var count = stream.read(buffer)
            while (count >= 0) {
                digest.update(buffer, 0, count)
                count = stream.read(buffer)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } else ""
        if (actual != SHA256) {
            target.parentFile!!.mkdirs()
            val temp = File(target.parentFile, "yt-dlp.tmp")
            context.resources.openRawResource(R.raw.ytdlp).use { input ->
                temp.outputStream().use { input.copyTo(it) }
            }
            check(temp.renameTo(target)) { "Unable to install the bundled media engine" }
        }
        val quickJs = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
        check(quickJs.canExecute()) { "The bundled JavaScript runtime is missing" }
        ready = true
    }
}

class MediaEngine(private val context: Context) {
    private val inspections = Collections.synchronizedSet(mutableSetOf<String>())
    private val clock = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "media-timeout").apply { isDaemon = true }
    }

    fun initialize() = BundledRuntime.initialize(context.applicationContext)

    fun initializeDownload() {
        initialize()
        FFmpeg.getInstance().init(context)
    }

    private fun request(url: String): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("--socket-timeout", "12")
        addOption("--retries", "2")
        addOption("--fragment-retries", "2")
        addOption("--extractor-retries", "1")
        addOption("--cache-dir", File(context.cacheDir, "extractor-${BundledRuntime.VERSION}").absolutePath)
    }

    private fun json(url: String, flat: Boolean = false): JSONObject {
        val request = request(url).apply {
            addOption("--dump-single-json")
            addOption("--skip-download")
            addOption("--retries", "1")
            if (flat) { addOption("--flat-playlist"); addOption("--ignore-errors") }
            else addOption("--no-playlist")
        }
        val id = "inspect-${UUID.randomUUID()}"
        inspections += id
        val expired = AtomicBoolean(false)
        val timer = clock.schedule({
            expired.set(true)
            YoutubeDL.destroyProcessById(id)
        }, 65, TimeUnit.SECONDS)
        return try {
            val response = YoutubeDL.execute(request, id)
            if (expired.get()) error(context.getString(R.string.analysis_timeout))
            JSONObject(response.out.trim())
        } catch (error: Exception) {
            if (expired.get()) throw IllegalStateException(context.getString(R.string.analysis_timeout), error)
            throw error
        } finally {
            timer.cancel(false)
            inspections -= id
        }
    }

    private fun parse(info: JSONObject, url: String): MediaItem? {
        val formats = info.optJSONArray("formats") ?: return null
        val parsed = (0 until formats.length()).mapNotNull { index ->
            val format = formats.optJSONObject(index) ?: return@mapNotNull null
            val id = format.optString("format_id")
            val video = format.optString("vcodec", "none")
            val audio = format.optString("acodec", "none")
            if (id.isBlank() || format.optString("url").isBlank() ||
                format.optString("protocol") == "mhtml" || format.optBoolean("has_drm") ||
                (video == "none" && audio == "none")) return@mapNotNull null
            Format(
                id, format.optString("ext"), format.optInt("height"),
                format.optDouble("abr", 0.0).takeIf { it.isFinite() }?.toInt() ?: 0,
                video != "none", audio != "none", audio,
                format.optLong("filesize").takeIf { it > 0 } ?: format.optLong("filesize_approx")
            )
        }
        if (parsed.isEmpty()) return null
        return MediaItem(
            url, info.optString("title", url), parsed, info.optString("thumbnail"),
            info.optString("uploader").ifBlank { info.optString("channel") },
            info.optInt("duration")
        )
    }

    suspend fun inspect(input: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): Inspection =
        withContext(Dispatchers.IO) {
            initialize()
            val url = normalizeMediaUrl(input)
            val root = json(url, flat = true)
            val entries = root.optJSONArray("entries")
            var skipped = 0
            val items = if (entries == null) {
                listOfNotNull(parse(root, url))
            } else {
                val links = (0 until entries.length()).mapNotNull { index ->
                    val entry = entries.optJSONObject(index) ?: return@mapNotNull null
                    val candidate = entry.optString("webpage_url").ifBlank { entry.optString("url") }
                    when {
                        candidate.startsWith("https://") || candidate.startsWith("http://") -> candidate
                        entry.optString("ie_key").equals("Youtube", true) && candidate.matches(Regex("[A-Za-z0-9_-]{11}")) ->
                            "https://www.youtube.com/watch?v=$candidate"
                        else -> null
                    }
                }
                skipped += entries.length() - links.size
                val completed = AtomicInteger(0)
                val permits = Semaphore(2)
                coroutineScope {
                    links.map { link -> async {
                        permits.withPermit {
                            try {
                                parse(json(link), link)
                            } catch (error: Exception) {
                                if (error is kotlinx.coroutines.CancellationException) throw error
                                null
                            } finally { onProgress(completed.incrementAndGet(), links.size) }
                        }
                    } }.awaitAll().filterNotNull().also { skipped += links.size - it.size }
                }
            }
            require(items.isNotEmpty()) { context.getString(R.string.nothing_available) }
            val qualities = commonQualities(items)
            require(qualities.isNotEmpty()) { context.getString(R.string.nothing_available) }
            Inspection(
                root.optString("title", items.first().title),
                root.optString("thumbnail").ifBlank { items.first().thumbnail },
                items, qualities, skipped,
                root.optString("uploader").ifBlank { items.first().uploader }
            )
        }

    fun download(
        item: MediaItem, quality: Quality, directory: File, processId: String,
        progress: (Float, String) -> Unit
    ): List<File> {
        directory.mkdirs()
        fun build(checkFormats: Boolean) = request(item.url).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--no-overwrites")
            addOption("--concurrent-fragments", "3")
            addOption("-o", File(directory, "%(title).160B [%(id)s].%(ext)s").absolutePath)
            if (checkFormats) { addOption("--check-formats"); addOption("--no-cache-dir") }
            when {
                quality.key == "mp3" -> {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "320K")
                }
                quality.kind == "audio" -> addOption("-f", "bestaudio[ext=${quality.key.substringAfter(':')}]")
                else -> {
                    val height = quality.key.substringAfter(':').toInt()
                    addOption("-f", "bestvideo[height=$height]+bestaudio/best[height=$height]")
                    addOption("--merge-output-format", "mkv")
                }
            }
        }
        try {
            YoutubeDL.execute(build(false), processId) { percent, _, line -> progress(percent, line) }
        } catch (error: Exception) {
            // Refresh and check the selected streams once; do not loop on a server refusal.
            if (error.message.orEmpty().contains("403") && error !is YoutubeDL.CanceledException) {
                progress(0f, context.getString(R.string.retrying_stream))
                YoutubeDL.execute(build(true), processId) { percent, _, line -> progress(percent, line) }
            } else throw error
        }
        val extensions = setOf("mp3", "m4a", "webm", "mp4", "mkv", "opus", "ogg", "aac", "flac", "wav", "mov", "3gp")
        return directory.listFiles()?.filter { it.isFile && it.length() > 0 && it.extension.lowercase() in extensions }
            ?: emptyList()
    }

    fun cancelInspection() {
        val active = synchronized(inspections) { inspections.toList() }
        clock.execute { active.forEach { YoutubeDL.destroyProcessById(it) } }
    }
    fun cancel(processId: String) { clock.execute { YoutubeDL.destroyProcessById(processId) } }
    fun close() {
        cancelInspection()
        clock.shutdown()
    }
}
