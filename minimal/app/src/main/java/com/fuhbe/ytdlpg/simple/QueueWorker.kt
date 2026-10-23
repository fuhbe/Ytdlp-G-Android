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
                val job = Store.edit(queued.id) { if (it.state == "QUEUED") it.copy(state = "RUNNING", error = "", phase = "Preparando…", indeterminate = true, speed = 0, eta = 0) else it }
                if (job.state != "RUNNING") continue
                val control = CoroutineScope(Dispatchers.IO).launch {
                    while (isActive) {
                        if (isStopped || Store.state(job.id) != "RUNNING") { Engine.stopJob(job.id); ExportStreams.cancel(job.id) }
                        delay(100)
                    }
                }
                try { download(job) }
                catch (_: BatchInterrupted) { /* Preserve pause/cancel and proceed to another queued job. */ }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    Store.edit(job.id) { if (it.state == "RUNNING") it.copy(state = "ERROR", error = Failures.classify(e).message, phase = "Interrompido", speed = 0, eta = 0, indeterminate = false) else it }
                } finally { control.cancel(); Engine.stopJob(job.id) }
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
        } finally { active?.let { Engine.stopJob(it.id) } }
    }

    private suspend fun download(job: QueueJob) {
        val checkedAt = AtomicLong(0)
        fun checkControl() {
            if (isStopped) throw CancellationException("Interrompido pelo Android")
            val now = android.os.SystemClock.elapsedRealtime()
            val last = checkedAt.get()
            if (now - last >= 100 && checkedAt.compareAndSet(last, now) && Store.state(job.id) != "RUNNING") throw BatchInterrupted()
        }
        Storage.validate(context, job.folder)
        val directory = File(context.filesDir, "jobs/${job.id}").apply { mkdirs() }
        if (StatFs(directory.path).availableBytes < 64L * 1024 * 1024)
            throw TransferException(Failure("NO_SPACE", "Armazenamento insuficiente. Libere espaço e retome.", true, systemic = true))
        val lastWrite = AtomicLong(0)
        val settings = TurboPolicy.capture(context, job)
        Store.edit(job.id) { it.copy(optimization = settings.description) }
        val batch = BatchRunner(job.id)
        val admission = AdaptiveAdmission()
        // A ZIP needs its private originals. Normal exported files remain valid
        // even after completed temporary copies were deliberately cleaned.
        Store.edit(job.id) { current ->
            val missing = current.files.filter { (!File(it.path).isFile || File(it.path).length() != it.bytes || it.bytes <= 0) && (job.choice.zip || it.uri.isBlank()) }.map { it.mediaId }.toSet()
            current.copy(files = current.files.filterNot { it.mediaId in missing }, records = BatchRunner.records(current).map {
                if (it.mediaId in missing) it.copy(state = "PENDING", fraction = 0f, phase = "Original não encontrado; retomando", code = "INTEGRITY") else it
            })
        }
        val result = batch.run(settings.files, ::checkControl, onSystemic = { Engine.stopJob(job.id) }, onSaved = DownloadCache::cleanIntermediates) { index, media, emit ->
            checkControl()
            var selectedSettings = TurboPolicy.capture(context, job)
            while (true) {
                checkControl()
                if (selectedSettings.cooling) emit(TransferProgress("Aguardando o aparelho resfriar…"))
                else if (admission.enter(selectedSettings.files)) break
                else emit(TransferProgress("Aguardando recursos para o próximo item…"))
                delay(1_000); selectedSettings = TurboPolicy.capture(context, job)
            }
            try {
            val entry = File(directory, index.toString()).apply { mkdirs() }
            val final = DownloadCache.load(entry, media, job.choice) ?: run {
            if (Store.get(job.id).records.any { it.position == index && it.code == "INTEGRITY" }) DownloadCache.rejectCompleted(entry)
            val output = File(entry, "finished.jsonl").apply { delete() }
            val progressFile = File(entry, "ffmpeg.progress").apply { delete() }
            val processId = "${job.id}:$index:transfer"
            val pipeline = TransferProtocol(job.choice.mp3, media.duration) { update ->
                val now = android.os.SystemClock.elapsedRealtime()
                val changed = Store.get(job.id).records.firstOrNull { it.position == index }?.phase != update.phase
                if (changed || now - lastWrite.get() >= 500) {
                    lastWrite.set(now); emit(update)
                    val snapshot = Store.get(job.id)
                    context.getSystemService(NotificationManager::class.java).notify(600, notification(snapshot.phase, snapshot).notification)
                }
            }
            val guard = ActivityGuard(android.os.SystemClock.elapsedRealtime())
            val stalled = java.util.concurrent.atomic.AtomicBoolean(false)
            val monitorFailure = java.util.concurrent.atomic.AtomicReference<Exception?>(null)
            val monitor = CoroutineScope(Dispatchers.IO).launch {
                try {
                var reportedAt = android.os.SystemClock.elapsedRealtime()
                while (isActive) {
                    try { pipeline.ffmpeg(progressFile) } catch (_: java.io.IOException) { /* Concurrent FFmpeg truncate/write; retry next sample. */ }
                    val bytes = entry.walkTopDown().filter { it.isFile && it.name !in setOf("ffmpeg.progress", "finished.jsonl", "export.json") }.sumOf { it.length() }
                    guard.observe(android.os.SystemClock.elapsedRealtime(), Engine.cpuTicks(processId), bytes, pipeline.processedMicros, pipeline.phase)
                    if (guard.stalled(android.os.SystemClock.elapsedRealtime())) {
                        if (stalled.compareAndSet(false, true)) Diagnostics.event(Diagnostics.Stage.PROCESS, "stalled item=${index + 1} phase=${pipeline.phase} bytes=$bytes")
                        Engine.stopProcess(processId)
                    }
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - reportedAt >= 10_000) {
                        reportedAt = now
                        Diagnostics.event(Diagnostics.Stage.PROCESS, "item=${index + 1} bytes=$bytes cpu_ticks=${Engine.cpuTicks(processId)} media_us=${pipeline.processedMicros}")
                    }
                    delay(1_000)
                }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    monitorFailure.set(e); Diagnostics.failure(Diagnostics.Stage.PROCESS, e)
                    while (isActive) { Engine.stopProcess(processId); delay(100) }
                }
            }
            try {
                val request = DownloadRequest.make(job, media, entry, selectedSettings.fragments, File(context.filesDir, "yt_dlp_plugins"))
                setForeground(notification("Analisando item ${index + 1}", Store.get(job.id)))
                Engine.execute(request, processId, { _, _, line ->
                    checkControl()
                    val before = pipeline.phase; pipeline.line(line)
                    if (before != pipeline.phase) Diagnostics.event(Diagnostics.Stage.PROCESS, "item=${index + 1} phase=${pipeline.phase.takeIf { it in setOf("DOWNLOAD", "CONVERT", "MERGE", "REMUX", "VALIDATE") } ?: "OTHER"}")
                }, owner = job.id)
                if (stalled.get()) throw TransferException(Failure("STALLED", "O processo parou de apresentar atividade. Os dados parciais foram preservados; tente novamente.", true, transient = true))
            } catch (e: Exception) {
                checkControl()
                monitorFailure.get()?.let { throw TransferException(Failure("SUPERVISION", "A supervisão do processo falhou. Copie o diagnóstico; a fila foi interrompida com segurança.", true, systemic = true), it) }
                if (stalled.get()) throw TransferException(Failure("STALLED", "O processo ficou sem avanço de bytes, tempo de mídia ou CPU. Os dados parciais foram preservados.", true, transient = true), e)
                throw e
            } finally { monitor.cancel(); Engine.stopProcess(processId) }
            checkControl()
            if (!output.exists()) throw TransferException(Failure("INTEGRITY", "O mecanismo não confirmou o arquivo final. O item não foi marcado como concluído.", true))
            val final = output.useLines { lines -> JSONObject(lines.last { it.isNotBlank() }) }
            val file = File(final.getString("filepath"))
            require(file.canonicalPath.startsWith(entry.canonicalPath + File.separator)) { "Caminho de saída inválido" }
            emit(TransferProgress("Verificando integridade do arquivo…"))
            MediaValidator.validate(context, file, media, job.choice.mp3, "${job.id}:$index:validate", job.id)
            DownloadCache.save(entry, media, job.choice, final)
            Diagnostics.event(Diagnostics.Stage.VALIDATE, "item=${index + 1} complete")
            final
            }
            checkControl()
            val file = File(final.getString("filepath"))
            emit(TransferProgress("Salvando arquivo…"))
            Diagnostics.event(Diagnostics.Stage.EXPORT, "item=${index + 1} start")
            val uri = if (job.choice.zip) "" else Storage.export(context, file, job.folder,
                if (job.choice.mp3) "audio/mpeg" else "video/mp4", ::checkControl,
                progress = { bytes, total ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastWrite.get() >= 500) { lastWrite.set(now); emit(TransferProgress("Salvando arquivo…", .96f + .035f * bytes.toFloat() / total, bytes, total)) }
                }, journal = File(entry, "export.json"), owner = job.id).toString()
            SavedFile(media.id, file.path, uri, file.length(), media.title, media.thumb, final.optInt("height", 0))
            } finally { admission.leave() }
        }
        checkControl()
        val files = result.files.sortedBy { saved -> job.items.indexOfFirst { it.id == saved.mediaId } }
        if (files.isEmpty()) throw TransferException(Failure("NO_COMPLETED", "Nenhum arquivo foi concluído. Consulte os itens não baixados.", true))
        if (job.choice.zip) {
            val archive = File(directory, "Ytdlp-G-${job.id.take(8)}.zip")
            var last = 0L
            val inputs = files.joinToString("\n") { "${it.mediaId}|${it.bytes}|${File(it.path).lastModified()}" }
            val manifest = File(directory, "archive-inputs.txt")
            if (!archive.isFile || !manifest.isFile || manifest.readText() != inputs)
            Storage.archiveSelected(files.map { File(it.path) }, archive, job.items.size, ::checkControl, progress = { progress ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - last >= 500) {
                    last = now
                    Store.edit(job.id) { if (it.state == "RUNNING") it.copy(progress = maxOf(it.progress, .90f + progress * .08f),
                        phase = "Compactando arquivos concluídos…", indeterminate = false, speed = 0, eta = 0) else it }
                }
            }, verifying = { Store.edit(job.id) { it.copy(phase = "Verificando integridade do ZIP…", indeterminate = true) } })
            val manifestAtomic = android.util.AtomicFile(manifest)
            val manifestOutput = manifestAtomic.startWrite()
            try { manifestOutput.write(inputs.toByteArray()); manifestAtomic.finishWrite(manifestOutput) }
            catch (e: Exception) { manifestAtomic.failWrite(manifestOutput); throw e }
            Store.edit(job.id) { it.copy(phase = "Salvando ZIP…", indeterminate = true) }
            val uri = Storage.export(context, archive, job.folder, "application/zip", ::checkControl, journal = File(directory, "archive-export.json"), owner = job.id)
            Store.edit(job.id) { it.copy(archiveUri = uri.toString()) }
        }
        checkControl()
        Store.edit(job.id) { current -> if (current.state != "RUNNING") current else {
            val warning = BatchRunner.records(current).any { it.state in setOf("FAILED", "SKIPPED") }
            current.copy(state = if (warning) "WARNING" else "DONE", progress = 1f, phase = BatchRunner.summary(current),
                current = current.items.size, speed = 0, eta = 0, indeterminate = false, error = "")
        } }
    }

    private fun notification(title: String, job: QueueJob?): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("downloads", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, "downloads").setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Ytdlp-G").setContentText(title).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        if (job != null) {
            val current = Store.get(job.id)
            builder.setProgress(100, (current.progress * 100).toInt(), current.indeterminate)
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
            Store.edit(id) { QueueActions.apply(it, action) }
            if (action !in listOf("resume", "retry")) { Engine.stopJob(id); ExportStreams.cancel(id) } else kick(context)
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
