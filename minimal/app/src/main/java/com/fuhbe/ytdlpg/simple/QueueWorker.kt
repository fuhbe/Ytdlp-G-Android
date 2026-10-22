package com.fuhbe.ytdlpg.simple

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.deniscerri.ytdl.core.RuntimeManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class QueueWorker(private val context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var active: QueueJob? = null
        try {
            setForeground(notification("Preparando downloads", null))
            Engine.ready(context)
            while (isActive) {
                val queued = Store.list().filter { it.state == "QUEUED" }.minByOrNull { it.created } ?: break
                active = queued
                val job = Store.edit(queued.id) { if (it.state == "QUEUED") it.copy(state = "RUNNING", error = "", phase = "Preparando…") else it }
                if (job.state != "RUNNING") continue
                val control = CoroutineScope(Dispatchers.IO).launch {
                    while (isActive) {
                        if (isStopped || Store.state(job.id) != "RUNNING") RuntimeManager.destroyProcessById(job.id)
                        delay(100)
                    }
                }
                try { download(job) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    Store.edit(job.id) { if (it.state == "RUNNING") it.copy(state = "ERROR", error = Policy.readableError(e.message.orEmpty()), phase = "Falha no download") else it }
                } finally { control.cancel(); RuntimeManager.destroyProcessById(job.id) }
                active = null
            }
            Result.success()
        } catch (e: CancellationException) {
            active?.let { job -> Store.edit(job.id) { if (it.state == "RUNNING") it.copy(state = "QUEUED", phase = "Interrompido pelo Android; aguardando retomada") else it } }
            throw e
        } catch (e: Exception) {
            (active?.let { listOf(it) } ?: Store.list().filter { it.state == "QUEUED" }).forEach { job ->
                Store.edit(job.id) { if (it.state in listOf("RUNNING", "QUEUED")) it.copy(state = "ERROR", phase = "Não foi possível iniciar", error = Policy.readableError(e.message.orEmpty())) else it }
            }
            Result.failure()
        } finally { active?.let { RuntimeManager.destroyProcessById(it.id) } }
    }

    private suspend fun download(job: QueueJob) {
        var checkedAt = 0L
        fun checkControl() {
            check(!isStopped) { "Download interrompido pelo Android." }
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - checkedAt >= 100) {
                check(Store.state(job.id) == "RUNNING") { "Download interrompido." }
                checkedAt = now
            }
        }
        Storage.validate(context, job.folder)
        val directory = File(context.filesDir, "jobs/${job.id}").apply { mkdirs() }
        require(StatFs(directory.path).availableBytes >= 64L * 1024 * 1024) { "Armazenamento insuficiente. Libere espaço e tente novamente." }
        val lastWrite = AtomicLong(0)
        fun report(progress: Float, phase: String, current: Int, speed: Long = 0, eta: Long = 0, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastWrite.get() < 500) return
            lastWrite.set(now)
            Store.edit(job.id) { if (it.state == "RUNNING") it.copy(progress = progress.coerceIn(0f, .999f), phase = phase, current = current, speed = speed, eta = eta) else it }
            context.getSystemService(NotificationManager::class.java).notify(600, notification(phase, job).notification)
        }
        job.items.forEachIndexed { index, media ->
            checkControl()
            val completed = Store.get(job.id).files.firstOrNull { it.mediaId == media.id }
            if (completed != null && File(completed.path).isFile) return@forEachIndexed
            val entry = File(directory, index.toString()).apply { mkdirs() }
            val output = File(entry, "finished.jsonl").apply { delete() }
            report(index.toFloat() / job.items.size * .9f, "Baixando ${index + 1} de ${job.items.size}", index, force = true)
            setForeground(notification(media.title, job))
            val request = Engine.request(media.url).addOption("--no-playlist").addOption("--continue")
                .addOption("--no-overwrites").addOption("--no-simulate").addOption("--progress")
                .addOption("--progress-delta", .5).addOption("--progress-template", "download:YG:%(progress)j")
                .addOption("-o", File(entry, "%(title).140B [%(id)s].%(ext)s").absolutePath)
                .addCommands(listOf("--print-to-file", "after_move:%(.{filepath,height,ext})j", output.absolutePath))
            if (job.choice.mp3) {
                request.addOption("-f", "ba/b").addOption("-x").addOption("--audio-format", "mp3").addOption("--audio-quality", "0")
            } else {
                request.addOption("-f", Policy.selector(job.choice.height))
                    .addOption("-S", "res:${if (job.choice.height == 0) 2160 else job.choice.height},vcodec:h264,acodec:aac")
                    .addOption("--merge-output-format", "mkv")
                    .addOption("--plugin-dirs", File(context.filesDir, "yt_dlp_plugins").absolutePath)
                    .addOption("--use-postprocessor", "GlassMP4:preset=fast")
            }
            Engine.execute(request, job.id) { _, _, line ->
                if (line.startsWith("YG:")) runCatching {
                    val p = JSONObject(line.removePrefix("YG:"))
                    val total = p.optLong("total_bytes", 0).takeIf { it > 0 } ?: p.optLong("total_bytes_estimate", 0)
                    val fraction = if (total > 0) p.optLong("downloaded_bytes").toFloat() / total else 0f
                    report((index + fraction) / job.items.size * .9f, "Baixando ${index + 1} de ${job.items.size}", index,
                        p.optDouble("speed", 0.0).takeIf { it.isFinite() }?.toLong() ?: 0, p.optLong("eta", 0))
                } else if (line.contains("[GlassMP4]") || line.contains("[ExtractAudio]")) {
                    report(index.toFloat() / job.items.size * .9f, if (job.choice.mp3) "Convertendo para MP3…" else "Preparando MP4; convertendo codecs quando necessário…", index)
                }
            }
            checkControl()
            require(output.exists()) { "O mecanismo não confirmou o arquivo final. Tente novamente." }
            val result = output.useLines { JSONObject(it.last { line -> line.isNotBlank() }) }
            val file = File(result.getString("filepath"))
            require(file.canonicalPath.startsWith(entry.canonicalPath + File.separator) && file.isFile && file.length() > 0) { "O arquivo final não foi encontrado." }
            require(file.extension == if (job.choice.mp3) "mp3" else "mp4") { "O formato final é incompatível com a seleção." }
            report((index + 1f) / job.items.size * .9f, "Salvando arquivo…", index, force = true)
            val uri = if (job.choice.zip) "" else Storage.export(context, file, job.folder, if (job.choice.mp3) "audio/mpeg" else "video/mp4", ::checkControl).toString()
            val saved = SavedFile(media.id, file.path, uri, file.length(), media.title, media.thumb, result.optInt("height", 0))
            Store.edit(job.id) { it.copy(files = it.files.filterNot { old -> old.mediaId == media.id } + saved) }
        }
        checkControl()
        val files = Store.get(job.id).files
        require(files.size == job.items.size) { "Alguns arquivos não foram concluídos." }
        if (job.choice.zip) {
            val archive = File(directory, "Ytdlp-G-${job.id.take(8)}.zip")
            Storage.archive(files.map { File(it.path) }, archive, ::checkControl) { progress ->
                report(.9f + progress * .08f, "Compactando… ${(progress * 100).toInt()}%", job.items.size)
            }
            report(.99f, "Salvando ZIP…", job.items.size, force = true)
            val uri = Storage.export(context, archive, job.folder, "application/zip", ::checkControl)
            Store.edit(job.id) { it.copy(archiveUri = uri.toString()) }
        }
        checkControl()
        Store.edit(job.id) { if (it.state == "RUNNING") it.copy(state = "DONE", progress = 1f, phase = "Concluído", current = it.items.size, speed = 0, eta = 0) else it }
    }

    private fun notification(title: String, job: QueueJob?): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("downloads", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, "downloads").setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Ytdlp-G").setContentText(title).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        if (job != null) {
            builder.setProgress(100, (Store.get(job.id).progress * 100).toInt(), false)
            for ((action, label) in listOf("pause" to "Pausar", "cancel" to "Cancelar")) {
                val intent = Intent(context, QueueActionReceiver::class.java).setAction(action).setData(Uri.parse("ytdlpg://job/${job.id}")).putExtra("id", job.id)
                builder.addAction(0, label, PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
        }
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(600, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(600, builder.build())
    }
    companion object {
        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<QueueWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("minimal-queue", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
        fun control(context: Context, id: String, action: String) {
            Store.edit(id) { job -> when {
                action == "pause" && job.state in listOf("QUEUED", "RUNNING") -> job.copy(state = "PAUSED", phase = "Pausado; dados parciais preservados", speed = 0, eta = 0)
                action == "cancel" && job.state != "DONE" -> job.copy(state = "CANCELLED", phase = "Cancelado; arquivos temporários preservados", speed = 0, eta = 0)
                action == "resume" && job.state in listOf("PAUSED", "ERROR", "CANCELLED") -> job.copy(state = "QUEUED", phase = "Retomando; o servidor pode exigir reinício", error = "")
                else -> job
            } }
            if (action != "resume") RuntimeManager.destroyProcessById(id) else kick(context)
        }
    }
}

class QueueActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { intent.getStringExtra("id")?.let { QueueWorker.control(context, it, intent.action.orEmpty()) } }
            finally { pending.finish() }
        }
    }
}
