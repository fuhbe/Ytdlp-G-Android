package com.fuhbe.ytdlpg

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val url: String = "",
    val inspection: Inspection? = null,
    val quality: Quality? = null,
    val kind: String = "audio",
    val zip: Boolean = false,
    val folder: Uri? = null,
    val busy: Boolean = false,
    val message: String = "",
    val error: String = ""
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val engine = MediaEngine(context)
    private val preferences = context.getSharedPreferences("download-options", 0)
    private val savedFolder = preferences.getString("folder", null)?.let(Uri::parse)?.takeIf { uri ->
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }
    private val mutable = MutableStateFlow(EditorState(
        folder = savedFolder, zip = preferences.getBoolean("zip", false)
    ))
    val state = mutable.asStateFlow()
    private var analysis: Job? = null
    private var generation = 0L
    private val cache = LinkedHashMap<String, Pair<Long, Inspection>>()

    fun input(text: String, immediately: Boolean = false) {
        cancelAnalysis()
        val requestGeneration = generation
        mutable.update { it.copy(url = text, inspection = null, quality = null, error = "", message = "") }
        if (text.isBlank()) return
        analysis = viewModelScope.launch {
            if (!immediately) delay(650)
            val url = try { normalizeMediaUrl(text) } catch (_: Exception) {
                if (immediately) mutable.update { it.copy(error = context.getString(R.string.invalid_link)) }
                return@launch
            }
            val cached = cache[url]?.takeIf { System.currentTimeMillis() - it.first < 10 * 60_000 }
            if (cached != null) {
                applyInspection(cached.second)
                return@launch
            }
            mutable.update { it.copy(busy = true, message = context.getString(R.string.analyzing)) }
            try {
                val result = engine.inspect(url) { current, total ->
                    if (generation == requestGeneration) mutable.update {
                        it.copy(message = context.getString(R.string.inspect_progress, current, total))
                    }
                }
                if (generation != requestGeneration) return@launch
                cache[url] = System.currentTimeMillis() to result
                while (cache.size > 4) cache.remove(cache.keys.first())
                applyInspection(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == requestGeneration) mutable.update {
                    it.copy(error = error.message.orEmpty().takeLast(1400), message = "")
                }
            } finally {
                if (generation == requestGeneration) mutable.update { it.copy(busy = false) }
            }
        }
    }

    private fun applyInspection(found: Inspection) {
        mutable.update {
            val kind = it.kind.takeIf { kind -> found.qualities.any { quality -> quality.kind == kind } }
                ?: found.qualities.first().kind
            it.copy(inspection = found, kind = kind, quality = preferredQuality(found.qualities, kind),
                busy = false, message = "", error = "")
        }
    }

    fun cancelAnalysis() {
        generation++
        analysis?.cancel()
        engine.cancelInspection()
        mutable.update { it.copy(busy = false, message = "") }
    }

    fun chooseKind(kind: String) = mutable.update {
        it.copy(kind = kind, quality = preferredQuality(it.inspection?.qualities.orEmpty(), kind))
    }
    fun chooseQuality(quality: Quality) = mutable.update { it.copy(quality = quality) }
    fun chooseZip(zip: Boolean) {
        preferences.edit().putBoolean("zip", zip).apply()
        mutable.update { it.copy(zip = zip) }
    }
    fun chooseFolder(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            preferences.edit().putString("folder", uri.toString()).apply()
            mutable.update { it.copy(folder = uri) }
        } catch (_: SecurityException) {
            mutable.update { it.copy(error = context.getString(R.string.cannot_save)) }
        }
    }
    fun startDownload() {
        val current = mutable.value
        val inspection = current.inspection ?: return
        val quality = current.quality ?: return
        if (DownloadState.running.value) return
        DownloadState.reset(DownloadState.Task(inspection, quality, current.folder, current.zip))
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (error: Exception) {
            DownloadState.running.value = false
            DownloadState.status.value = error.message.orEmpty()
        }
    }
    override fun onCleared() {
        cancelAnalysis()
        engine.close()
        super.onCleared()
    }
}
