package com.fuhbe.ytdlpg.simple

import com.deniscerri.ytdl.core.models.YTDLRequest
import java.io.File

object DownloadRequest {
    const val PROGRESS = "download:YG:%(progress.{downloaded_bytes,total_bytes,total_bytes_estimate,speed,eta,status,elapsed,fragment_index,fragment_count})j"
    fun make(job: QueueJob, media: Media, entry: File, fragments: Int, plugins: File): YTDLRequest {
        val request = Engine.request(media.url).addOption("--no-playlist").addOption("--continue")
            .addOption("--no-overwrites").addOption("--keep-video").addOption("--no-simulate").addOption("--progress")
            .addOption("--progress-delta", .5).addOption("--progress-template", PROGRESS)
            .addOption("--concurrent-fragments", fragments.coerceIn(1, 4))
            .addOption("--abort-on-unavailable-fragments")
            .addOption("--retry-sleep", "http:exp=1:6").addOption("--retry-sleep", "fragment:exp=1:6")
            .addOption("-o", File(entry, "%(title).140B [%(id)s].%(ext)s").absolutePath)
            .addOption("--postprocessor-args", "ffmpeg:-progress '${File(entry, "ffmpeg.progress").absolutePath}' -stats_period 1 -nostats -nostdin" + if (job.choice.mp3) " -threads 1" else "")
            .addCommands(listOf("--print-to-file", "after_move:%(.{filepath,height,ext})j", File(entry, "finished.jsonl").absolutePath))
        if (job.choice.mp3) request.addOption("-f", "ba/b").addOption("-x").addOption("--audio-format", "mp3").addOption("--audio-quality", "0")
        else request.addOption("-f", Policy.selector(job.choice.height))
            .addOption("-S", "res:${if (job.choice.height == 0) 2160 else job.choice.height},vcodec:h264,acodec:aac")
            .addOption("--merge-output-format", "mp4/mkv")
            .addOption("--plugin-dirs", plugins.absolutePath).addOption("--use-postprocessor", "GlassMP4:preset=veryfast")
        return request
    }
}
