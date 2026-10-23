/* Derived from YTDLnis StreamProcessExtractor (Deniscerri and contributors).
 * GPL-3.0. Ytdlp-G changes: buffered UTF-8, bounded lines/logs and EOF delivery.
 */
package com.deniscerri.ytdl.core.stream

import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException

internal class StreamProcessExtractor(private val buffer: StringBuffer, private val stream: InputStream,
                                      private val callback: ((Float, Long, String) -> Unit)?) : Thread() {
    init { start() }
    override fun run() {
        try {
            InputStreamReader(stream, Charsets.UTF_8).buffered(8192).use { input ->
                val chars = CharArray(4096)
                val line = StringBuilder()
                var overflow = false
                fun deliver() {
                    if (overflow) callback?.invoke(-1f, -1, "YG_IO_ERROR:line_limit")
                    else if (line.isNotEmpty()) {
                        val value = line.toString()
                        callback?.invoke(-1f, -1, value)
                        if (buffer.length < 128_000) buffer.append(value.take(8000)).append('\n')
                    }
                    line.setLength(0); overflow = false
                }
                while (true) {
                    val count = input.read(chars)
                    if (count < 0) break
                    for (index in 0 until count) {
                        val char = chars[index]
                        if (char == '\n' || char == '\r') deliver()
                        else if (line.length < 64_000) line.append(char) else overflow = true
                    }
                }
                deliver()
            }
        } catch (_: IOException) {
            callback?.invoke(-1f, -1, "YG_IO_ERROR:read")
        }
    }
}
