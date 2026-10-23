package com.fuhbe.ytdlpg.simple

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/** Reader callbacks never abandon a live process's stdout pipe on ordinary failure. */
internal class ProcessOutput(private val callback: ((Float, Long, String) -> Unit)?, private val stop: () -> Unit) {
    val failure = AtomicReference<Throwable?>(null)
    val lastError = AtomicReference("")
    fun accept(percent: Float, eta: Long, line: String) {
        try {
            if (line.startsWith("YG_IO_ERROR:")) throw IOException("Falha ao ler a saída do mecanismo")
            if (line.contains("ERROR", true)) lastError.set(line.takeLast(1800))
            if (failure.get() == null) callback?.invoke(percent, eta, line)
        } catch (e: Exception) { record(e) }
        catch (e: LinkageError) { record(e) }
    }
    private fun record(error: Throwable) {
        if (failure.compareAndSet(null, error)) {
            try { stop() } catch (stopError: Exception) { error.addSuppressed(stopError) }
        }
    }
}
