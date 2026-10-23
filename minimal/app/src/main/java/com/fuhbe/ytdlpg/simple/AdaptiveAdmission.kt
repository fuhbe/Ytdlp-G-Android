package com.fuhbe.ytdlpg.simple

/** Lower resource limits apply before the next item starts, without discarding active work. */
class AdaptiveAdmission {
    private var active = 0
    @Synchronized fun enter(limit: Int): Boolean {
        if (active >= limit.coerceIn(1, 2)) return false
        active++; return true
    }
    @Synchronized fun leave() { check(active > 0); active-- }
}
