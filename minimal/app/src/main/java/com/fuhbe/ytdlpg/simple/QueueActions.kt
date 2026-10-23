package com.fuhbe.ytdlpg.simple

object QueueActions {
    fun apply(job: QueueJob, action: String): QueueJob = when {
                action == "retry" && BatchRunner.records(job).none { it.state == "FAILED" && it.retryable } -> job
                action == "pause" && job.state in listOf("QUEUED", "RUNNING") -> job.copy(state = "PAUSED", phase = "Pausado; dados parciais preservados", speed = 0, eta = 0, indeterminate = false)
                action == "cancel" && job.state !in listOf("DONE", "WARNING") -> job.copy(state = "CANCELLED", phase = "Cancelado; arquivos temporários preservados", speed = 0, eta = 0, indeterminate = false)
                action in listOf("resume", "retry") && job.state in listOf("PAUSED", "ERROR", "CANCELLED", "WARNING") -> {
                    val retry = action == "retry"
                    job.copy(state = "QUEUED", phase = if (retry) "Nova tentativa dos itens elegíveis…" else "Retomando; parciais preservados…", error = "",
                        progress = if (retry) 0f else job.progress, retryGeneration = job.retryGeneration + if (retry) 1 else 0,
                        records = BatchRunner.records(job).map { if (it.state == "RUNNING" || (it.state == "FAILED" && it.retryable)) it.copy(state = "PENDING", fraction = 0f, indeterminate = true) else it })
                }
                else -> job
        }
}
