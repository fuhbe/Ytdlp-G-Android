package com.fuhbe.ytdlpg.simple

import android.content.Context
import org.json.JSONObject
import java.io.File

object MediaValidator {
    fun inspect(json: String, mp3: Boolean, expectedDuration: Long = 0) {
        val metadata = JSONObject(json)
        val streams = metadata.optJSONArray("streams") ?: throw invalid()
        val audio = (0 until streams.length()).map { streams.getJSONObject(it) }.firstOrNull { it.optString("codec_type") == "audio" } ?: throw invalid()
        val video = (0 until streams.length()).map { streams.getJSONObject(it) }.firstOrNull { it.optString("codec_type") == "video" && it.optJSONObject("disposition")?.optInt("attached_pic", 0) != 1 }
        val duration = metadata.optJSONObject("format")?.optString("duration")?.toDoubleOrNull() ?: 0.0
        if (!duration.isFinite() || duration <= 0 || (expectedDuration > 10 && duration + 2 < expectedDuration * .95)) throw invalid()
        if (audio.optLong("nb_read_packets", 0) <= 0) throw invalid()
        if (mp3) { if (audio.optString("codec_name") != "mp3") throw invalid() }
        else {
            if (video == null || video.optString("codec_name") != "h264" || audio.optString("codec_name") != "aac" || video.optLong("nb_read_packets", 0) <= 0) throw invalid()
            if (video.optInt("height", 0) !in 1..2160) throw invalid()
        }
        (0 until streams.length()).map { streams.getJSONObject(it) }.forEach {
            val declared = it.optLong("nb_frames", 0); val actual = it.optLong("nb_read_packets", 0)
            if (declared > 20 && actual < declared * .97) throw invalid()
        }
    }
    private fun invalid() = TransferException(Failure("INTEGRITY", "O arquivo final está incompleto, corrompido ou incompatível. O original foi preservado.", true))
    suspend fun validate(context: Context, file: File, media: Media, mp3: Boolean, id: String, owner: String) {
        if (!file.isFile || file.length() == 0L || file.extension != if (mp3) "mp3" else "mp4") throw invalid()
        val probe = File(context.applicationInfo.nativeLibraryDir, "libffprobe.so").absolutePath
        try {
            val json = Engine.native(listOf(probe, "-v", "fatal", "-count_packets", "-show_streams", "-show_format", "-of", "json", file.absolutePath), id, owner)
            inspect(json, mp3, media.duration)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            Diagnostics.failure(Diagnostics.Stage.VALIDATE, e)
            throw if (e is TransferException) e else TransferException(invalid().failure, e)
        }
    }
}
