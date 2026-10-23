"""Ytdlp-G MP4 export, GPL-3.0-or-later. Keep originals on all failures.

Compatible MP4 needs no second write. Other containers remux H.264/AAC; only
incompatible streams transcode. FFmpeg progress is streamed to the per-item file
configured by the caller, while yt-dlp concurrently drains both process pipes.
"""
import json
from pathlib import Path
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor
from yt_dlp.utils import PostProcessingError


class GlassMP4PP(FFmpegPostProcessor):
    def __init__(self, downloader=None, preset="veryfast"):
        super().__init__(downloader)
        self.preset = preset

    @staticmethod
    def options(streams, preset="veryfast"):
        video = next((s for s in streams if s.get("codec_type") == "video"
                      and not s.get("disposition", {}).get("attached_pic")), None)
        audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
        if video is None or audio is None:
            raise PostProcessingError("MP4 export requires both video and audio")
        args = ["-map", f"0:{video['index']}", "-map", f"0:{audio['index']}",
                "-c:v", "copy" if video.get("codec_name") == "h264" else "libx264",
                "-c:a", "copy" if audio.get("codec_name") == "aac" else "aac"]
        if video.get("codec_name") != "h264":
            args += ["-preset", preset, "-crf", "18", "-pix_fmt", "yuv420p", "-threads:v", "2"]
        if audio.get("codec_name") != "aac":
            args += ["-b:a", "320k", "-threads:a", "1"]
        return args + ["-map_metadata", "0", "-map_chapters", "0", "-movflags", "+faststart", "-f", "mp4"]

    def event(self, phase, duration=0):
        self._downloader.to_screen("YGPP:" + json.dumps({"phase": phase, "duration": duration}, separators=(",", ":")))

    def run(self, info):
        source = Path(info["filepath"])
        metadata = self.get_metadata_object(str(source))
        args = self.options(metadata.get("streams", []), self.preset)
        duration = float(metadata.get("format", {}).get("duration") or info.get("duration") or 0)
        copy = args[args.index("-c:v") + 1] == "copy" and args[args.index("-c:a") + 1] == "copy"
        destination = source.with_suffix(".glass.mp4")
        final = source.with_name(source.stem + ".converted.mp4") if source.suffix.lower() == ".mp4" and not copy else source.with_suffix(".mp4")
        self.to_screen("Checking MP4 compatibility")
        if source.suffix.lower() == ".mp4" and copy:
            self.event("VALIDATE", duration)
            info.update(ext="mp4", vcodec="h264", acodec="aac")
            return [], info
        self.event("REMUX" if copy else "CONVERT", duration)
        try:
            self.run_ffmpeg(str(source), str(destination), ["-nostdin", *args])
            if not destination.exists() or not destination.stat().st_size:
                raise PostProcessingError("FFmpeg produced an empty MP4")
            # Validate the actual output before replacing the source or reporting after_move.
            output = self.get_metadata_object(str(destination))
            self.options(output.get("streams", []), self.preset)
            if float(output.get("format", {}).get("duration") or 0) <= 0:
                raise PostProcessingError("FFmpeg produced invalid MP4 duration")
            destination.replace(final)
        except Exception:
            destination.unlink(missing_ok=True)
            raise
        self.event("VALIDATE", duration)
        info.update(filepath=str(final), ext="mp4", vcodec="h264", acodec="aac")
        return ([str(source)] if source != final else []), info
