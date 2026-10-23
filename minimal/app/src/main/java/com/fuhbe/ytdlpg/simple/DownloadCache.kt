package com.fuhbe.ytdlpg.simple

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Written only after the real native packet/codec validation, never from progress. */
object DownloadCache {
    fun load(entry: File, media: Media, choice: Choice): JSONObject? = runCatching {
        val marker = File(entry, "validated.json")
        if (!marker.isFile) return null
        val data = JSONObject(marker.readText())
        val file = File(data.getString("filepath"))
        if (data.optInt("schema") != 1 || data.optString("mediaId") != media.id || data.optBoolean("mp3") != choice.mp3 || data.optInt("cap") != choice.height) return null
        if (!file.canonicalPath.startsWith(entry.canonicalPath + File.separator) || !file.isFile || file.length() <= 0) return null
        if (file.length() != data.optLong("bytes") || file.lastModified() != data.optLong("modified")) return null
        if (file.extension != if (choice.mp3) "mp3" else "mp4") return null
        data
    }.getOrNull()
    fun save(entry: File, media: Media, choice: Choice, result: JSONObject) {
        val file = File(result.getString("filepath"))
        require(file.isFile && file.length() > 0 && file.canonicalPath.startsWith(entry.canonicalPath + File.separator))
        val data = JSONObject(result.toString()).put("schema", 1).put("mediaId", media.id).put("mp3", choice.mp3).put("cap", choice.height)
            .put("bytes", file.length()).put("modified", file.lastModified())
        val atomic = AtomicFile(File(entry, "validated.json")); val output = atomic.startWrite()
        try { output.write(data.toString().toByteArray()); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
    }
    fun rejectCompleted(entry: File) {
        val complete = entry.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("mp3", "mp4", "mkv", "webm", "m4a", "opus", "aac", "ogg") }.orEmpty()
        if (complete.isEmpty()) return
        val rejected = File(entry, "rejected-${java.util.UUID.randomUUID().toString().take(8)}").apply { check(mkdirs()) }
        complete.forEach { check(it.renameTo(File(rejected, it.name))) { "Não foi possível preservar o arquivo rejeitado para nova tentativa." } }
    }
    fun cleanIntermediates(file: SavedFile) {
        val final = File(file.path)
        val metadata = setOf("finished.jsonl", "ffmpeg.progress", "export.json", "validated.json")
        final.parentFile?.listFiles()?.filter { it.isFile && it.canonicalFile != final.canonicalFile && it.name !in metadata }?.forEach { it.delete() }
    }
}
