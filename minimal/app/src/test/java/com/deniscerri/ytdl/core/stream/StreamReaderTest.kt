package com.deniscerri.ytdl.core.stream

import com.fuhbe.ytdlpg.simple.ProcessOutput
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class StreamReaderTest {
    @Test fun splitUtf8AndTheFinalLineWithoutNewlineAreDelivered() {
        val expected = "Olá — música 日本語"
        val input = object : ByteArrayInputStream(expected.toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = super.read(buffer, offset, minOf(1, length))
        }
        val lines = mutableListOf<String>(); val output = StringBuffer()
        val reader = StreamProcessExtractor(output, input) { _, _, line -> lines.add(line) }
        reader.join(3000); assertFalse(reader.isAlive)
        assertEquals(listOf(expected), lines); assertTrue(output.toString().contains(expected))
    }
    @Test fun callbackFailureDoesNotAbandonAPipeAndDeadlockItsWriter() {
        val input = PipedInputStream(4096); val writer = PipedOutputStream(input)
        var stops = 0
        val expected = IOException("controlled callback failure")
        val callback = ProcessOutput({ _, _, _ -> throw expected }) { stops++ }
        val permanentLog = StringBuffer()
        val reader = StreamProcessExtractor(permanentLog, input, callback::accept)
        val producer = Thread { writer.use { out -> repeat(20_000) { out.write("progress\n".toByteArray()) } } }.apply { start() }
        producer.join(5000); reader.join(5000)
        assertFalse("Producer must not remain blocked on a full pipe", producer.isAlive)
        assertFalse(reader.isAlive); assertEquals(1, stops); assertSame(expected, callback.failure.get())
        assertTrue(permanentLog.length <= 136_001)
    }
    @Test fun unboundedMalformedLinesAreRejectedWithoutUnboundedPermanentLogs() {
        val callback = mutableListOf<String>(); val output = StringBuffer()
        val reader = StreamProcessExtractor(output, ByteArrayInputStream(("x".repeat(100_000) + "\nOK").toByteArray())) { _, _, line -> callback.add(line) }
        reader.join(3000); assertFalse(reader.isAlive)
        assertEquals(listOf("YG_IO_ERROR:line_limit", "OK"), callback); assertEquals("OK\n", output.toString())
    }
    private fun writerProcess() = ProcessBuilder("python3", "-u", "-c",
        "import sys; sys.stdin.readline(); sys.stdout.write('progress\\n' * 500000); sys.stdout.flush()").start()
    @Test fun oldCallbackFailureLeavesTheActualNativeWriterBlockedOnItsPipe() {
        val process = writerProcess()
        val failed = java.util.concurrent.CountDownLatch(1)
        val reader = LegacyStreamProcessExtractor(StringBuffer(), process.inputStream) { _, _, _ -> throw IllegalStateException("controlled callback error") }
        reader.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> failed.countDown() }
        try {
            process.outputStream.write("start\n".toByteArray()); process.outputStream.flush()
            assertTrue(failed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            reader.join(1000)
            assertFalse("Old parser leaves its process blocked after reader failure", process.waitFor(150, java.util.concurrent.TimeUnit.MILLISECONDS))
        } finally { process.destroyForcibly(); process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); process.inputStream.close() }
    }
    @Test fun guardedCallbackStopsTheActualWriterAndDrainsItsPipe() {
        val process = writerProcess()
        val expected = IOException("controlled callback error")
        val output = ProcessOutput({ _, _, _ -> throw expected }) { process.destroyForcibly(); Unit }
        val reader = StreamProcessExtractor(StringBuffer(), process.inputStream, output::accept)
        try {
            process.outputStream.write("start\n".toByteArray()); process.outputStream.flush()
            assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)); reader.join(5000)
            assertFalse(reader.isAlive); assertSame(expected, output.failure.get())
        } finally { process.destroyForcibly(); process.inputStream.close() }
    }

}
