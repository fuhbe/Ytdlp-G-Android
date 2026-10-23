package com.fuhbe.ytdlpg.simple

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Native DASH/HLS fragment concurrency; direct HTTP keeps its internal downloader. */
data class TurboSettings(val fragments: Int, val files: Int, val cooling: Boolean) {
    val description get() = "Otimização automática • até $fragments fragmentos" + if (files > 1) " • até $files arquivos" else ""
}
object TurboPolicy {
    fun decide(mp3: Boolean, count: Int, memory: Long, lowMemory: Boolean, powerSave: Boolean,
               metered: Boolean, thermal: Int, temperature: Int): TurboSettings {
        val constrained = lowMemory || powerSave || memory < 768L * 1024 * 1024 || thermal >= 2 || temperature >= 430
        val fragments = if (constrained) 1 else if (metered) 2 else 4
        val files = if (mp3 && count > 1 && !constrained && !metered && memory >= 1536L * 1024 * 1024) 2 else 1
        return TurboSettings(fragments, files, thermal >= 3 || temperature >= 480)
    }
    fun capture(context: Context, job: QueueJob): TurboSettings {
        val info = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val power = context.getSystemService(PowerManager::class.java)
        val network = context.getSystemService(ConnectivityManager::class.java)
        val temperature = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val thermal = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else 0
        return decide(job.choice.mp3, job.items.size, info.availMem, info.lowMemory, power.isPowerSaveMode,
            network.isActiveNetworkMetered || (Build.VERSION.SDK_INT >= 24 && network.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED), thermal, temperature)
    }
}
