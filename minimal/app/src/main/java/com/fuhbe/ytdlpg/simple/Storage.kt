package com.fuhbe.ytdlpg.simple

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Storage {
    fun folderName(context: Context, value: String): String = if (value.isBlank()) "Downloads/Ytdlp-G" else
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(value))?.name ?: "Pasta selecionada" }.getOrDefault("Pasta selecionada")
    fun validate(context: Context, folder: String) {
        if (folder.isNotBlank()) {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(folder))
            require(tree != null && tree.exists() && tree.canWrite()) { "A pasta está inacessível. Selecione novamente a pasta de destino." }
        } else require(Build.VERSION.SDK_INT >= 29) { "Escolha uma pasta de destino antes de baixar." }
    }
    fun export(context: Context, source: File, folder: String, mime: String, check: () -> Unit): Uri {
        validate(context, folder)
        val resolver = context.contentResolver
        val uri: Uri
        if (folder.isNotBlank()) {
            val tree = checkNotNull(DocumentFile.fromTreeUri(context, Uri.parse(folder)))
            var name = source.name
            var suffix = 1
            while (tree.findFile(name) != null) {
                check()
                require(suffix <= 1000) { "Existem muitos arquivos com o mesmo nome na pasta." }
                name = "${source.nameWithoutExtension} (${suffix++}).${source.extension}"
            }
            uri = checkNotNull(tree.createFile(mime, name)?.uri) { "Não foi possível criar o arquivo na pasta escolhida." }
        } else {
            uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ytdlp-G")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })) { "Não foi possível criar o arquivo em Downloads." }
        }
        try {
            source.inputStream().use { input -> checkNotNull(resolver.openOutputStream(uri, "w")).use { output -> copy(input, output, check) } }
            if (folder.isBlank()) resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
    fun copy(input: InputStream, output: OutputStream, check: () -> Unit = {}, progress: (Long) -> Unit = {}) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            check()
            val length = input.read(buffer)
            if (length < 0) break
            output.write(buffer, 0, length)
            total += length
            progress(total)
        }
    }
    fun archive(files: List<File>, target: File, check: () -> Unit, progress: (Float) -> Unit) {
        require(files.size >= 2) { "ZIP exige pelo menos dois arquivos." }
        val partial = File(target.path + ".part")
        try {
            var completed = 0L
            val total = files.sumOf { it.length() }.coerceAtLeast(1)
            ZipOutputStream(partial.outputStream().buffered()).use { output ->
                output.setLevel(0) // MP3/MP4 are already compressed; save CPU.
                files.forEachIndexed { index, file ->
                    check()
                    require(file.isFile && file.length() > 0) { "Um arquivo original não está disponível para compactar." }
                    output.putNextEntry(ZipEntry("%03d-%s".format(index + 1, file.name)))
                    file.inputStream().use { input -> copy(input, output, check) { bytes -> progress((completed + bytes).toFloat() / total) } }
                    completed += file.length()
                    output.closeEntry()
                }
            }
            ZipFile(partial).use { require(it.size() == files.size) { "O ZIP ficou incompleto." } }
            ZipInputStream(partial.inputStream().buffered()).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (input.nextEntry != null) {
                    while (input.read(buffer) != -1) check()
                    input.closeEntry() // Verifies entry CRC without buffering files.
                }
            }
            check()
            require(partial.renameTo(target)) { "Não foi possível finalizar o ZIP." }
        } finally { partial.delete() }
    }
    fun openUri(context: Context, file: SavedFile): Uri = if (file.uri.isNotBlank()) Uri.parse(file.uri) else
        FileProvider.getUriForFile(context, context.packageName + ".files", File(file.path))
}
