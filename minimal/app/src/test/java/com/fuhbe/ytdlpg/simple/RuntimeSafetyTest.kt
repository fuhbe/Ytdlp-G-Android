package com.fuhbe.ytdlpg.simple

import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = SimpleApp::class)
class RuntimeSafetyTest {
    @Test fun zipInitializerErrorIsReportedWithoutLosingItsCauseOrRunningOnMain() = runBlocking {
        val cause = ExceptionInInitializerError(RuntimeException("https://youtu.be/private?token=secret123"))
        val failure = try {
            RuntimeSafety.run(Diagnostics.Stage.RUNTIME_INIT) {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                throw cause
            }
            error("Expected initialization failure")
        } catch (e: RuntimeBootstrapException) { e }
        assertSame(cause, failure.cause)
        assertTrue(failure.message!!.contains("RUNTIME_LINKAGE"))
        val report = Diagnostics.snapshot(RuntimeEnvironment.getApplication())
        assertTrue(report.contains("ExceptionInInitializerError"))
        assertTrue(report.contains("RUNTIME_INIT"))
        assertFalse(report.contains("youtu.be"))
        assertFalse(report.contains("secret123"))
    }
    @Test fun cancellationAndUnrelatedFatalErrorsAreNotDisguisedAsSuccessfulAnalysis() = runBlocking {
        val cancellation = kotlinx.coroutines.CancellationException("cancel")
        try { RuntimeSafety.run(Diagnostics.Stage.ANALYZE) { throw cancellation }; fail() }
        catch (e: kotlinx.coroutines.CancellationException) { assertEquals(cancellation.message, e.message) }
        val fatal = AssertionError("not a recoverable linkage error")
        try { RuntimeSafety.run(Diagnostics.Stage.RUNTIME_INIT) { throw fatal }; fail() }
        catch (e: AssertionError) { assertEquals(fatal.message, e.message) }
    }
    @Test fun missingNativeLibraryFailsClearlyBeforeStartingAnyProcess() {
        val app = RuntimeEnvironment.getApplication()
        val directory = File(app.cacheDir, "empty-native").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationInfo() = ApplicationInfo(super.getApplicationInfo()).apply { nativeLibraryDir = directory.path }
        }
        val error = assertThrows(IllegalStateException::class.java) { BundledRuntime.verify(context) }
        assertTrue(error.message!!.contains("libpython.so"))
        assertTrue(error.message!!.contains("ausente"))
    }
    @Test fun incompatibleAbiFailsBeforeExecution() {
        val app = RuntimeEnvironment.getApplication()
        val directory = File(app.cacheDir, "wrong-abi").apply { mkdirs() }
        // Deliberately invalid machine ID in a fixture; no native process runs.
        File(directory, "libpython.so").apply {
            writeBytes(ByteArray(20).apply { this[0] = 0x7f; this[1] = 69; this[2] = 76; this[3] = 70; this[18] = 127 })
            setExecutable(true)
        }
        val context = object : ContextWrapper(app) {
            override fun getApplicationInfo() = ApplicationInfo(super.getApplicationInfo()).apply { nativeLibraryDir = directory.path }
        }
        val error = assertThrows(IllegalStateException::class.java) { BundledRuntime.verify(context) }
        assertTrue(error.message!!.contains("incompatível"))
    }
}
