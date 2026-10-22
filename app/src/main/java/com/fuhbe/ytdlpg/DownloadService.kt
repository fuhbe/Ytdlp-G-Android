package com.fuhbe.ytdlpg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ItemResult(
    val item: MediaItem,
    val bytes: Long = 0,
    val uri: Uri? = null,
    val error: String = ""
)

object DownloadState {
    data class Task(val inspection: Inspection, val quality: Quality, val folder: Uri?, val zip: Boolean)
    val status = MutableStateFlow("")
    val progress = MutableStateFlow(0f)
    val running = MutableStateFlow(false)
    val current = MutableStateFlow(0)
    val detail = MutableStateFlow("")
    val results = MutableStateFlow<List<ItemResult>>(emptyList())
    var task: Task? = null
    fun reset(value: Task) {
        task = value
        status.value = ""
        progress.value = 0f
        current.value = 0
        detail.value = ""
        results.value = emptyList()
        running.value = true
    }
}

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var cancelled = false
    private val engine by lazy { MediaEngine(this) }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("downloads", getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "cancel") {
            cancelled = true
            engine.cancel("active-download")
            job?.cancel()
            DownloadState.running.value = false
            DownloadState.status.value = getString(R.string.cancelled)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val task = DownloadState.task ?: run { stopSelf(); return START_NOT_STICKY }
        cancelled = false
        DownloadState.running.value = true
        startForeground(1, notification(getString(R.string.preparing)))
        job = scope.launch {
            try {
                runTask(task)
            } catch (error: Exception) {
                if (!cancelled) {
                    Log.e("YtdlpG", "Download task failed", error)
                    DownloadState.status.value = errorDetail(error)
                }
            } finally {
                DownloadState.running.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(message: String): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction("cancel"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle(getString(R.string.app_name))
            .setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.cancel), cancel).build()
    }

    private fun runTask(task: DownloadState.Task) {
        DownloadState.status.value = getString(R.string.preparing)
        engine.initializeDownload()
        val destination = task.folder?.let {
            DocumentFile.fromTreeUri(this, it)?.takeIf { folder -> folder.canWrite() }
                ?: error(getString(R.string.folder_required))
        }
        require(destination != null || Build.VERSION.SDK_INT >= 29) { getString(R.string.folder_required) }
        val root = File(cacheDir, "download-${System.currentTimeMillis()}").apply { mkdirs() }
        val pending = mutableListOf<Pair<MediaItem, List<File>>>()
        var exported = false
        try {
            for ((index, item) in task.inspection.items.withIndex()) {
                if (cancelled) break
                DownloadState.current.value = index
                DownloadState.status.value = getString(R.string.download_in_progress)
                DownloadState.detail.value = ""
                getSystemService(NotificationManager::class.java).notify(1, notification(item.title))
                val directory = File(root, "item-$index")
                try {
                    val outputs = engine.download(item, task.quality, directory, "active-download") { percent, line ->
                        DownloadState.progress.value = (index + percent.coerceIn(0f, 100f) * 0.0085f) / task.inspection.items.size
                        DownloadState.detail.value = line.replace(Regex("\u001B\\[[;\\d]*m"), "").takeLast(180)
                    }
                    check(outputs.isNotEmpty()) { getString(R.string.no_output) }
                    if (cancelled) break
                    if (task.zip) pending += item to outputs
                    else {
                        DownloadState.status.value = getString(R.string.saving)
                        val saved = outputs.map { copyOutput(it, destination, task.quality.kind) }
                        DownloadState.results.update { it + ItemResult(item, outputs.sumOf(File::length), saved.first()) }
                        directory.deleteRecursively()
                    }
                } catch (error: Exception) {
                    if (cancelled) break
                    Log.e("YtdlpG", "Item failed: ${item.title}", error)
                    DownloadState.results.update { it + ItemResult(item, error = errorDetail(error)) }
                }
                DownloadState.progress.value = (index + 1f) / task.inspection.items.size * if (task.zip) 0.9f else 1f
            }
            if (cancelled) return
            if (task.zip && pending.isNotEmpty()) {
                DownloadState.status.value = getString(R.string.packaging)
                val archive = File(root, "Ytdlp-G-${System.currentTimeMillis()}.zip")
                ArchiveWriter.create(pending.flatMap { it.second }, archive) { cancelled }
                DownloadState.status.value = getString(R.string.saving)
                try {
                    val uri = copyOutput(archive, destination, "zip")
                    DownloadState.results.update { results -> results + pending.map { (item, files) ->
                        ItemResult(item, files.sumOf(File::length), uri)
                    } }
                } catch (error: Exception) {
                    DownloadState.results.update { results -> results + pending.map { (item, _) ->
                        ItemResult(item, error = errorDetail(error))
                    } }
                    throw error
                }
            }
            exported = true
            val done = DownloadState.results.value.count { it.uri != null }
            val failed = DownloadState.results.value.count { it.error.isNotEmpty() }
            DownloadState.progress.value = if (done > 0) 1f else 0f
            DownloadState.detail.value = ""
            DownloadState.status.value = if (done == 0) getString(R.string.download_failed)
            else getString(R.string.finished, done, failed + task.inspection.skipped)
        } finally {
            // Preserve downloaded data if the final export failed; never report it as saved.
            if (exported || cancelled) root.deleteRecursively()
        }
    }

    private fun copyOutput(file: File, destination: DocumentFile?, kind: String): Uri {
        val mime = when (file.extension.lowercase()) {
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> if (kind == "audio") "audio/webm" else "video/webm"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "opus", "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            else -> "application/octet-stream"
        }
        val uri = if (destination != null) destination.createFile(mime, file.name)?.uri
        else if (Build.VERSION.SDK_INT >= 29) contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ytdlp-G")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ) else null
        checkNotNull(uri) { getString(R.string.cannot_save) }
        try {
            val output = contentResolver.openOutputStream(uri, "w") ?: error(getString(R.string.cannot_save))
            output.use { sink -> file.inputStream().use { it.copyTo(sink) } }
            if (destination == null && Build.VERSION.SDK_INT >= 29) contentResolver.update(
                uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null
            )
            return uri
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun errorDetail(error: Exception): String {
        val lines = error.message.orEmpty().lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        return (lines.lastOrNull { it.contains("ERROR:", true) } ?: lines.lastOrNull()
            ?: error.javaClass.simpleName).take(700)
    }

    override fun onDestroy() {
        if (job?.isActive == true) engine.cancel("active-download")
        engine.close()
        scope.cancel()
        super.onDestroy()
    }
}
