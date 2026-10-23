package com.fuhbe.ytdlpg.simple

/** Monotonic inactivity, not a total-duration timeout. Advancing slow work survives. */
class ActivityGuard(start: Long, private val transferLimit: Long = 180_000, private val processingLimit: Long = 600_000) {
    private var last = start
    private var cpu = 0L
    private var bytes = 0L
    private var time = 0L
    private var stage = "ANALYZE"
    @Synchronized fun observe(now: Long, cpuTicks: Long = cpu, fileBytes: Long = bytes,
                              processedMicros: Long = time, phase: String = stage) {
        if (cpuTicks != cpu || fileBytes != bytes || processedMicros != time || phase != stage) last = now
        cpu = cpuTicks; bytes = fileBytes; time = processedMicros; stage = phase
    }
    @Synchronized fun stalled(now: Long): Boolean = now - last > if (stage in setOf("CONVERT", "MERGE", "VALIDATE")) processingLimit else transferLimit
}
