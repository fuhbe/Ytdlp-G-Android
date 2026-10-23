package com.fuhbe.ytdlpg.simple

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

interface QueueRepository {
    fun get(id: String): QueueJob
    fun edit(id: String, change: (QueueJob) -> QueueJob): QueueJob
}

/** Production queue policy, also exercised with controlled per-item failures. */
class BatchRunner(private val id: String, private val repository: QueueRepository = Store) {
    companion object {
        val terminal = setOf("DONE", "FAILED", "SKIPPED")
        fun records(job: QueueJob): List<ItemRecord> = job.items.mapIndexed { index, media ->
            job.records.firstOrNull { it.position == index } ?: if (job.files.any { it.mediaId == media.id })
                ItemRecord(index, media.id, state = "DONE", fraction = 1f, phase = "Concluído", indeterminate = false)
            else if (!media.available) ItemRecord(index, media.id, state = "SKIPPED", phase = "Não baixado", fraction = 1f,
                reason = "Vídeo privado, removido ou indisponível.", code = "UNAVAILABLE", indeterminate = false)
            else ItemRecord(index, media.id)
        }
        fun summary(job: QueueJob): String {
            val downloaded = job.files.size
            val failed = records(job).count { it.state in setOf("FAILED", "SKIPPED") }
            return if (job.items.size == 1) if (downloaded == 1) "Concluído" else "Não baixado"
            else "Playlist finalizada — ${job.foundTotal} itens encontrados; " + (if (job.foundTotal != job.items.size) "${job.items.size} selecionados; " else "") + "$downloaded baixados; $failed não baixados"
        }
    }
    fun progress(index: Int, update: TransferProgress) {
        repository.edit(id) { job ->
            if (job.state != "RUNNING") job else {
                val next = records(job).map { record -> if (record.position != index || record.state != "RUNNING") record else
                    record.copy(phase = update.phase, fraction = maxOf(record.fraction, update.fraction?.coerceIn(0f, .99f) ?: record.fraction),
                        bytes = maxOf(record.bytes, update.bytes), total = maxOf(record.total, update.total),
                        speed = update.speed, eta = update.eta, indeterminate = update.fraction == null)
                }
                aggregate(job, next, update.phase)
            }
        }
    }
    private fun aggregate(job: QueueJob, records: List<ItemRecord>, phase: String): QueueJob {
        val active = records.filter { it.state == "RUNNING" }
        val settled = records.count { it.state in terminal }
        val measured = records.sumOf { if (it.state in terminal) 1.0 else it.fraction.toDouble() } / records.size.coerceAtLeast(1) * .90
        return job.copy(records = records, progress = maxOf(job.progress, measured.toFloat()).coerceAtMost(.99f),
            current = settled, phase = phase, indeterminate = active.isEmpty() || active.any { it.indeterminate },
            speed = active.sumOf { it.speed }, eta = if (active.size == 1) active.first().eta else 0)
    }
    suspend fun run(parallelism: Int, check: () -> Unit, onSystemic: () -> Unit = {}, onSaved: (SavedFile) -> Unit = {},
                    transfer: suspend (Int, Media, (TransferProgress) -> Unit) -> SavedFile): QueueJob = coroutineScope {
        repository.edit(id) { job -> job.copy(records = records(job).map { if (it.state == "RUNNING") it.copy(state = "PENDING") else it }) }
        val original = repository.get(id)
        val targets = records(original).filter { it.state == "PENDING" }.map { it.position }
        val next = java.util.concurrent.atomic.AtomicInteger(0)
        val stop = AtomicBoolean(false)
        val networkFailures = java.util.concurrent.atomic.AtomicInteger(0)
        val systemic = java.util.concurrent.atomic.AtomicReference<Failure?>(null)
        suspend fun one(index: Int) {
            val media = original.items[index]
            for (attempt in 1..2) {
                check()
                repository.edit(id) { job -> aggregate(job, records(job).map { if (it.position == index)
                    it.copy(state = "RUNNING", attempts = it.attempts + 1, reason = "", phase = "Analisando item…", indeterminate = true) else it }, "Analisando item ${index + 1} de ${original.items.size}…") }
                try {
                    val saved = transfer(index, media) { progress(index, it) }
                    networkFailures.set(0)
                    check()
                    val committed = repository.edit(id) { job ->
                        if (job.state != "RUNNING") job else aggregate(job.copy(files = job.files.filterNot { it.mediaId == saved.mediaId } + saved),
                            records(job).map { if (it.position == index) it.copy(state = "DONE", fraction = 1f, phase = "Concluído", retryable = false, reason = "", code = "",
                                indeterminate = false, speed = 0, eta = 0, bytes = saved.bytes, total = saved.bytes) else it }, "Arquivo ${index + 1} concluído")
                    }
                    if (records(committed).any { it.position == index && it.state == "DONE" }) {
                        try { onSaved(saved) } catch (e: Exception) { Diagnostics.failure(Diagnostics.Stage.EXPORT, e) }
                    }
                    return
                } catch (e: CancellationException) { throw e }
                catch (e: BatchInterrupted) { throw e }
                catch (e: Exception) {
                    check() // User controls always win over a process exit/error.
                    if (stop.get() && systemic.get() != null) {
                        repository.edit(id) { job -> job.copy(records = records(job).map { if (it.position == index) it.copy(state = "PENDING", phase = "Aguardando retomada", speed = 0, eta = 0) else it }) }
                        return
                    }
                    val phase = records(repository.get(id)).first { it.position == index }.phase
                    var failure = Failures.classify(e, phase)
                    Diagnostics.failure(Diagnostics.Stage.BATCH, e)
                    if (failure.transient && attempt < 2 && !stop.get()) {
                        progress(index, TransferProgress("Conexão interrompida; tentando novamente…"))
                        delay(if (failure.code == "RATE_LIMIT") 4_000 else 2_000)
                        continue
                    }
                    if (failure.code in setOf("NETWORK", "RATE_LIMIT") && networkFailures.incrementAndGet() >= 3)
                        failure = failure.copy(systemic = true, message = "Falhas de conexão repetidas. Os itens restantes aguardam retomada; confira a rede antes de continuar.")
                    repository.edit(id) { job -> if (job.state != "RUNNING") job else aggregate(job, records(job).map {
                        if (it.position == index) it.copy(state = "FAILED", phase = "Não baixado", fraction = 1f,
                            reason = failure.message, code = failure.code, retryable = failure.retryable, indeterminate = false, speed = 0, eta = 0) else it
                    }, if (failure.systemic) failure.message else "Item ${index + 1} não baixado; continuando…") }
                    if (failure.systemic) { systemic.set(failure); stop.set(true); onSystemic() }
                    return
                }
            }
        }
        (1..parallelism.coerceIn(1, 2)).map { launch {
            while (!stop.get()) {
                check()
                val n = next.getAndIncrement()
                if (n >= targets.size) break
                one(targets[n])
            }
        } }.joinAll()
        systemic.get()?.let { throw TransferException(it) }
        check()
        repository.get(id)
    }
}
