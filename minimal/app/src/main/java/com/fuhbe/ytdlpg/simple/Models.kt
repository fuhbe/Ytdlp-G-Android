package com.fuhbe.ytdlpg.simple

import java.net.URI
import java.util.UUID

data class Media(
    val id: String, val url: String, val title: String, val author: String = "",
    val thumb: String = "", val duration: Long = 0, val available: Boolean = true,
    val heights: List<Int> = emptyList()
)
data class Inspection(val title: String, val items: List<Media>, val playlist: Boolean, val heights: List<Int>)
data class Choice(val mp3: Boolean = false, val height: Int = 0, val zip: Boolean = false)
data class SavedFile(val mediaId: String, val path: String, val uri: String = "", val bytes: Long,
                     val title: String, val thumb: String, val height: Int = 0)
data class QueueJob(
    val id: String = UUID.randomUUID().toString(), val title: String, val items: List<Media>,
    val choice: Choice, val folder: String, val state: String = "QUEUED",
    val progress: Float = 0f, val current: Int = 0, val speed: Long = 0, val eta: Long = 0,
    val phase: String = "Na fila", val error: String = "", val files: List<SavedFile> = emptyList(),
    val archiveUri: String = "", val created: Long = System.currentTimeMillis(),
    val records: List<ItemRecord> = emptyList(), val indeterminate: Boolean = true,
    val foundTotal: Int = items.size, val retryGeneration: Int = 0,
    val optimization: String = ""
)

data class ItemRecord(
    val position: Int, val mediaId: String, val state: String = "PENDING",
    val phase: String = "Aguardando", val fraction: Float = 0f,
    val reason: String = "", val code: String = "", val retryable: Boolean = false,
    val attempts: Int = 0, val bytes: Long = 0, val total: Long = 0,
    val speed: Long = 0, val eta: Long = 0, val indeterminate: Boolean = true
)
data class TransferProgress(
    val phase: String, val fraction: Float? = null, val bytes: Long = 0,
    val total: Long = 0, val speed: Long = 0, val eta: Long = 0
)

object Policy {
    fun links(text: String): List<String> {
        val links = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(text.trim()).map { it.value.trimEnd(',', ')', ';') }.toList().distinct()
        require(links.isNotEmpty()) { "Cole um link do YouTube válido." }
        require(links.size <= 100) { "Adicione até 100 links por vez." }
        links.forEach { link ->
            val uri = runCatching { URI(link) }.getOrNull()
            val host = uri?.host?.lowercase().orEmpty()
            require(uri?.scheme == "https" && (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")) && uri.userInfo == null) {
                "Use links HTTPS do YouTube ou youtu.be."
            }
        }
        return links
    }
    fun zipAllowed(selected: Int) = selected >= 2
    fun heights(values: Iterable<Int>) = values.filter { it in 1..2160 }.distinct().sortedDescending()
    fun selector(height: Int): String {
        require(height == 0 || height in 1..2160)
        val cap = if (height == 0) 2160 else height
        return "bv*[height<=$cap]+ba/b[height<=$cap]"
    }
    fun choice(choice: Choice, selected: Int) = choice.copy(zip = choice.zip && zipAllowed(selected))
    fun readableError(message: String): String = when {
        message.contains("403") -> "O YouTube recusou o acesso (403). Atualize o mecanismo e tente novamente; este vídeo pode exigir autenticação."
        message.contains("private", true) || message.contains("Sign in", true) -> "Vídeo privado ou com acesso restrito. Não é possível baixar sem a autorização necessária."
        message.contains("No space", true) || message.contains("ENOSPC", true) -> "Armazenamento insuficiente. Libere espaço e tente novamente."
        message.contains("unavailable", true) -> "Este vídeo está indisponível. Confira o link ou selecione outro item."
        message.contains("timed out", true) || message.contains("resolve", true) -> "A conexão demorou a responder. Verifique a rede e tente novamente."
        else -> message.takeLast(700).ifBlank { "Não foi possível concluir. Verifique a conexão e tente novamente." }
    }
}
