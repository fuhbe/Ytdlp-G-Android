package com.fuhbe.ytdlpg

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

data class Quality(
    val key: String,
    val label: String,
    val kind: String,
    val bytes: Long = 0,
    val original: Boolean = true
)

data class Format(
    val id: String,
    val ext: String,
    val height: Int,
    val abr: Int,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val audioCodec: String = "",
    val bytes: Long = 0
)

data class MediaItem(
    val url: String,
    val title: String,
    val formats: List<Format>,
    val thumbnail: String = "",
    val uploader: String = "",
    val duration: Int = 0
)

data class Inspection(
    val title: String,
    val thumbnail: String,
    val items: List<MediaItem>,
    val qualities: List<Quality>,
    val skipped: Int,
    val uploader: String = ""
)

/** Strip sharing/tracking fields while preserving the user's playlist selection. */
fun normalizeMediaUrl(input: String): String {
    val uri = URI(input.trim())
    require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank())
    val host = uri.host.lowercase(Locale.ROOT).removePrefix("www.").removePrefix("m.")
    if (host !in setOf("youtube.com", "youtu.be", "music.youtube.com")) return uri.toASCIIString()
    val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
        val pair = it.split('=', limit = 2)
        if (pair.size != 2) null else pair[0] to URLDecoder.decode(pair[1], "UTF-8")
    }.toMap()
    val list = query["list"]
    if (!list.isNullOrBlank() && list.matches(Regex("[A-Za-z0-9_-]+"))) {
        return "https://www.youtube.com/playlist?list=$list"
    }
    val id = if (host == "youtu.be") uri.path.trim('/').substringBefore('/')
    else query["v"] ?: uri.path.removePrefix("/shorts/").removePrefix("/live/").trim('/')
    require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
    return "https://www.youtube.com/watch?v=$id"
}

fun commonQualities(items: List<MediaItem>): List<Quality> {
    if (items.isEmpty()) return emptyList()
    fun keys(item: MediaItem): Set<String> = buildSet {
        item.formats.filter { it.hasVideo && it.height > 0 &&
            (it.hasAudio || item.formats.any { audio -> audio.hasAudio && !audio.hasVideo }) }
            .forEach { add("v:${it.height}") }
        item.formats.filter { it.hasAudio && !it.hasVideo && it.ext.isNotBlank() }
            .forEach { add("a:${it.ext}") }
        if (item.formats.any { it.hasAudio }) add("mp3")
    }
    val shared = items.map(::keys).reduce { left, right -> left intersect right }
    return shared.map { key ->
        when {
            key == "mp3" -> Quality(key, "MP3", "audio",
                items.sumOf { it.duration.toLong() * 40_000 }, original = false)
            key.startsWith("v:") -> {
                val height = key.substringAfter(':').toInt()
                val sizes = items.map { item ->
                    val video = item.formats.filter { it.hasVideo && it.height == height }.maxByOrNull { it.bytes }
                    val audioBytes = if (video?.hasAudio == true) 0
                    else item.formats.filter { it.hasAudio && !it.hasVideo }.maxOfOrNull { it.bytes } ?: 0
                    if (video == null || video.bytes <= 0 || (!video.hasAudio && audioBytes <= 0)) 0L
                    else video.bytes + audioBytes
                }
                val bytes = if (sizes.all { it > 0 }) sizes.sum() else 0L
                Quality(key, "${height}p", "video", bytes)
            }
            else -> {
                val ext = key.substringAfter(':')
                val best = items.map { item ->
                    item.formats.filter { it.hasAudio && !it.hasVideo && it.ext == ext }.maxBy { it.abr }
                }
                val name = if (best.all { it.audioCodec.startsWith("opus") }) "OPUS" else ext.uppercase(Locale.ROOT)
                val rate = if (items.size == 1 && best.first().abr > 0) " · ${best.first().abr} kbps" else ""
                Quality(key, name + rate, "audio", if (best.all { it.bytes > 0 }) best.sumOf { it.bytes } else 0L)
            }
        }
    }.sortedWith(compareBy<Quality> { if (it.kind == "video") 0 else 1 }
        .thenBy { when {
            it.kind == "video" -> -it.key.substringAfter(':').toInt()
            it.label.startsWith("OPUS") -> 0
            it.label.startsWith("M4A") -> 1
            it.key == "mp3" -> 2
            else -> 3
        } })
}

fun preferredQuality(qualities: List<Quality>, kind: String): Quality? {
    val options = qualities.filter { it.kind == kind }
    return if (kind == "video") options.firstOrNull { it.key == "v:720" }
        ?: options.firstOrNull { it.key == "v:1080" } ?: options.lastOrNull()
    else options.firstOrNull { it.key == "mp3" } ?: options.firstOrNull()
}
