package com.fuhbe.ytdlpg.simple

import org.junit.Assert.*
import org.junit.Test

class TurboPolicyTest {
    @Test fun fragmentAndFileConcurrencyRespectMemoryThermalPowerAndMetering() {
        val good = TurboPolicy.decide(true, 16, 2L * 1024 * 1024 * 1024, false, false, false, 0, 300)
        assertEquals(4, good.fragments); assertEquals(2, good.files)
        assertEquals(1, TurboPolicy.decide(false, 16, 2L * 1024 * 1024 * 1024, false, false, false, 0, 300).files)
        assertEquals(1, TurboPolicy.decide(true, 16, 2L * 1024 * 1024 * 1024, false, true, false, 0, 300).fragments)
        assertEquals(1, TurboPolicy.decide(true, 16, 2L * 1024 * 1024 * 1024, false, false, false, 2, 300).fragments)
        assertTrue(TurboPolicy.decide(true, 16, 2L * 1024 * 1024 * 1024, false, false, false, 3, 300).cooling)
        assertEquals(2, TurboPolicy.decide(true, 16, 2L * 1024 * 1024 * 1024, false, false, true, 0, 300).fragments)
        assertEquals(1, TurboPolicy.decide(true, 16, 300L * 1024 * 1024, true, false, false, 0, 300).fragments)
    }
    @Test fun slowTransfersAndLongActiveConversionsAreNotKilledByTotalDuration() {
        val guard = ActivityGuard(0, transferLimit = 100, processingLimit = 1000)
        for (tick in 1..1000) { guard.observe(tick * 99L, fileBytes = tick.toLong(), phase = "DOWNLOAD"); assertFalse(guard.stalled(tick * 99L)) }
        assertTrue(guard.stalled(99_102))
        val conversion = ActivityGuard(0, transferLimit = 100, processingLimit = 1000)
        for (tick in 1..1000) { conversion.observe(tick * 900L, cpuTicks = tick.toLong(), phase = "CONVERT"); assertFalse(conversion.stalled(tick * 900L)) }
        assertTrue(conversion.stalled(901_001))
    }
    @Test fun permanentAccessAndSystemicDiskFailuresAreNotMisclassifiedAsNetworkRetries() {
        assertFalse(Failures.classify(java.io.IOException("Video unavailable")).retryable)
        assertFalse(Failures.classify(java.io.IOException("403 Forbidden")).transient)
        assertTrue(Failures.classify(java.io.IOException("HTTP Error 503")).transient)
        assertTrue(Failures.classify(java.io.IOException("ENOSPC")).systemic)
        assertTrue(Failures.classify(SecurityException("Permission denied")).systemic)
    }
}
