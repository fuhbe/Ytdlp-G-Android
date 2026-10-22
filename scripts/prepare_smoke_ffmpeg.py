"""Install pinned host-only test tools without changing system packages."""
import hashlib
import io
import pathlib
import shutil
import sys
import tarfile
import urllib.request

URL = ("https://github.com/yt-dlp/FFmpeg-Builds/releases/download/"
       "autobuild-2026-10-21-18-38/ffmpeg-N-127911-gadfe258a6a-linux64-gpl.tar.xz")
SHA256 = "bf3d9ff8c9ff7300b9c31d44becb6a68c12e62af661cc75e3b12fa01f0a5d0d2"


def prepare(directory, force=False):
    if not force and shutil.which("ffmpeg") and shutil.which("ffprobe"):
        print("Using installed host FFmpeg and FFprobe")
        return
    output = pathlib.Path(directory)
    output.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(URL, timeout=60) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise RuntimeError("The host FFmpeg archive failed checksum verification")
    found = set()
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:xz") as archive:
        for member in archive:
            name = pathlib.PurePosixPath(member.name).name
            if member.isfile() and name in {"ffmpeg", "ffprobe"}:
                with archive.extractfile(member) as source:
                    target = output / name
                    target.write_bytes(source.read())
                    target.chmod(0o755)
                found.add(name)
    assert found == {"ffmpeg", "ffprobe"}, "The archive is missing a required test tool"
    print("Pinned host FFmpeg and FFprobe installed and checksum-verified")


if __name__ == "__main__":
    prepare(sys.argv[1], force="--force" in sys.argv[2:])
