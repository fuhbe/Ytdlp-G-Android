package com.fuhbe.ytdlpg.simple

import android.content.Context
import android.os.Build
import java.io.File

/** Validate the installed package before executing any native child process. */
object BundledRuntime {
    private val files = listOf("libpython.so", "libpython.zip.so", "libffmpeg.so", "libffprobe.so", "libffmpeg.zip.so", "libqjs.so")
    fun verify(context: Context) {
        val directory = context.applicationInfo.nativeLibraryDir?.let(::File)
            ?: error("A pasta das bibliotecas nativas não está disponível. Reinstale o APK correto para o aparelho.")
        files.forEach { name ->
            val file = File(directory, name)
            val readable = file.isFile && file.canRead() && file.length() > 0
            Diagnostics.event(Diagnostics.Stage.RUNTIME_INIT, "native=$name readable=$readable bytes=${file.length()} executable=${file.canExecute()}")
            check(readable) { "Uma biblioteca necessária ($name) está ausente ou ilegível. Reinstale o APK correto para o aparelho." }
            if (!name.endsWith(".zip.so")) {
                val header = file.inputStream().use { input -> ByteArray(20).also { check(input.read(it) == 20) } }
                check(header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()) { "Biblioteca nativa inválida: $name." }
                val machine = (header[18].toInt() and 255) or ((header[19].toInt() and 255) shl 8)
                val abi = when (machine) { 183 -> "arm64-v8a"; 40 -> "armeabi-v7a"; 62 -> "x86_64"; 3 -> "x86"; else -> "unknown" }
                check(abi in Build.SUPPORTED_ABIS) { "O APK contém uma biblioteca incompatível com a arquitetura deste aparelho ($name)." }
                check(file.canExecute()) { "O Android não permite executar uma biblioteca necessária ($name). Reinstale o APK." }
            }
        }
    }
}
