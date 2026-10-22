package com.fuhbe.ytdlpg

import android.content.Context
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.ResultRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Only adapts models. Extraction, fallback and process cancellation belong to upstream. */
class CoreInspection(private val context: Context) {
    private val db by lazy { DBManager.getInstance(context) }
    private val repository by lazy { ResultRepository(db.resultDao, db.commandTemplateDao, context) }
    val originals = ConcurrentHashMap<String, ResultItem>()

    suspend fun inspect(url: String, progress: (Int, Int) -> Unit): Inspection = withContext(Dispatchers.IO) {
        seedDefaults(context)
        RuntimeManager.assertInit()
        withTimeout(180_000) {
            val results = withTimeout(60_000) {
                repository.getResultsFromSource(url, resetResults = false, addToResults = false)
            }.distinctBy { it.url }
            check(results.isNotEmpty()) { "No accessible media was returned by the extractor." }
            val gate = Semaphore(3)
            val completed = java.util.concurrent.atomic.AtomicInteger()
            val items = results.map { result -> async {
                gate.withPermit {
                    try {
                        if (result.formats.isEmpty()) result.formats = withTimeoutOrNull(45_000) {
                            repository.getFormats(result.url)
                        }.orEmpty()
                        originals[result.url] = result
                        result.toMedia().takeIf { it.formats.isNotEmpty() }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                    finally { progress(completed.incrementAndGet(), results.size) }
                }
            } }.awaitAll().filterNotNull()
            val qualities = commonQualities(items).map {
                if (it.kind == "video") it.copy(label = "MP4 · ${it.label}", original = false) else it
            }
            check(qualities.isNotEmpty()) { "No shared downloadable formats were found. Try a single video." }
            Inspection(results.first().playlistTitle.ifBlank { results.first().title },
                results.first().thumb, items, qualities, results.size - items.size, results.first().author)
        }
    }

    private fun ResultItem.toMedia() = MediaItem(url, title, formats.map { f ->
        val video = !f.vcodec.isNullOrBlank() && f.vcodec != "none"
        val ext = when (val e = f.container.lowercase(Locale.ROOT)) {
            "mpeg_4", "mpeg-4" -> "mp4"
            "webma" -> "webm"
            else -> e
        }
        val audio = (!f.acodec.isNullOrBlank() && f.acodec != "none") || (!video && ext in setOf("m4a", "webm", "opus", "mp3", "aac"))
        Format(f.format_id, ext, f.height ?: 0,
            f.tbr.orEmpty().removeSuffix("k").toDoubleOrNull()?.toInt() ?: 0,
            video, audio, f.acodec, f.filesize.coerceAtLeast(0))
    }, thumb, author, duration.split(':').fold(0) { total, part -> total * 60 + (part.toIntOrNull() ?: 0) })

    companion object {
        fun seedDefaults(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getBoolean("glass_defaults_v1", false)) return
            prefs.edit().putBoolean("glass_defaults_v1", true)
                .putString("youtube_data_fetching_extractor", "NEWPIPE")
                .putString("formats_source", "newpipe")
                .putBoolean("disable_write_info_json", true)
                .putBoolean("use_itemurl_instead_playlisturl", true)
                .putBoolean("log_downloads", true)
                .putBoolean("use_sponsorblock", false)
                .putBoolean("use_scheduler", false)
                .putBoolean("update_app", false)
                .putBoolean("cache_downloads", true)
                .putBoolean("aria2", false)
                .putString("concurrent_downloads", "2")
                .putString("socket_timeout", "12")
                .putString("retries", "2")
                .putString("fragment_retries", "2")
                .putString("prevent_duplicate_downloads", "")
                .apply()
        }
    }
}
