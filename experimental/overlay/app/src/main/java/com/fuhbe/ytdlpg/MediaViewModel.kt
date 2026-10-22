package com.fuhbe.ytdlpg

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EditorState(
    val url: String = "", val inspection: Inspection? = null, val quality: Quality? = null,
    val kind: String = "audio", val zip: Boolean = false, val folder: Uri? = null,
    val busy: Boolean = false, val message: String = "", val error: String = ""
)

class MediaViewModel(private val app: Application) : AndroidViewModel(app) {
    private val engine = CoreInspection(app)
    private val preferences = app.getSharedPreferences("glass-options", 0)
    private val mutable = MutableStateFlow(EditorState(
        folder = preferences.getString("folder", null)?.let(Uri::parse)?.takeIf { uri ->
            app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        }, zip = preferences.getBoolean("zip", false)))
    val state = mutable.asStateFlow()
    private var analysis: Job? = null
    private var generation = 0L
    private val cache = LinkedHashMap<String, Pair<Long, Inspection>>()

    init {
        CoreInspection.seedDefaults(app)
        BatchStore.restore(app)
        BatchStore.observe(app, viewModelScope)
    }

    fun input(text: String, immediately: Boolean = false) {
        cancelAnalysis()
        val currentGeneration = generation
        mutable.update { it.copy(url = text, inspection = null, quality = null, error = "", message = "") }
        if (text.isBlank()) return
        analysis = viewModelScope.launch {
            if (!immediately) delay(650)
            val url = try { normalizeMediaUrl(text) } catch (_: Exception) {
                if (immediately) mutable.update { it.copy(error = app.getString(R.string.g_invalid_link)) }
                return@launch
            }
            cache[url]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.let {
                applyInspection(it.second); return@launch
            }
            mutable.update { it.copy(busy = true, message = app.getString(R.string.g_analyzing)) }
            try {
                val found = engine.inspect(url) { count, total ->
                    if (generation == currentGeneration) mutable.update {
                        it.copy(message = app.getString(R.string.g_inspect_progress, count, total))
                    }
                }
                if (generation != currentGeneration) return@launch
                cache[url] = System.currentTimeMillis() to found
                while (cache.size > 4) cache.remove(cache.keys.first())
                applyInspection(found)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == currentGeneration) mutable.update { it.copy(error = e.message.orEmpty().takeLast(1400), message = "") }
            } finally {
                if (generation == currentGeneration) mutable.update { it.copy(busy = false) }
            }
        }
    }

    private fun applyInspection(found: Inspection) = mutable.update {
        val kind = it.kind.takeIf { kind -> found.qualities.any { q -> q.kind == kind } } ?: found.qualities.first().kind
        it.copy(inspection = found, quality = preferredQuality(found.qualities, kind), kind = kind,
            busy = false, message = "", error = "")
    }

    fun cancelAnalysis() {
        generation++
        analysis?.cancel()
        mutable.update { it.copy(busy = false, message = "") }
    }
    fun chooseKind(kind: String) = mutable.update { it.copy(kind = kind, quality = preferredQuality(it.inspection?.qualities.orEmpty(), kind)) }
    fun chooseQuality(quality: Quality) = mutable.update { it.copy(quality = quality) }
    fun chooseZip(zip: Boolean) {
        preferences.edit().putBoolean("zip", zip).apply()
        mutable.update { it.copy(zip = zip) }
    }
    fun chooseFolder(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            preferences.edit().putString("folder", uri.toString()).apply()
            mutable.update { it.copy(folder = uri) }
        } catch (_: SecurityException) { mutable.update { it.copy(error = app.getString(R.string.g_cannot_save)) } }
    }
    fun startDownload() {
        val editor = mutable.value
        val inspection = editor.inspection ?: return
        val quality = editor.quality ?: return
        if (DownloadState.running.value) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BatchStore.enqueue(app, DownloadState.Task(inspection, quality, editor.folder, editor.zip), engine.originals)
                }
            } catch (e: Exception) { mutable.update { it.copy(error = e.message.orEmpty()) } }
        }
    }
    fun cancelDownload() { viewModelScope.launch(Dispatchers.IO) { BatchStore.cancel(app) } }
    override fun onCleared() { cancelAnalysis(); super.onCleared() }
}
