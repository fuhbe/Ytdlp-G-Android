package com.fuhbe.ytdlpg.simple

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.File

class AppModel(app: Application) : AndroidViewModel(app) {
    var url by mutableStateOf("")
    var inspection by mutableStateOf<Inspection?>(null)
    var selected by mutableStateOf<Set<String>>(emptySet())
    var choice by mutableStateOf(Choice())
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var error by mutableStateOf("")
    var folder by mutableStateOf(Store.preferences(app).getString("folder", "").orEmpty())
    var theme by mutableStateOf(Store.preferences(app).getString("theme", "system").orEmpty())
    var area by mutableStateOf("home")
    var updating by mutableStateOf(false)
    var queuing by mutableStateOf(false)
    private var analysis: Job? = null
    private var cachedInput = ""
    private var cachedAt = 0L
    private var analysisEpoch = 0L

    fun analyze() {
        val epoch = ++analysisEpoch
        analysis?.cancel()
        busy = false
        error = ""
        val links = try { Policy.links(url) } catch (e: Exception) { error = e.message.orEmpty(); return }
        val key = links.joinToString("\n")
        if (key == cachedInput && System.currentTimeMillis() - cachedAt < 300_000 && inspection != null) return
        inspection = null
        busy = true
        analysis = viewModelScope.launch {
            try {
                status = "Preparando o mecanismo…"
                val result = Engine.inspect(getApplication(), links) { message -> viewModelScope.launch { if (epoch == analysisEpoch) status = message } }
                ensureActive()
                inspection = result
                selected = result.items.filter { it.available }.map { it.id }.toSet()
                choice = Choice()
                cachedInput = key
                cachedAt = System.currentTimeMillis()
            } catch (e: TimeoutCancellationException) { error = "A análise demorou a responder. Verifique a conexão e tente novamente." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = Policy.readableError(e.message.orEmpty()) }
            finally { if (epoch == analysisEpoch) { busy = false; status = "" } }
        }
    }
    fun clear() { analysisEpoch++; analysis?.cancel(); busy = false; status = ""; inspection = null; selected = emptySet(); error = ""; cachedInput = "" }
    fun select(id: String) {
        selected = if (id in selected) selected - id else selected + id
        choice = Policy.choice(choice, selected.size)
    }
    fun setFolder(uri: Uri) {
        folder = uri.toString()
        Store.preferences(getApplication()).edit().putString("folder", folder).apply()
    }
    fun setAppearance(value: String) {
        theme = value
        Store.preferences(getApplication()).edit().putString("theme", value).apply()
    }
    fun enqueue() {
        if (queuing) return
        val inspected = inspection ?: return
        val items = inspected.items.filter { it.id in selected && it.available }
        if (items.isEmpty()) { error = "Selecione pelo menos um vídeo."; return }
        val app = getApplication<Application>()
        val job = QueueJob(title = if (items.size == 1) items.first().title else inspected.title, items = items,
            choice = Policy.choice(choice, items.size), folder = folder)
        queuing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Storage.validate(app, job.folder)
                Store.insert(job)
                QueueWorker.kick(app)
                withContext(Dispatchers.Main) { area = "downloads" }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { withContext(Dispatchers.Main) { error = Policy.readableError(e.message.orEmpty()) } }
            finally { withContext(NonCancellable + Dispatchers.Main) { queuing = false } }
        }
    }
    fun control(id: String, action: String) { viewModelScope.launch(Dispatchers.IO) { QueueWorker.control(getApplication(), id, action) } }
    fun updateEngine() {
        if (updating) return
        updating = true
        error = ""
        viewModelScope.launch {
            try { status = Engine.update(getApplication()) }
            catch (e: Exception) { error = Policy.readableError(e.message.orEmpty()) }
            finally { updating = false }
        }
    }
    fun cleanCompleted() {
        viewModelScope.launch(Dispatchers.IO) {
            Store.list().filter { it.state == "DONE" && (it.archiveUri.isNotBlank() || it.files.all { file -> file.uri.isNotBlank() }) }.forEach {
                File(getApplication<Application>().filesDir, "jobs/${it.id}").deleteRecursively()
            }
            withContext(Dispatchers.Main) { status = "Cópias temporárias dos concluídos removidas. Seus arquivos salvos foram preservados." }
        }
    }
}
