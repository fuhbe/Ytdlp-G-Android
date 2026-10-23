"""Ytdlp-G MP4 export. GPL-3.0-or-later; see the repository LICENSE.

Select the source before this postprocessor. Copy compatible H.264/AAC streams;
encode other streams at CRF 18 / AAC 320k. Never replace a valid source on error.
"""
from pathlib import Path
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor
from yt_dlp.utils import PostProcessingError


class GlassMP4PP(FFmpegPostProcessor):
    def __init__(self, downloader=None, preset="fast"):
        super().__init__(downloader)
        self.preset = preset

    @staticmethod
    def options(streams, preset="fast"):
        video = next((s for s in streams if s.get("codec_type") == "video"
                      and not s.get("disposition", {}).get("attached_pic")), None)
        audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
        if video is None or audio is None:
            raise PostProcessingError("MP4 export requires both video and audio")
        copy_video = video.get("codec_name") == "h264"
        copy_audio = audio.get("codec_name") == "aac"
        args = ["-map", f"0:{video['index']}", "-map", f"0:{audio['index']}",
                "-c:v", "copy" if copy_video else "libx264",
                "-c:a", "copy" if copy_audio else "aac"]
        if not copy_video:
            args += ["-preset", preset, "-crf", "18", "-pix_fmt", "yuv420p", "-threads:v", "2"]
        if not copy_audio:
            args += ["-b:a", "320k", "-threads:a", "1"]
        return args + ["-map_metadata", "0", "-map_chapters", "0", "-movflags", "+faststart", "-f", "mp4"]

    def run(self, info):
        source = Path(info["filepath"])
        metadata = self.get_metadata_object(str(source))
        args = self.options(metadata.get("streams", []), self.preset)
        destination = source.with_suffix(".glass.mp4")
        final = source.with_suffix(".mp4")
        self.to_screen("Exporting MP4; copying compatible streams and converting others")
        try:
            self.run_ffmpeg(str(source), str(destination), args)
            if not destination.exists() or not destination.stat().st_size:
                raise PostProcessingError("FFmpeg produced an empty MP4")
            destination.replace(final)
        except Exception:
            destination.unlink(missing_ok=True)
            raise
        info.update(filepath=str(final), ext="mp4", vcodec="h264", acodec="aac")
        return ([str(source)] if source != final else []), info
