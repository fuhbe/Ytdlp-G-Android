package com.fuhbe.ytdlpg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object DownloadState {
    data class Task(val inspection: Inspection, val quality: Quality, val folder: Uri, val zip: Boolean)
    val status = MutableStateFlow("")
    val progress = MutableStateFlow(0f)
    val running = MutableStateFlow(false)
    val errors = MutableStateFlow<List<String>>(emptyList())
    var task: Task? = null
}

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var cancelled = false
    private val engine by lazy { MediaEngine(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("downloads", getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "cancel") {
            cancelled = true
            engine.cancel("active-download")
            job?.cancel()
            DownloadState.running.value = false
            DownloadState.status.value = getString(R.string.cancel)
            stopSelf()
            return START_NOT_STICKY
        }
        if (DownloadState.running.value) return START_NOT_STICKY
        val task = DownloadState.task ?: return START_NOT_STICKY
        cancelled = false
        DownloadState.running.value = true
        startForeground(1, notification(getString(R.string.download_in_progress)))
        job = scope.launch {
            try { runTask(task) }
            catch (e: Exception) {
                if (!cancelled) {
                    Log.e("YtdlpG", "Download task failed", e)
                    DownloadState.status.value = e.message ?: e.javaClass.simpleName
                }
            }
            finally {
                DownloadState.running.value = false
                DownloadState.task = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(message: String): Notification {
        val cancel = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction("cancel"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setOngoing(true)
            .addAction(0, getString(R.string.cancel), cancel)
            .build()
    }

    private fun runTask(task: DownloadState.Task) {
        engine.initializeDownload()
        val destination = DocumentFile.fromTreeUri(this, task.folder) ?: error(getString(R.string.folder_required))
        require(destination.canWrite()) { getString(R.string.folder_required) }
        val tempRoot = File(cacheDir, "download-${System.currentTimeMillis()}").apply { mkdirs() }
        var done = 0
        var failed = 0
        val files = mutableListOf<File>()
        val archiveNames = mutableMapOf<File, String>()
        try {
            task.inspection.items.forEachIndexed { index, item ->
                if (cancelled) return@forEachIndexed
                val text = getString(R.string.working, index + 1, task.inspection.items.size, item.title)
                DownloadState.status.value = text
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification(text))
                val itemDir = File(tempRoot, "item-$index")
                try {
                    val outputs = engine.download(item, task.quality, itemDir, "active-download") { pct ->
                        DownloadState.progress.value = (index + pct.coerceIn(0f, 100f) / 100f) / task.inspection.items.size
                    }
                    require(outputs.isNotEmpty()) { "No output file was produced" }
                    if (task.zip) {
                        files += outputs
                        outputs.forEach { archiveNames[it] = "%03d-%s".format(index + 1, it.name) }
                    }
                    else {
                        outputs.forEach { copyToTree(it, destination) }
                        itemDir.deleteRecursively()
                    }
                    done++
                } catch (e: Exception) {
                    if (!cancelled) {
                        failed++
                        Log.e("YtdlpG", "Item ${index + 1} failed: ${item.title}", e)
                        val lines = e.message.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                        val detail = (lines.lastOrNull { it.contains("ERROR:", ignoreCase = true) }
                            ?: lines.lastOrNull() ?: e.javaClass.simpleName).take(280)
                        DownloadState.errors.value += "${index + 1}. ${item.title}: $detail"
                    }
                    itemDir.deleteRecursively()
                }
            }
            if (!cancelled && task.zip && files.isNotEmpty()) {
                val zip = File(tempRoot, "playlist.zip")
                ZipOutputStream(zip.outputStream().buffered()).use { out ->
                    files.forEach { file ->
                        out.putNextEntry(ZipEntry(archiveNames.getValue(file)))
                        FileInputStream(file).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
                ZipFile(zip).use { archive -> require(archive.size() == files.size) }
                copyToTree(zip, destination)
            }
            if (!cancelled) {
                DownloadState.progress.value = 1f
                DownloadState.status.value = getString(R.string.finished, done, failed + task.inspection.skipped)
            }
        } finally { tempRoot.deleteRecursively() }
    }

    private fun copyToTree(file: File, tree: DocumentFile) {
        val mime = when (file.extension.lowercase()) {
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }
        val target = tree.createFile(mime, file.name) ?: error("Unable to create ${file.name}")
        try {
            contentResolver.openOutputStream(target.uri, "w")!!.use { output ->
                file.inputStream().use { it.copyTo(output) }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
