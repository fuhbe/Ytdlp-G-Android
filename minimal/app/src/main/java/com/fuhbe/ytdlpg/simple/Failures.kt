package com.fuhbe.ytdlpg.simple

import java.io.IOException

/** Stable, non-sensitive reason codes persisted independently for each item. */
data class Failure(val code: String, val message: String, val retryable: Boolean,
                   val systemic: Boolean = false, val transient: Boolean = false)
class TransferException(val failure: Failure, cause: Throwable? = null) : IOException(failure.message, cause)
class BatchInterrupted : Exception("Fila pausada ou cancelada")
object Failures {
    fun classify(error: Exception, phase: String = ""): Failure {
        if (error is TransferException) return error.failure
        val message = generateSequence(error as Throwable?) { it.cause }.take(5).joinToString(" ") { it.message.orEmpty() }.lowercase()
        return when {
            listOf("enospc", "no space", "armazenamento insuficiente", "disk full").any { it in message } ->
                Failure("NO_SPACE", "Armazenamento insuficiente. Libere espaço e retome a fila.", true, systemic = true)
            error is SecurityException || listOf("eacces", "permission denied", "pasta está inacessível", "pasta inacessível", "grant", "read-only file").any { it in message } ->
                Failure("DESTINATION", "Sem acesso à pasta. Conceda acesso novamente e retome a fila.", true, systemic = true)
            error is RuntimeBootstrapException || "instance not initialized" in message || "biblioteca" in message || "unknown encoder" in message || "postprocessor glassmp4" in message ->
                Failure("RUNTIME", "Falha no mecanismo. Copie o diagnóstico em Configurações antes de tentar novamente.", true, systemic = true)
            "429" in message || "too many requests" in message ->
                Failure("RATE_LIMIT", "O servidor limitou as solicitações (429). Tente novamente mais tarde.", true, transient = true)
            "403" in message || "401" in message || "sign in" in message || "age-restricted" in message || "authentication" in message ->
                Failure("ACCESS", "Acesso recusado ou restrito. Este item exige autorização; nenhum bloqueio será contornado.", false)
            "fragment" in message && ("not found" in message || "404" in message || "unable to continue" in message) ->
                Failure("INTEGRITY", "Um fragmento não foi recebido. O arquivo incompleto não foi aceito; tente novamente.", true)
            listOf("private video", "video unavailable", "video is unavailable", "deleted video", "has been removed", "not available", "404", "410").any { it in message } ->
                Failure("UNAVAILABLE", "Vídeo privado, removido ou indisponível.", false)
            listOf("timed out", "timeout", "connection reset", "connection refused", "temporary failure", "network is unreachable", "unable to resolve", "503", "502", "504", "500 internal").any { it in message } ->
                Failure("NETWORK", "Falha temporária de conexão. Confira a rede e tente novamente.", true, transient = true)
            "salvando" in phase.lowercase() || "export" in phase.lowercase() ->
                Failure("DESTINATION", "Não foi possível salvar na pasta de destino. Verifique acesso e espaço livre.", true, systemic = true)
            "ffmpeg" in message || "postprocessing" in message || "codec" in message ->
                Failure("PROCESSING", "O processamento de áudio/vídeo falhou. O original foi preservado para nova tentativa.", true)
            "extract" in message || "requested format" in message ->
                Failure("EXTRACTION", "O mecanismo não conseguiu obter o formato deste item. Atualize o yt-dlp e tente novamente.", true)
            else -> Failure("UNKNOWN", "Não foi possível concluir este item. Copie o diagnóstico e tente novamente.", true)
        }
    }
}
