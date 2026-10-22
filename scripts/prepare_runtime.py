"""Fetch the pinned upstream zipapp and verify it before packaging it as a resource."""
import hashlib
import pathlib
import sys
import urllib.request

VERSION = "2026.10.21"
SHA256 = "b7af77f686e95f7845ce8d09d21957718febb8b48b30555adb9dd2292ad7bf14"


def prepare(output):
    target = pathlib.Path(output) / "raw" / "ytdlp"
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == SHA256:
        return
    url = f"https://github.com/yt-dlp/yt-dlp/releases/download/{VERSION}/yt-dlp"
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise RuntimeError("The yt-dlp download does not match the pinned upstream checksum")
    temporary = target.with_suffix(".tmp")
    temporary.write_bytes(data)
    temporary.replace(target)


if __name__ == "__main__":
    prepare(sys.argv[1])
