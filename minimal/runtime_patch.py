"""Small reviewed changes to the pinned native bridge; originals stay in source ZIP."""
from pathlib import Path

def patch_runtime(data):
    marker = b'    fun executeImpl(\n'
    assert data.count(marker) == 1
    addition = b'''    // Ytdlp-G: observe actual native CPU activity without logging commands/paths.
    // /proc may be restricted; progress files and output byte growth remain fallbacks.
    fun processCpuTicks(processId: String): Long {
        val process = idProcessMap[processId] ?: return -1
        val root = process.pidOrNull() ?: return -1
        val ticks = (listOf(root) + descendantPids(root)).mapNotNull { pid ->
            runCatching {
                val fields = File("/proc/$pid/stat").readText().substringAfterLast(')').trim().split(' ')
                fields[11].toLong() + fields[12].toLong()
            }.getOrNull()
        }
        return if (ticks.isEmpty()) -1 else ticks.sum()
    }

'''
    return data.replace(marker, addition + marker)

def stream_source():
    return (Path(__file__).resolve().parent/'runtime/StreamProcessExtractor.kt').read_bytes()
