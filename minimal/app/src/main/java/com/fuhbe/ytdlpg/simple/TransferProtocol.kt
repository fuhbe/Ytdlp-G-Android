package com.fuhbe.ytdlpg.simple

import org.json.JSONObject
import java.io.File

/** Compact yt-dlp progress and real FFmpeg -progress observations. */
class TransferProtocol(private val mp3: Boolean, duration: Long, private val emit: (TransferProgress) -> Unit) {
    @Volatile var phase = "ANALYZE"; private set
    private var duration = duration.toDouble()
    private var emittedDuration = 0L
    private var phaseStarted = 0L
    @Volatile var bytes = 0L; private set
    @Volatile var processedMicros = 0L; private set
    @Synchronized fun line(line: String) {
        when {
            line.startsWith("YG:") -> {
                val p = JSONObject(line.removePrefix("YG:"))
                phase = "DOWNLOAD"
                bytes = p.optLong("downloaded_bytes", 0).coerceAtLeast(0)
                val total = p.optLong("total_bytes", 0).takeIf { it > 0 } ?: p.optLong("total_bytes_estimate", 0)
                val fraction = if (total > 0) .05f + .65f * (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
                emit(TransferProgress(if (mp3) "Baixando áudio…" else "Baixando áudio/vídeo…", fraction, bytes, total,
                    p.optDouble("speed", 0.0).takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0, p.optLong("eta", 0).coerceAtLeast(0)))
            }
            line.startsWith("YGPP:") -> {
                val p = JSONObject(line.removePrefix("YGPP:")); duration = p.optDouble("duration", duration)
                phase = p.optString("phase"); phaseStarted = System.currentTimeMillis(); processedMicros = 0; emittedDuration = 0
                emit(TransferProgress(when (phase) { "REMUX" -> "Remuxando MP4 sem recodificar…"; "VALIDATE" -> "Verificando arquivo…"; else -> "Convertendo codecs para MP4…" }))
            }
            line.contains("[Merger]") -> stage("MERGE", "Juntando áudio e vídeo sem recodificar…")
            line.contains("[ExtractAudio]") -> stage("CONVERT", "Convertendo para MP3…")
        }
    }
    private fun stage(value: String, text: String) { phase = value; phaseStarted = System.currentTimeMillis(); processedMicros = 0; emittedDuration = 0; emit(TransferProgress(text)) }
    @Synchronized fun ffmpeg(file: File) {
        if (phase !in setOf("MERGE", "REMUX", "CONVERT") || !file.isFile || file.lastModified() < phaseStarted) return
        // Read a bounded tail even for hours-long conversions. FFmpeg writes one
        // small record per second; no native stderr needs to be buffered for UI.
        val tail = java.io.RandomAccessFile(file, "r").use { input ->
            val length = minOf(input.length(), 16_384).toInt(); input.seek(input.length() - length)
            ByteArray(length).also { input.readFully(it) }.toString(Charsets.UTF_8)
        }
        val micros = tail.lineSequence().filter { it.startsWith("out_time_us=") }.lastOrNull()?.substringAfter('=')?.toLongOrNull() ?: return
        if (micros < 0 || micros == emittedDuration) return
        processedMicros = micros; emittedDuration = micros
        val fraction = if (duration > 0) (micros / 1_000_000.0 / duration).coerceIn(0.0, .99).toFloat() else null
        val base = if (phase == "MERGE") .70f else .79f
        val width = if (phase == "MERGE") .08f else .15f
        emit(TransferProgress(when (phase) { "MERGE" -> "Juntando áudio e vídeo…"; "REMUX" -> "Remuxando MP4 sem recodificar…"; else -> if (mp3) "Convertendo para MP3…" else "Convertendo codecs para MP4…" }, fraction?.let { base + width * it }))
    }
}
