"""Exercise the pinned engine with a local HTTP media fixture and real FFmpeg."""
import functools
import http.server
import json
import pathlib
import subprocess
import sys
import tempfile
import threading


def run(*arguments):
    result = subprocess.run(arguments, check=True, capture_output=True, text=True, timeout=60)
    return result.stdout


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def smoke(engine):
    with tempfile.TemporaryDirectory(prefix="ytdlpg-smoke-") as directory:
        root = pathlib.Path(directory)
        source = root / "fixture.mp4"
        run("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=c=blue:s=320x180:d=2",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-c:v", "mpeg4",
            "-c:a", "aac", "-shortest", str(source))
        server = http.server.ThreadingHTTPServer(
            ("127.0.0.1", 0), functools.partial(QuietHandler, directory=directory)
        )
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            url = f"http://127.0.0.1:{server.server_port}/fixture.mp4"
            info = json.loads(run(sys.executable, str(engine), "--dump-single-json", "--skip-download", url))
            assert info["formats"], "The real extractor returned no formats"
            target = root / "audio.%(ext)s"
            run(sys.executable, str(engine), "--no-playlist", "-f", "bestaudio/best",
                "-x", "--audio-format", "mp3", "--audio-quality", "320K", "-o", str(target), url)
            probe = json.loads(run("ffprobe", "-v", "error", "-show_streams", "-of", "json", str(root / "audio.mp3")))
            assert probe["streams"][0]["codec_name"] == "mp3"
            assert float(probe["streams"][0]["duration"]) > 1
            print("HTTP extraction, real download and MP3 conversion passed with the pinned engine")
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    smoke(pathlib.Path(sys.argv[1]).resolve())
