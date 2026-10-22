package com.fuhbe.ytdlpg

import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveWriterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun repeatedPlaylistItemsKeepBothFilesWithoutNameCollisions() {
        val first = java.io.File(temporary.newFolder("first"), "same.mp3").apply { writeText("first data") }
        val second = java.io.File(temporary.newFolder("second"), "same.mp3").apply { writeText("second data") }
        val archive = temporary.newFile("playlist.zip")
        ArchiveWriter.create(listOf(first, second), archive)
        ZipFile(archive).use {
            assertEquals(2, it.size())
            assertEquals("first data", it.getInputStream(it.getEntry("001-same.mp3")).reader().readText())
            assertEquals("second data", it.getInputStream(it.getEntry("002-same.mp3")).reader().readText())
        }
    }

    @Test(expected = java.util.zip.ZipException::class)
    fun truncatedArchivesCannotBeReportedAsVerified() {
        val file = temporary.newFile("track.mp3").apply { writeText("media fixture") }
        val archive = temporary.newFile("playlist.zip")
        ArchiveWriter.create(listOf(file), archive)
        archive.writeBytes(archive.readBytes().take(24).toByteArray())
        ArchiveWriter.verify(archive, 1)
    }

    @Test(expected = InterruptedException::class)
    fun cancellationStopsArchiveCreation() {
        val file = temporary.newFile("track.mp3").apply { writeText("media fixture") }
        ArchiveWriter.create(listOf(file), temporary.newFile("playlist.zip")) { true }
    }
}
