package com.fuhbe.ytdlpg

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveWriter {
    fun create(files: List<File>, destination: File, cancelled: () -> Boolean = { false }) {
        require(files.isNotEmpty())
        ZipOutputStream(destination.outputStream().buffered()).use { output ->
            files.forEachIndexed { index, file ->
                if (cancelled()) throw InterruptedException()
                // A playlist can contain the same media more than once.
                output.putNextEntry(ZipEntry("%03d-%s".format(index + 1, file.name)))
                file.inputStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var count = input.read(buffer)
                    while (count != -1) {
                        if (cancelled()) throw InterruptedException()
                        output.write(buffer, 0, count)
                        count = input.read(buffer)
                    }
                }
                output.closeEntry()
            }
        }
        verify(destination, files.size, cancelled)
    }

    fun verify(archive: File, expected: Int, cancelled: () -> Boolean = { false }) {
        ZipFile(archive).use { require(it.size() == expected) { "Incomplete ZIP directory" } }
        var count = 0
        ZipInputStream(archive.inputStream().buffered()).use { input ->
            val buffer = ByteArray(32 * 1024)
            while (input.nextEntry != null) {
                while (input.read(buffer) != -1) {
                    if (cancelled()) throw InterruptedException()
                }
                input.closeEntry() // Validates each entry's CRC.
                count++
            }
        }
        require(count == expected) { "Incomplete ZIP data" }
    }
}
