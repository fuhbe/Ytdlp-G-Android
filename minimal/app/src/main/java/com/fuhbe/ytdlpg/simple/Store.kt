package com.fuhbe.ytdlpg.simple

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow

class SimpleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Store.init(this)
        Store.list().filter { it.state == "RUNNING" }.forEach { job ->
            Store.edit(job.id) { it.copy(state = "QUEUED", phase = "Retomando após interrupção") }
        }
        if (Store.list().any { it.state == "QUEUED" }) QueueWorker.kick(this)
    }
    companion object { lateinit var instance: SimpleApp }
}

/** SQLite commits preserve queue controls, partial paths and export URIs on restart. */
object Store {
    private lateinit var db: SQLiteDatabase
    private val gson = Gson()
    val jobs = MutableStateFlow<List<QueueJob>>(emptyList())
    fun init(context: Context) {
        if (::db.isInitialized && db.isOpen) db.close()
        db = object : SQLiteOpenHelper(context, "minimal-queue.db", null, 2) {
            override fun onCreate(db: SQLiteDatabase) = db.execSQL("CREATE TABLE jobs (id TEXT PRIMARY KEY, state TEXT NOT NULL, json TEXT NOT NULL)")
            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
                if (old < 2) {
                    db.execSQL("ALTER TABLE jobs ADD COLUMN state TEXT NOT NULL DEFAULT 'QUEUED'")
                    db.rawQuery("SELECT id,json FROM jobs", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val job = gson.fromJson(cursor.getString(1), QueueJob::class.java)
                            db.update("jobs", ContentValues().apply { put("state", job.state) }, "id=?", arrayOf(cursor.getString(0)))
                        }
                    }
                }
            }
        }.writableDatabase
        publish()
    }
    @Synchronized fun list(): List<QueueJob> = db.rawQuery("SELECT json FROM jobs", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(gson.fromJson(cursor.getString(0), QueueJob::class.java)) }.sortedByDescending { it.created }
    }
    @Synchronized fun get(id: String): QueueJob = db.rawQuery("SELECT json FROM jobs WHERE id=?", arrayOf(id)).use {
        check(it.moveToFirst()) { "Download não encontrado." }; gson.fromJson(it.getString(0), QueueJob::class.java)
    }
    @Synchronized fun state(id: String): String = db.rawQuery("SELECT state FROM jobs WHERE id=?", arrayOf(id)).use {
        check(it.moveToFirst()); it.getString(0)
    }
    @Synchronized fun insert(job: QueueJob) {
        db.insertOrThrow("jobs", null, ContentValues().apply { put("id", job.id); put("state", job.state); put("json", gson.toJson(job)) })
        publish()
    }
    @Synchronized fun edit(id: String, change: (QueueJob) -> QueueJob): QueueJob {
        val next = change(get(id))
        db.update("jobs", ContentValues().apply { put("state", next.state); put("json", gson.toJson(next)) }, "id=?", arrayOf(id))
        publish()
        return next
    }
    private fun publish() { jobs.value = list() }
    fun preferences(context: Context) = context.getSharedPreferences("minimal", Context.MODE_PRIVATE)
}
