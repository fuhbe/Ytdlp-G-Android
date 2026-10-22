#!/usr/bin/env python3
"""Real HTTP -> yt-dlp -> GlassMP4 -> ffprobe checks; no YouTube mocks."""
import functools
import http.server
import json
import pathlib
import subprocess
import sys
import tempfile
import threading

HERE = pathlib.Path(__file__).resolve().parent
PLUGINS = HERE / "overlay/app/src/main/assets/yt_dlp_plugins"


def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=120).stdout


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def verify(engine):
    with tempfile.TemporaryDirectory(prefix="glass-mp4-") as directory:
        root = pathlib.Path(directory)
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0),
            functools.partial(QuietHandler, directory=directory))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            for name, video_codec, audio_codec, suffix in (
                ("remux", "libx264", "aac", "mkv"),
                ("convert", "libvpx-vp9", "libopus", "webm"),
            ):
                source = root / f"{name}.{suffix}"
                run("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "testsrc2=s=320x180:r=15:d=1",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-c:v", video_codec,
                    "-c:a", audio_codec, "-shortest", str(source))
                url = f"http://127.0.0.1:{server.server_port}/{source.name}"
                log = run(sys.executable, str(engine), "--ignore-config", "--plugin-dirs", str(PLUGINS),
                    "--use-postprocessor", "GlassMP4:preset=fast", "-f", "b",
                    "-o", str(root / f"{name}-output.%(ext)s"), url)
                assert "[GlassMP4]" in log, "The actual postprocessor did not run"
                output = root / f"{name}-output.mp4"
                metadata = json.loads(run("ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(output)))
                streams = {stream["codec_type"]: stream for stream in metadata["streams"]}
                assert streams["video"]["codec_name"] == "h264"
                assert streams["audio"]["codec_name"] == "aac"
                assert (streams["video"]["width"], streams["video"]["height"]) == (320, 180)
                assert float(metadata["format"]["duration"]) >= 1
                if name == "remux":
                    # The video bitstream must survive the MP4 remux without re-encoding.
                    original_hash = run("ffmpeg", "-v", "error", "-i", str(source), "-map", "0:v:0", "-c", "copy", "-f", "hash", "-")
                    output_hash = run("ffmpeg", "-v", "error", "-i", str(output), "-map", "0:v:0", "-c", "copy", "-f", "hash", "-")
                    assert original_hash == output_hash, "Compatible video was unexpectedly re-encoded"
                print(f"PASS {name}: real HTTP download -> H.264/AAC MP4, dimensions and duration verified")
            # A missing audio stream must fail and leave the source available for retry.
            source = root / "silent.mkv"
            run("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=c=blue:s=160x90:d=1", "-c:v", "libx264", str(source))
            failed = subprocess.run([sys.executable, str(engine), "--ignore-config", "--plugin-dirs", str(PLUGINS),
                "--use-postprocessor", "GlassMP4", "-o", str(root / "silent-output.%(ext)s"),
                f"http://127.0.0.1:{server.server_port}/silent.mkv"], capture_output=True, text=True, timeout=60)
            assert failed.returncode != 0
            assert (root / "silent-output.mkv").exists()
            assert not (root / "silent-output.mp4").exists()
            print("PASS conversion failure preserves the source and produces no completed MP4")
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    verify(pathlib.Path(sys.argv[1]).resolve())
