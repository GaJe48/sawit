package com.gaje48.lms.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gaje48.lms.util.LmsLogger
import com.gaje48.lms.util.LmsSyncPrefs
import kotlin.time.Duration.Companion.minutes

class LmsSyncScheduler(
    context: Context,
) {
    companion object {
        private val SYNC_INTERVAL = 30.minutes.inWholeMilliseconds
    }

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val pendingIntent =
        Intent(appContext, LmsSyncReceiver::class.java)
            .apply {
                action = LmsSyncReceiver.ACTION_SYNC_TRIGGER
            }.let { intent ->
                PendingIntent.getBroadcast(
                    appContext,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
    private val syncPrefs = LmsSyncPrefs(appContext)

    fun getOrCalculateNextExpectedSyncTime(): Long {
        val expected = syncPrefs.nextExpectedSyncTime
        val now = System.currentTimeMillis()

        var nextTime = if (expected == 0L) now + SYNC_INTERVAL else expected

        if (nextTime <= now) {
            val initialDelay = now - nextTime

            while (nextTime <= now) nextTime += SYNC_INTERVAL

            LmsLogger.writeLog("Terjadi keterlambatan eksekusi sebesar ${initialDelay / 1000} detik. Menyesuaikan grid waktu berikutnya.")
        }

        syncPrefs.nextExpectedSyncTime = nextTime
        return nextTime
    }

    fun scheduleNextSync() {
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        val nextCheckTime = getOrCalculateNextExpectedSyncTime()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextCheckTime,
                pendingIntent,
            )

            LmsLogger.writeLog("Sync exact dijadwalkan pada ${nextCheckTime - System.currentTimeMillis()} ms mendatang")
        }
    }

    fun scheduleSyncIfNecessary() {
        val expected = syncPrefs.nextExpectedSyncTime
        val now = System.currentTimeMillis()
        if (expected <= now) scheduleNextSync()
    }

    fun cancelNextSync() {
        alarmManager.cancel(pendingIntent)

        syncPrefs.nextExpectedSyncTime = 0L
    }

    fun handleBootCompleted() {
        val expected = syncPrefs.nextExpectedSyncTime
        if (expected == 0L) return

        val now = System.currentTimeMillis()
        if (now < expected) {
            LmsLogger.writeLog(
                "Boot: Masih dalam window 30 menit. Menjadwalkan sisa waktu: ${(expected - now) / 1000} detik.",
            )

            scheduleNextSync()
        } else {
            LmsLogger.writeLog(
                "Boot: Sudah melewati window 30 menit. Menjalankan sinkronisasi langsung dan menjadwalkan ulang.",
            )

            startSync()
        }
    }

    fun startSync() {
        scheduleNextSync()

        Intent(appContext, LmsSyncService::class.java).let { intent ->
            appContext.startForegroundService(intent)
        }
    }
}
