package com.fuhbe.ytdlpg

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import org.json.JSONObject
import java.io.File
import java.net.URI

/** Quality choices are derived from actual yt-dlp format lists, never guessed resolutions. */
data class Quality(val key: String, val label: String, val kind: String)
data class MediaItem(val url: String, val title: String, val formats: List<Format>)
data class Format(val id: String, val ext: String, val height: Int, val abr: Int, val hasVideo: Boolean, val hasAudio: Boolean)
data class Inspection(val title: String, val thumbnail: String, val items: List<MediaItem>, val qualities: List<Quality>, val skipped: Int)

class MediaEngine(private val context: Context) {
    fun initialize() {
        YoutubeDL.init(context)
    }

    fun initializeDownload() {
        initialize()
        FFmpeg.getInstance().init(context)
    }

    private fun json(url: String, flat: Boolean = false): JSONObject {
        val request = YoutubeDLRequest(url)
        request.addOption("--dump-single-json")
        request.addOption("--skip-download")
        if (flat) request.addOption("--flat-playlist") else request.addOption("--no-playlist")
        return JSONObject(YoutubeDL.execute(request).out.trim())
    }

    fun inspect(url: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): Inspection {
        val uri = URI(url)
        require(uri.scheme == "http" || uri.scheme == "https")
        val root = json(url, true)
        val entries = root.optJSONArray("entries")
        val links = if (entries == null) listOf(url) else (0 until entries.length()).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            val candidate = entry.optString("webpage_url").ifBlank { entry.optString("url") }
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) candidate
            else if (entry.optString("ie_key").equals("Youtube", true) && candidate.isNotBlank())
                "https://www.youtube.com/watch?v=$candidate"
            else null
        }
        val items = mutableListOf<MediaItem>()
        var skipped = 0
        var firstFailure: Exception? = null
        for ((index, link) in links.withIndex()) {
            try {
                // A single video already contains its formats in the first response.
                val info = if (entries == null && root.optJSONArray("formats") != null) root else json(link)
                val formats = info.optJSONArray("formats")
                if (formats == null) { skipped++; continue }
                val parsed = (0 until formats.length()).mapNotNull { i ->
                    val f = formats.optJSONObject(i) ?: return@mapNotNull null
                    val id = f.optString("format_id")
                    if (id.isBlank()) return@mapNotNull null
                    val vc = f.optString("vcodec", "none")
                    val ac = f.optString("acodec", "none")
                    val protocol = f.optString("protocol")
                    if (f.optString("url").isBlank() || protocol == "mhtml") return@mapNotNull null
                    Format(id, f.optString("ext"), f.optInt("height"), f.optDouble("abr", 0.0).toInt(), vc != "none", ac != "none")
                }
                if (parsed.isNotEmpty()) items += MediaItem(link, info.optString("title", link), parsed) else skipped++
            } catch (error: Exception) {
                skipped++
                if (firstFailure == null) firstFailure = error
            }
            onProgress(index + 1, links.size)
        }
        if (items.isEmpty() && firstFailure != null) throw firstFailure
        require(items.isNotEmpty()) { context.getString(R.string.nothing_available) }
        val shared = items.map { item ->
            buildSet {
                item.formats.filter { it.hasVideo && it.height > 0 }.forEach { add("v:${it.height}") }
                item.formats.filter { it.hasAudio && !it.hasVideo && it.ext.isNotBlank() }.forEach { add("a:${it.ext}") }
                if (item.formats.any { it.hasAudio }) add("mp3")
            }
        }.reduce { a, b -> a intersect b }
        val qualities = shared.map { key ->
            when {
                key == "mp3" -> Quality(key, context.getString(R.string.mp3_quality), "audio")
                key.startsWith("v:") -> Quality(key, "${key.substringAfter(':')}p", "video")
                else -> Quality(key, "${key.substringAfter(':').uppercase()} · ${context.getString(R.string.best_available)}", "audio")
            }
        }.sortedWith(compareBy<Quality> { if (it.kind == "video") 0 else 1 }
            .thenByDescending { it.key.substringAfter(':', "0").toIntOrNull() ?: 0 }
            .thenBy { it.label })
        return Inspection(root.optString("title", items.first().title), root.optString("thumbnail"), items, qualities, skipped)
    }

    fun download(item: MediaItem, quality: Quality, directory: File, processId: String, progress: (Float) -> Unit): List<File> {
        directory.mkdirs()
        val request = YoutubeDLRequest(item.url)
        request.addOption("--no-playlist")
        request.addOption("--no-overwrites")
        request.addOption("--restrict-filenames")
        request.addOption("-o", File(directory, "%(title).180B [%(id)s].%(ext)s").absolutePath)
        when {
            quality.key == "mp3" -> {
                request.addOption("-f", "bestaudio/best")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "320K")
            }
            quality.kind == "audio" -> {
                val ext = quality.key.substringAfter(':')
                request.addOption("-f", "bestaudio[ext=$ext]")
            }
            else -> {
                val height = quality.key.substringAfter(':').toInt()
                // Re-resolve formats when downloading: site format IDs can change after inspection.
                request.addOption("-f", "bestvideo[height=$height]+bestaudio/best[height=$height]")
                request.addOption("--merge-output-format", "mkv")
            }
        }
        YoutubeDL.execute(request, processId) { pct, _, _ -> progress(pct) }
        return directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: emptyList()
    }

    fun cancel(processId: String) { YoutubeDL.destroyProcessById(processId) }
}
