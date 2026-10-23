package com.fuhbe.ytdlpg.simple

import android.content.*
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

/** Controlled document-service backend with real disk streams; device SAF is separate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = SimpleApp::class)
class ExportRecoveryTest {
    private class Provider(private val root: File) : ContentProvider() {
        var creates = 0; var deletes = 0; var updates = 0
        val files = mutableMapOf<String, File>()
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val id = (++creates).toString(); files[id] = File(root, id).apply { writeBytes(ByteArray(0)) }
            return Uri.parse("content://media/downloads/$id")
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(files.getValue(uri.lastPathSegment!!),
            if (mode.contains('w')) ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE else ParcelFileDescriptor.MODE_READ_ONLY)
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor = AssetFileDescriptor(openFile(uri, mode), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int { updates++; return 1 }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int { deletes++; files.remove(uri.lastPathSegment)?.delete(); return 1 }
        override fun getType(uri: Uri) = "video/mp4"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    }
    private fun fixture(): Triple<File, File, Provider> {
        val app = RuntimeEnvironment.getApplication(); val directory = File(app.cacheDir, "provider").apply { mkdirs() }
        val provider = Provider(directory)
        provider.attachInfo(app, android.content.pm.ProviderInfo().apply { authority = "media"; exported = true; grantUriPermissions = true; applicationInfo = app.applicationInfo })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val source = File(app.cacheDir, "source.mp4").apply { writeBytes(ByteArray(180_000) { (it % 251).toByte() }) }
        return Triple(source, File(app.cacheDir, "export-journal.json"), provider)
    }
    @Test fun completedAndCopiedJournalsRecoverTheSameUriWithoutCreatingDuplicateFiles() {
        val (source, journal, provider) = fixture(); val app = RuntimeEnvironment.getApplication()
        val uri = Storage.export(app, source, "", "video/mp4", {}, journal = journal)
        assertArrayEquals(source.readBytes(), provider.files.getValue(uri.lastPathSegment!!).readBytes())
        assertEquals(uri, Storage.export(app, source, "", "video/mp4", {}, journal = journal)); assertEquals(1, provider.creates)
        val interrupted = JSONObject(journal.readText()).put("stage", "COPIED"); journal.writeText(interrupted.toString())
        assertEquals(uri, Storage.export(app, source, "", "video/mp4", {}, journal = journal))
        assertEquals(1, provider.creates); assertEquals(0, provider.deletes); assertTrue(provider.updates >= 2)
    }
    @Test fun cancellationDeletesOnlyTheIncompleteExportAndRetainsTheSource() {
        val (source, journal, provider) = fixture(); var checks = 0
        assertThrows(BatchInterrupted::class.java) { Storage.export(RuntimeEnvironment.getApplication(), source, "", "video/mp4", { if (++checks > 1) throw BatchInterrupted() }, journal = journal) }
        assertEquals(1, provider.deletes); assertTrue(provider.files.isEmpty()); assertTrue(source.isFile); assertFalse(journal.exists())
    }
    @Test fun aNewArchiveRevisionNeverDeletesThePreviouslyCommittedExport() {
        val (source, journal, provider) = fixture(); val app = RuntimeEnvironment.getApplication()
        val old = Storage.export(app, source, "", "video/mp4", {}, journal = journal)
        source.appendText("new revision")
        val new = Storage.export(app, source, "", "video/mp4", {}, journal = journal)
        assertNotEquals(old, new); assertEquals(2, provider.creates); assertEquals(0, provider.deletes)
        assertEquals(2, provider.files.size)
    }
}
