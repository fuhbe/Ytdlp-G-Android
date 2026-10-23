package com.fuhbe.ytdlpg.simple

import android.content.Context
import android.os.Build
import android.util.Log
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Logs structural failure data only: never URLs, titles, arguments or messages. */
object Diagnostics {
    const val TAG = "YtdlpG"
    enum class Stage { APP, ANALYZE, RUNTIME_INIT, PROCESS, UPDATE, BATCH, VALIDATE, EXPORT, UNCAUGHT }
    private val session = UUID.randomUUID().toString().take(8)
    private var destination: File? = null
    @Synchronized fun install(context: Context) {
        destination = File(context.filesDir, "diagnostic.txt")
        event(Stage.APP, "start sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.filter { it in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }.joinToString(",")} version=${BuildConfig.VERSION_NAME}")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous != null && previous !is CrashRecorder) Thread.setDefaultUncaughtExceptionHandler(CrashRecorder(previous))
    }
    private class CrashRecorder(private val previous: Thread.UncaughtExceptionHandler) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            try { failure(Stage.UNCAUGHT, error) }
            finally { previous.uncaughtException(thread, error) }
        }
    }
    @Synchronized fun event(stage: Stage, state: String) {
        // All callers supply fixed stage/state strings or numeric/native health data.
        record("session=$session stage=${stage.name} $state")
    }
    @Synchronized fun failure(stage: Stage, error: Throwable) {
        var current: Throwable? = error
        repeat(5) { depth ->
            val cause = current ?: return
            val frames = cause.stackTrace.take(10).joinToString(";") { "${it.className}.${it.methodName}:${it.lineNumber}" }
            record("session=$session stage=${stage.name} depth=$depth type=${cause.javaClass.name} frames=$frames", true)
            current = cause.cause.takeIf { it !== cause }
        }
    }
    private fun record(line: String, failed: Boolean = false) {
        if (failed) Log.e(TAG, line) else Log.i(TAG, line)
        try {
            destination?.let { file ->
                val previous = if (file.exists()) file.readText().takeLast(20_000) else ""
                file.writeText(previous + line.take(4000) + "\n")
            }
        } catch (_: Exception) { /* Logging must never break the operation being diagnosed. */ }
    }
    fun snapshot(context: Context): String {
        val file = File(context.filesDir, "diagnostic.txt")
        val processes = try { Store.list().sumOf { Engine.activeProcesses(it.id) } } catch (_: Exception) { -1 }
        return "Ytdlp-G ${BuildConfig.VERSION_NAME} • Android ${Build.VERSION.SDK_INT} • active_native=$processes\n" +
            if (file.exists()) file.readText().takeLast(24_000) else "Nenhum diagnóstico registrado."
    }
}

class RuntimeBootstrapException(cause: LinkageError) : IllegalStateException(
    "Não foi possível inicializar uma biblioteca do mecanismo (RUNTIME_LINKAGE). " +
        "Use o APK corrigido e copie o diagnóstico em Configurações se o erro persistir.", cause)

object RuntimeSafety {
    suspend fun <T> run(stage: Diagnostics.Stage, operation: suspend () -> T): T = withContext(Dispatchers.IO) {
        Diagnostics.event(stage, "start main=${Looper.myLooper() == Looper.getMainLooper()}")
        try {
            operation().also { Diagnostics.event(stage, "complete") }
        } catch (e: CancellationException) {
            Diagnostics.event(stage, "cancelled"); throw e
        } catch (e: LinkageError) {
            Diagnostics.failure(stage, e)
            throw RuntimeBootstrapException(e)
        } catch (e: Exception) {
            Diagnostics.failure(stage, e)
            throw e
        }
    }
}
