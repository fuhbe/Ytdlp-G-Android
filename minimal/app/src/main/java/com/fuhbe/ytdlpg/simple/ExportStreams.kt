package com.fuhbe.ytdlpg.simple

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/** Pause/cancel closes active export streams as well as native processes. */
object ExportStreams {
    private val active = ConcurrentHashMap<String, MutableSet<Closeable>>()
    fun track(owner: String, resource: Closeable): () -> Unit {
        if (owner.isBlank()) return {}
        active.compute(owner) { _, current -> (current ?: ConcurrentHashMap.newKeySet()).apply { add(resource) } }
        return { active.computeIfPresent(owner) { _, current -> current.remove(resource); if (current.isEmpty()) null else current }; Unit }
    }
    fun cancel(owner: String) { active[owner]?.toList()?.forEach { try { it.close() } catch (_: Exception) { /* Already closed or provider disconnected. */ } } }
}
