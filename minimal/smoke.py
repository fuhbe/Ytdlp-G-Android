#!/usr/bin/env python3
"""Real HTTP/HLS downloads and exact CLI/output protocol used by the Android worker.

Uses generated media, not a YouTube account or a simulated downloader. This
does not stand in for Android device/network acceptance testing.
"""
import functools
import http.server
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import threading

ROOT = Path(__file__).resolve().parent.parent
ENGINE = ROOT / "minimal/app/build/generated/runtime/res/raw/ytdlp"
PLUGINS = ROOT / "minimal/plugins"

def run(*args):
    result = subprocess.run(list(map(str, args)), capture_output=True, text=True, timeout=150)
    if result.returncode:
        raise RuntimeError(result.stderr[-2000:] + result.stdout[-2000:])
    return result.stdout

class Handler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass

def verify():
    results = []
    with tempfile.TemporaryDirectory(prefix="ytdlpg-minimal-") as directory:
        root = Path(directory)
        for name, resolution in [("low", "320x180"), ("high", "640x360")]:
            run("ffmpeg", "-v", "error", "-f", "lavfi", "-i", f"testsrc2=s={resolution}:r=15:d=2",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-c:v", "libx264", "-c:a", "aac", "-g", "15",
                "-hls_time", "1", "-hls_list_size", "0", "-hls_segment_filename", root / f"{name}-%03d.ts", root / f"{name}.m3u8")
        (root / "master.m3u8").write_text("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=180000,RESOLUTION=320x180\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360\nhigh.m3u8\n")
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Handler, directory=directory))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        url = f"http://127.0.0.1:{server.server_port}/master.m3u8"
        try:
            metadata = root / "metadata.jsonl"
            run(sys.executable, ENGINE, "--ignore-config", "--flat-playlist", "--skip-download", "--print-to-file", "%()j", metadata, url)
            item = json.loads(metadata.read_text().splitlines()[0])
            heights = {f.get("height") for f in item["formats"] if f.get("height")}
            assert heights == {180, 360}, heights
            results.append("Metadata reports real HLS resolutions: 180p and 360p")
            for selected in [180, 2160]:
                output = root / f"finished-{selected}.jsonl"
                run(sys.executable, ENGINE, "--ignore-config", "--no-playlist", "--no-simulate", "--continue",
                    "--plugin-dirs", PLUGINS, "--use-postprocessor", "GlassMP4:preset=fast",
                    "-f", f"bv*[height<={selected}]+ba/b[height<={selected}]", "-S", f"res:{selected},vcodec:h264,acodec:aac",
                    "--merge-output-format", "mkv", "--print-to-file", "after_move:%(.{filepath,height,ext})j", output,
                    "-o", root / f"video-{selected}.%(ext)s", url)
                info = json.loads(output.read_text().splitlines()[-1])
                path = Path(info["filepath"])
                assert path.is_file() and path.suffix == ".mp4"
                probe = json.loads(run("ffprobe", "-v", "error", "-show_streams", "-of", "json", path))
                streams = {s["codec_type"]: s for s in probe["streams"]}
                expected = 180 if selected == 180 else 360
                assert streams["video"]["height"] == expected
                assert streams["video"]["codec_name"] == "h264"
                assert streams["audio"]["codec_name"] == "aac"
                assert info["height"] == expected
                results.append(f"Real MP4 download: requested <= {selected}p, output {expected}p H.264/AAC")
            output = root / "finished-audio.jsonl"
            log = run(sys.executable, ENGINE, "--ignore-config", "--no-simulate", "-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "0",
                "--progress", "--newline", "--progress-template", "download:YG:%(progress)j",
                "--print-to-file", "after_move:%(.{filepath,height,ext})j", output, "-o", root / "audio.%(ext)s", url)
            info = json.loads(output.read_text().splitlines()[-1])
            assert Path(info["filepath"]).suffix == ".mp3"
            streams = json.loads(run("ffprobe", "-v", "error", "-show_streams", "-of", "json", info["filepath"]))["streams"]
            assert len(streams) == 1 and streams[0]["codec_name"] == "mp3"
            progress = [json.loads(line.removeprefix("YG:")) for line in log.splitlines() if line.startswith("YG:")]
            assert any(p.get("downloaded_bytes", 0) > 0 for p in progress)
            results.append("Real best-source MP3 conversion and byte-based progress protocol verified")
        finally:
            server.shutdown()
            server.server_close()
    report = {"type": "real host HTTP/HLS downloads with generated media", "youtube_device_test": False, "checks": results}
    target = ROOT / "minimal/build/host-smoke.json"
    target.parent.mkdir(exist_ok=True)
    target.write_text(json.dumps(report, indent=2))
    for line in results:
        print("PASS", line)

if __name__ == "__main__":
    verify()
