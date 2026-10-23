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
    fun export(context: Context, source: File, folder: String, mime: String, check: () -> Unit,
               progress: (Long, Long) -> Unit = { _, _ -> }, journal: File? = null, owner: String = ""): Uri {
        validate(context, folder)
        val resolver = context.contentResolver
        fun journalWrite(uri: Uri, stage: String) {
            journal ?: return
            val atomic = android.util.AtomicFile(journal)
            val output = atomic.startWrite()
            try {
                output.write(org.json.JSONObject().put("source", source.canonicalPath).put("bytes", source.length()).put("modified", source.lastModified()).put("uri", uri.toString()).put("stage", stage).toString().toByteArray())
                atomic.finishWrite(output)
            } catch (e: Exception) { atomic.failWrite(output); throw e }
        }
        // Recover a completed export after process death; never create another copy.
        if (journal?.isFile == true) {
            val old = org.json.JSONObject(journal.readText())
            val oldUri = Uri.parse(old.getString("uri"))
            if (old.optString("source") == source.canonicalPath && old.optLong("bytes") == source.length() && old.optLong("modified") == source.lastModified()) {
                if (old.optString("stage") in setOf("COPIED", "COMMITTED")) {
                    val length = resolver.openAssetFileDescriptor(oldUri, "r")?.use { it.length }
                    if (length == source.length() || length == -1L) {
                        if (folder.isBlank()) resolver.update(oldUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                        journalWrite(oldUri, "COMMITTED")
                        return oldUri
                    }
                }
                resolver.delete(oldUri, null, null)
            } else if (old.optString("stage") != "COMMITTED") resolver.delete(oldUri, null, null)
            android.util.AtomicFile(journal).delete()
        }
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
            journalWrite(uri, "CREATED")
            val signal = android.os.CancellationSignal()
            val releaseSignal = ExportStreams.track(owner, java.io.Closeable { signal.cancel() })
            try {
                source.inputStream().use { input ->
                    val descriptor = checkNotNull(resolver.openFileDescriptor(uri, "w", signal))
                    android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                        val release = ExportStreams.track(owner, output)
                        try { copy(input, output, check) { bytes -> progress(bytes, source.length()) } }
                        finally { release() }
                    }
                }
            } finally { releaseSignal() }
            journalWrite(uri, "COPIED")
            if (folder.isBlank()) resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            journalWrite(uri, "COMMITTED")
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            journal?.let { android.util.AtomicFile(it).delete() }
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
        archiveSelected(files, target, files.size, check, progress)
    }
    fun archiveSelected(files: List<File>, target: File, selectedCount: Int, check: () -> Unit, progress: (Float) -> Unit, verifying: () -> Unit = {}) {
        require(selectedCount >= 2) { "ZIP exige uma seleção de pelo menos dois arquivos." }
        require(files.isNotEmpty()) { "Nenhum arquivo válido foi concluído para o ZIP." }
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
            verifying()
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
