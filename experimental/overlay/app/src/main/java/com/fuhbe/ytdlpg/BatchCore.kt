package com.fuhbe.ytdlpg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import androidx.work.*
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.AudioPreferences
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.VideoPreferences
import com.deniscerri.ytdl.database.models.Format as CoreFormat
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.util.DownloadQueueUtil
import com.deniscerri.ytdl.util.WorkerEventBus
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ItemResult(val item: MediaItem, val bytes: Long = 0, val uri: Uri? = null, val error: String = "")
object DownloadState {
    data class Task(val inspection: Inspection, val quality: Quality, val folder: Uri?, val zip: Boolean)
    val status = MutableStateFlow("")
    val progress = MutableStateFlow(0f)
    val running = MutableStateFlow(false)
    val current = MutableStateFlow(0)
    val detail = MutableStateFlow("")
    val results = MutableStateFlow<List<ItemResult>>(emptyList())
    var task: Task? = null
}

// Android Uri is deliberately serialized as a string, never through Gson reflection.
data class BatchEntry(
    val media: MediaItem, val download: DownloadItem,
    var paths: List<String> = emptyList(), var outputUri: String? = null,
    var saved: Boolean = false, var error: String = ""
)
data class Batch(
    val id: String, val inspection: Inspection, val quality: Quality,
    val folder: String?, val zip: Boolean, val entries: List<BatchEntry>,
    var started: Boolean = false, var finished: Boolean = false,
    var status: String = "", var detail: String = "", var progress: Float = 0f,
    var current: Int = 0, var archiveUri: String? = null
)

object BatchStore {
    private val gson = Gson()
    private fun prefs(context: Context) = context.getSharedPreferences("glass-batches", 0)
    private fun file(context: Context, id: String) = File(context.filesDir, "glass-batches/$id.json")
    fun read(context: Context, id: String): Batch = synchronized(this) {
        gson.fromJson(file(context, id).readText(), Batch::class.java)
    }
    fun write(context: Context, batch: Batch) = synchronized(this) {
        val target = file(context, batch.id)
        target.parentFile!!.mkdirs()
        val temp = File(target.parentFile, "${batch.id}.tmp")
        temp.writeText(gson.toJson(batch))
        check(temp.renameTo(target)) { "Could not persist download batch" }
    }
    private fun currentId(context: Context) = prefs(context).getString("current", null)
    fun restore(context: Context) {
        currentId(context)?.let { id -> runCatching { render(read(context, id)) } }
    }
    fun observe(context: Context, scope: CoroutineScope) = scope.launch(Dispatchers.IO) {
        while (isActive) { restore(context); delay(500) }
    }
    fun render(batch: Batch) {
        DownloadState.task = DownloadState.Task(batch.inspection, batch.quality, batch.folder?.let(Uri::parse), batch.zip)
        DownloadState.status.value = batch.status
        DownloadState.progress.value = batch.progress
        DownloadState.current.value = batch.current
        DownloadState.detail.value = batch.detail
        DownloadState.results.value = batch.entries.mapNotNull { entry -> when {
            entry.saved -> ItemResult(entry.media, entry.paths.sumOf { File(it).length() }.takeIf { it > 0 } ?: entry.download.format.filesize,
                entry.outputUri?.let(Uri::parse))
            entry.error.isNotEmpty() -> ItemResult(entry.media, error = entry.error)
            else -> null
        } }
        DownloadState.running.value = !batch.finished
    }

    fun enqueue(context: Context, task: DownloadState.Task, originals: Map<String, ResultItem>) {
        val id = UUID.randomUUID().toString()
        val firstId = System.currentTimeMillis() * 1000
        val entries = task.inspection.items.mapIndexed { index, media ->
            val original = checkNotNull(originals[media.url]) { "Inspect the link again before downloading." }
            val directory = File(context.filesDir, "glass-batches/$id/media/$index").apply { mkdirs() }
            val video = task.quality.kind == "video"
            val selector = if (video) {
                val height = task.quality.key.substringAfter(':').toInt()
                "bv[height=$height]+ba/b[height=$height]"
            } else if (task.quality.key == "mp3") "ba/b"
            else "ba[ext=${task.quality.key.substringAfter(':')}]/b[ext=${task.quality.key.substringAfter(':')}]"
            // Last config options override upstream defaults. Selectors are derived from
            // available formats, never from shell text or user-supplied commands.
            val extra = if (video) "-f \"$selector\" --merge-output-format mkv --use-postprocessor GlassMP4:preset=fast"
                else if (task.quality.key == "mp3") "-f \"$selector\" -x --audio-format mp3 --audio-quality 320K"
                else "-f \"$selector\""
            val item = DownloadItem(
                id = firstId + index, url = media.url, title = media.title, author = media.uploader,
                thumb = media.thumbnail, duration = original.duration,
                type = if (video) DownloadType.video else DownloadType.audio,
                format = CoreFormat(format_id = if (video) "best" else "ba"),
                container = "Default",
                downloadSections = "", allFormats = original.formats.toMutableList(),
                downloadPath = directory.absolutePath, website = original.website, downloadSize = "",
                playlistTitle = original.playlistTitle,
                audioPreferences = AudioPreferences(embedThumb = false, bitrate = if (task.quality.key == "mp3") "320K" else ""),
                videoPreferences = VideoPreferences(embedSubs = false, addChapters = false),
                extraCommands = extra, customFileNameTemplate = "%(title).150B [%(id)s].%(ext)s",
                SaveThumb = false, status = DownloadRepository.Status.Queued.toString(),
                downloadStartTime = 0, logID = null, playlistURL = original.playlistURL,
                playlistIndex = original.playlistIndex, rowNumber = index + 1
            )
            BatchEntry(media, item)
        }
        val batch = Batch(id, task.inspection, task.quality, task.folder?.toString(), task.zip,
            entries, status = context.getString(R.string.g_preparing))
        write(context, batch)
        check(prefs(context).edit().putString("current", id).commit())
        render(batch)
        WorkManager.getInstance(context).enqueueUniqueWork("glass-$id", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BatchWorker>()
                .setInputData(workDataOf("batch" to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("glass-batch").build())
    }

    fun isCancelled(context: Context, id: String) = prefs(context).getBoolean("cancel-$id", false)
    suspend fun cancel(context: Context) {
        val id = currentId(context) ?: return
        prefs(context).edit().putBoolean("cancel-$id", true).commit()
        val batch = read(context, id)
        batch.finished = true
        batch.status = context.getString(R.string.g_cancelled)
        write(context, batch)
        render(batch)
        val dao = DBManager.getInstance(context).downloadDao
        dao.setStatusMultiple(batch.entries.map { it.download.id }, DownloadRepository.Status.Cancelled.toString())
        batch.entries.forEach { RuntimeManager.destroyProcessById(it.download.id.toString()) }
    }
}

/** Observes the genuine upstream queue and publishes its completed files.
 * Both queue IDs and export destinations survive activity and process recreation.
 */
class BatchWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val batch = BatchStore.read(context, inputData.getString("batch") ?: return@withContext Result.failure())
        if (batch.finished) return@withContext Result.success()
        val db = DBManager.getInstance(context)
        val ids = batch.entries.map { it.download.id }
        val events = ConcurrentHashMap<Long, Pair<Int, String>>()
        val listener = launch { WorkerEventBus.events.collect { events[it.downloadItemID] = it.progress to it.output } }
        try {
            setForeground(foreground(batch.status))
            CoreInspection.seedDefaults(context)
            RuntimeManager.assertInit()
            check(!BatchStore.isCancelled(context, batch.id)) { context.getString(R.string.g_cancelled) }
            val known = db.downloadDao.getDownloadsByIds(ids).map { it.id }.toSet() +
                db.historyDao.getGlassBatchHistory(ids).map { it.downloadId }
            val missing = batch.entries.filter { it.download.id !in known }.map { it.download }
            if (!batch.started || missing.isNotEmpty()) {
                if (missing.isNotEmpty()) DownloadQueueUtil(context).enqueue(missing, ignoreDuplicates = true)
                batch.started = true
                BatchStore.write(context, batch)
            } else {
                val queued = db.downloadDao.getDownloadsByIds(ids).filter { it.status == "Queued" }
                // App startup recovers orphaned Active rows before runtime initialization.
                // A monitor restart must never reset a genuinely active download.
                if (queued.isNotEmpty()) {
                    DownloadRepository(db.downloadDao).startDownloadWorker(queued, context)
                }
            }
            while (currentCoroutineContext().isActive) {
                if (BatchStore.isCancelled(context, batch.id)) throw CancellationException(context.getString(R.string.g_cancelled))
                // A consistent snapshot avoids racing the core's history insert + queue
                // deletion. A deleted task with no file must fail instead of spinning.
                val (history, queued) = db.withTransaction {
                    db.historyDao.getGlassBatchHistory(ids).associateBy { it.downloadId } to
                        db.downloadDao.getDownloadsByIds(ids).associateBy { it.id }
                }
                batch.entries.forEach { entry ->
                    if (entry.saved || entry.error.isNotBlank()) return@forEach
                    val completed = history[entry.download.id]
                    if (completed != null && entry.paths.isEmpty()) {
                        entry.paths = completed.downloadPath.filter { File(it).isFile && File(it).length() > 0 }
                        if (entry.paths.isEmpty()) entry.error = context.getString(R.string.g_cannot_save)
                    } else if (queued[entry.download.id]?.status in listOf("Error", "Cancelled")) {
                        val log = queued[entry.download.id]?.logID?.let { db.logDao.getByID(it)?.content }
                        entry.error = events[entry.download.id]?.second?.takeIf { it.contains("error", true) }
                            ?: log?.lineSequence()?.lastOrNull { it.contains("ERROR", true) }
                            ?: context.getString(R.string.g_download_failed)
                    } else if (entry.paths.isEmpty() && entry.download.id !in queued && completed == null) {
                        entry.error = context.getString(R.string.g_no_output)
                    }
                    if (!batch.zip && entry.paths.isNotEmpty() && !entry.saved) {
                        try {
                            check(entry.paths.size == 1) { "Unexpected multiple outputs for a single item" }
                            val source = File(entry.paths.first())
                            entry.outputUri = allocate(batch, source, entry.outputUri).toString()
                            BatchStore.write(context, batch)
                            publish(batch, source, Uri.parse(entry.outputUri))
                            entry.download.format.filesize = source.length()
                            entry.saved = true
                            completed?.let { db.historyDao.update(it.copy(downloadPath = listOf(entry.outputUri!!))) }
                            BatchStore.write(context, batch)
                            source.delete()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { entry.error = e.message.orEmpty().ifBlank { context.getString(R.string.g_cannot_save) } }
                    }
                }
                val ready = batch.entries.count { it.paths.isNotEmpty() || it.error.isNotBlank() }
                val active = batch.entries.indexOfFirst { queued[it.download.id]?.status == "Active" }.coerceAtLeast(0)
                batch.current = active
                batch.detail = events[batch.entries[active].download.id]?.second.orEmpty().takeLast(700)
                val fractions = batch.entries.map { entry ->
                    if (entry.paths.isNotEmpty() || entry.error.isNotBlank()) 1f
                    else (events[entry.download.id]?.first ?: 0).coerceIn(0, 99) / 100f
                }
                batch.progress = fractions.sum() / batch.entries.size * if (batch.zip) 0.9f else 1f
                batch.status = context.getString(R.string.g_download_in_progress)
                BatchStore.write(context, batch)
                if (ready == batch.entries.size) break
                delay(500)
            }
            if (batch.zip) {
                val good = batch.entries.filter { it.paths.isNotEmpty() && it.error.isBlank() }
                if (good.isNotEmpty()) {
                    batch.status = context.getString(R.string.g_packaging)
                    BatchStore.write(context, batch)
                    val archive = File(context.filesDir, "glass-batches/${batch.id}/Ytdlp-G-${batch.id.take(8)}.zip")
                    ArchiveWriter.create(good.flatMap { it.paths.map(::File) }, archive) { BatchStore.isCancelled(context, batch.id) }
                    batch.archiveUri = allocate(batch, archive, batch.archiveUri).toString()
                    BatchStore.write(context, batch)
                    publish(batch, archive, Uri.parse(batch.archiveUri))
                    good.forEach { entry ->
                        entry.download.format.filesize = entry.paths.sumOf { File(it).length() }
                        entry.outputUri = batch.archiveUri
                        entry.saved = true
                        db.historyDao.getGlassBatchHistory(listOf(entry.download.id)).forEach {
                            db.historyDao.update(it.copy(downloadPath = listOf(batch.archiveUri!!)))
                        }
                    }
                    BatchStore.write(context, batch)
                    good.flatMap { it.paths }.forEach { File(it).delete() }
                    archive.delete()
                }
            }
            val done = batch.entries.count { it.saved }
            batch.progress = if (done > 0) 1f else 0f
            batch.detail = ""
            batch.status = if (done == 0) context.getString(R.string.g_download_failed)
                else context.getString(R.string.g_finished, done, batch.entries.size - done + batch.inspection.skipped)
            batch.finished = true
            BatchStore.write(context, batch)
            Result.success()
        } catch (e: CancellationException) {
            if (BatchStore.isCancelled(context, batch.id)) {
                batch.finished = true
                batch.status = context.getString(R.string.g_cancelled)
                BatchStore.write(context, batch)
            }
            throw e
        } catch (e: Exception) {
            batch.finished = true
            batch.status = if (BatchStore.isCancelled(context, batch.id)) context.getString(R.string.g_cancelled)
                else e.message.orEmpty().ifBlank { context.getString(R.string.g_download_failed) }
            batch.entries.filter { !it.saved && it.error.isBlank() }.forEach { it.error = batch.status }
            BatchStore.write(context, batch)
            Result.failure()
        } finally { listener.cancel() }
    }

    private fun foreground(message: String): ForegroundInfo {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("glass-export", context.getString(R.string.g_notification_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 300, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, "glass-export")
            .setSmallIcon(R.drawable.g_ic_download).setContentTitle("Ytdlp-G")
            .setContentText(message).setOngoing(true).setContentIntent(open).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(3091, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(3091, notification)
    }

    private fun allocate(batch: Batch, source: File, existing: String?): Uri {
        if (existing != null) return Uri.parse(existing)
        val mime = when (source.extension.lowercase()) {
            "mp4" -> "video/mp4"; "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"; "webm" -> "audio/webm"
            "zip" -> "application/zip"; else -> "application/octet-stream"
        }
        return checkNotNull(if (batch.folder != null) {
            DocumentFile.fromTreeUri(context, Uri.parse(batch.folder))?.createFile(mime, source.name)?.uri
        } else if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ytdlp-G")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })
        } else null) { context.getString(R.string.g_cannot_save) }
    }

    private fun publish(batch: Batch, source: File, uri: Uri) {
        try {
            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { sink ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (BatchStore.isCancelled(context, batch.id)) throw CancellationException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                    }
                }
            }
            if (batch.folder == null && Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}
