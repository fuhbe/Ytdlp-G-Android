package com.fuhbe.ytdlpg.simple

import android.content.Context
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.UUID

object Engine {
    private val initLock = Mutex()
    suspend fun ready(context: Context) = initLock.withLock {
        withContext(Dispatchers.IO) {
            if (!RuntimeManager.initialized) RuntimeManager.init(context.applicationContext)
            RuntimeManager.assertInit()
        }
    }
    fun request(url: String) = YTDLRequest(url).addOption("--ignore-config").addOption("--no-warnings")
        .addOption("--socket-timeout", 12).addOption("--retries", 2)
        .addOption("--fragment-retries", 3).addOption("--no-color").addOption("--newline")

    suspend fun execute(request: YTDLRequest, id: String, callback: ((Float, Long, String) -> Unit)? = null): com.deniscerri.ytdl.core.models.ExecuteResponse {
        val parent = currentCoroutineContext()[Job]!!
        val lastError = java.util.concurrent.atomic.AtomicReference("")
        // Do not interrupt the wait thread first: upstream would unregister the
        // parent before its FFmpeg children can be terminated. Kill the complete
        // process tree from a watchdog while the registration still exists.
        val watchdog = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (!parent.isActive) RuntimeManager.destroyProcessById(id)
                delay(100)
            }
        }
        return try {
            withContext(Dispatchers.IO) {
                try {
                    RuntimeManager.execute(request, id, redirectErrorStream = true) { percent, eta, line ->
                        if (line.contains("ERROR", true)) lastError.set(line.takeLast(1800))
                        callback?.invoke(percent, eta, line)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    throw IllegalStateException(lastError.get().ifBlank { e.message.orEmpty() }, e)
                }
            }
        } finally { watchdog.cancel(); RuntimeManager.destroyProcessById(id) }
    }

    suspend fun inspect(context: Context, links: List<String>, progress: (String) -> Unit): Inspection = withContext(Dispatchers.IO) {
        ready(context)
        val items = mutableListOf<Media>()
        var title = "Links selecionados"
        var playlist = links.size > 1
        withTimeout(120_000) {
            for ((index, link) in links.withIndex()) {
                progress("Analisando link ${index + 1} de ${links.size}…")
                val target = File(context.cacheDir, "inspect-${UUID.randomUUID()}.jsonl")
                try {
                    val request = request(link).addOption("--flat-playlist").addOption("--skip-download")
                        .addOption("--ignore-errors").addCommands(listOf("--print-to-file", "%()j", target.absolutePath))
                    var extractionError: Exception? = null
                    try { execute(request, "inspect-${target.name}") }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { extractionError = e }
                    if (!target.exists()) throw extractionError ?: IllegalStateException("Nenhum vídeo acessível foi encontrado.")
                    target.useLines { lines -> lines.filter { it.isNotBlank() }.forEach { line ->
                        val json = JSONObject(line)
                        if (title == "Links selecionados") title = json.optString("playlist_title").valid().ifBlank { json.optString("title").valid() }
                        playlist = playlist || json.optString("playlist_id").valid().isNotBlank()
                        parseMedia(json, link)?.let { items.add(it) }
                    } }
                } finally { target.delete() }
            }
            check(items.isNotEmpty()) { "Nenhum vídeo acessível foi encontrado. A playlist pode estar privada." }
            val distinct = items.distinctBy { it.id }.toMutableList()
            var heights = distinct.first().heights
            if (heights.isEmpty()) {
                progress("Consultando as qualidades disponíveis…")
                for (i in distinct.indices.take(3)) {
                    if (!distinct[i].available) continue
                    val media = try { probe(context, distinct[i]) } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { continue }
                    distinct[i] = media
                    if (media.heights.isNotEmpty()) { heights = media.heights; break }
                }
            }
            Inspection(title.ifBlank { distinct.first().title }, distinct, playlist || distinct.size > 1, heights)
        }
    }

    suspend fun probe(context: Context, item: Media): Media {
        val target = File(context.cacheDir, "probe-${UUID.randomUUID()}.jsonl")
        try {
            withTimeout(40_000) {
                execute(request(item.url).addOption("--no-playlist").addOption("--skip-download")
                    .addCommands(listOf("--print-to-file", "%()j", target.absolutePath)), "probe-${target.name}")
            }
            return target.useLines { lines -> parseMedia(JSONObject(lines.first()), item.url) ?: item }
        } finally { target.delete() }
    }

    fun parseMedia(json: JSONObject, originalUrl: String): Media? {
        val id = json.optString("id").valid()
        if (id.isBlank()) return null
        val url = json.optString("webpage_url").valid().ifBlank { json.optString("url").valid() }
            .takeIf { it.startsWith("https://") } ?: if (id.matches(Regex("[A-Za-z0-9_-]{11}"))) "https://www.youtube.com/watch?v=$id" else originalUrl
        val formats = json.optJSONArray("formats")
        val heights = mutableListOf<Int>()
        var hasAudio = false
        if (formats != null) for (i in 0 until formats.length()) {
            val format = formats.getJSONObject(i)
            if (format.optBoolean("has_drm") || format.optString("url").valid().isBlank()) continue
            val video = format.optString("vcodec").valid().let { it.isNotBlank() && it != "none" }
            val audio = format.optString("acodec").valid().let { it.isNotBlank() && it != "none" }
            if (audio) hasAudio = true
            if (video && format.optInt("height") in 1..2160) heights.add(format.optInt("height"))
        }
        val thumb = json.optString("thumbnail").valid().ifBlank {
            json.optJSONArray("thumbnails")?.let { array -> if (array.length() > 0) array.getJSONObject(array.length() - 1).optString("url") else "" }.orEmpty()
        }
        val availability = json.optString("availability").valid()
        return Media(id, url, json.optString("title").valid().ifBlank { "Vídeo indisponível" },
            json.optString("uploader").valid().ifBlank { json.optString("channel").valid() }, thumb,
            json.optDouble("duration", 0.0).takeIf { it.isFinite() }?.toLong() ?: 0,
            availability !in listOf("private", "premium_only", "subscriber_only") && json.optString("title") !in listOf("[Private video]", "[Deleted video]"),
            if (hasAudio) Policy.heights(heights) else emptyList())
    }

    suspend fun update(context: Context): String = initLock.withLock {
        withContext(Dispatchers.IO) {
            check(Store.list().none { it.state in listOf("RUNNING", "QUEUED") }) { "Aguarde ou pause os downloads antes de atualizar." }
            if (!RuntimeManager.initialized) RuntimeManager.init(context)
            RuntimeManager.updateYTDL(context)
            val version = RuntimeManager.execute(YTDLRequest(emptyList()).addOption("--version"), "version").out.trim()
            Store.preferences(context).edit().putString("engine-version", version).apply()
            "Mecanismo atualizado: $version"
        }
    }
    private fun String.valid() = if (this == "null" || this == "NA") "" else this
}
