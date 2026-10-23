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

interface MediaBackend {
    suspend fun inspect(context: Context, links: List<String>, progress: (String) -> Unit): Inspection
    suspend fun update(context: Context): String
}

object Engine : MediaBackend {
    private val initLock = Mutex()
    suspend fun ready(context: Context) = initLock.withLock {
        RuntimeSafety.run(Diagnostics.Stage.RUNTIME_INIT) {
            if (!RuntimeManager.initialized) {
                BundledRuntime.verify(context)
                RuntimeManager.init(context.applicationContext)
            }
            RuntimeManager.assertInit()
        }
    }
    fun request(url: String) = YTDLRequest(url).addOption("--ignore-config").addOption("--no-warnings")
        .addOption("--socket-timeout", 12).addOption("--retries", 2)
        .addOption("--fragment-retries", 3).addOption("--no-color").addOption("--newline")

    private val processes = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun stopJob(owner: String) { processes.entries.filter { it.value == owner }.forEach { stopProcess(it.key) } }
    fun stopProcess(id: String) {
        try { synchronized(RuntimeManager.idProcessMap) { RuntimeManager.destroyProcessById(id) } }
        catch (e: Exception) { Diagnostics.failure(Diagnostics.Stage.PROCESS, e) }
    }
    fun cpuTicks(id: String) = RuntimeManager.processCpuTicks(id)
    fun activeProcesses(owner: String) = processes.values.count { it == owner }

    private suspend fun bridge(id: String, owner: String, callback: ((Float, Long, String) -> Unit)?,
                              action: (((Float, Long, String) -> Unit)) -> com.deniscerri.ytdl.core.models.ExecuteResponse): com.deniscerri.ytdl.core.models.ExecuteResponse {
        val parent = currentCoroutineContext()[Job]!!
        val output = ProcessOutput(callback) { stopProcess(id) }
        processes[id] = owner
        val watchdog = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (!parent.isActive || output.failure.get() != null) stopProcess(id)
                delay(100)
            }
        }
        return try {
            RuntimeSafety.run(Diagnostics.Stage.PROCESS) {
                try {
                    val response = action(output::accept)
                    output.failure.get()?.let { throw it }
                    response
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    output.failure.get()?.let { throw it }
                    throw IllegalStateException(output.lastError.get().ifBlank { e.message.orEmpty() }, e)
                }
            }
        } finally { watchdog.cancel(); stopProcess(id); processes.remove(id) }
    }
    suspend fun execute(request: YTDLRequest, id: String, callback: ((Float, Long, String) -> Unit)? = null,
                        owner: String = id): com.deniscerri.ytdl.core.models.ExecuteResponse = bridge(id, owner, callback) {
        RuntimeManager.execute(request, id, redirectErrorStream = true, callback = it)
    }
    suspend fun native(command: List<String>, id: String, owner: String = id): String {
        val guard = ActivityGuard(android.os.SystemClock.elapsedRealtime())
        guard.observe(android.os.SystemClock.elapsedRealtime(), phase = "VALIDATE")
        val stalled = java.util.concurrent.atomic.AtomicBoolean(false)
        val monitor = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val cpu = try { cpuTicks(id) } catch (e: Exception) { Diagnostics.failure(Diagnostics.Stage.VALIDATE, e); -1 }
                if (cpu >= 0) {
                    guard.observe(android.os.SystemClock.elapsedRealtime(), cpuTicks = cpu)
                    if (guard.stalled(android.os.SystemClock.elapsedRealtime())) { stalled.set(true); stopProcess(id) }
                }
                delay(1_000)
            }
        }
        try { return bridge(id, owner, null) { RuntimeManager.executeImpl(command, id, redirectErrorStream = false, callback = it) }.out }
        catch (e: Exception) {
            if (stalled.get()) throw TransferException(Failure("STALLED", "A verificação ficou sem atividade de CPU. Tente novamente; o original foi preservado.", true), e)
            throw e
        } finally { monitor.cancel() }
    }

    override suspend fun inspect(context: Context, links: List<String>, progress: (String) -> Unit): Inspection = withContext(Dispatchers.IO) {
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

    override suspend fun update(context: Context): String = initLock.withLock {
        RuntimeSafety.run(Diagnostics.Stage.UPDATE) {
            check(Store.list().none { it.state in listOf("RUNNING", "QUEUED") }) { "Aguarde ou pause os downloads antes de atualizar." }
            if (!RuntimeManager.initialized) {
                BundledRuntime.verify(context)
                RuntimeManager.init(context)
            }
            RuntimeManager.updateYTDL(context)
            val version = RuntimeManager.execute(YTDLRequest(emptyList()).addOption("--version"), "version").out.trim()
            Store.preferences(context).edit().putString("engine-version", version).apply()
            "Mecanismo atualizado: $version"
        }
    }
    private fun String.valid() = if (this == "null" || this == "NA") "" else this
}
