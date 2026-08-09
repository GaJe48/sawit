package com.gaje48.lms.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.gaje48.lms.data.UpdateRepository
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class LmsUpdateService : Service() {
    companion object {
        const val EXTRA_APK_URL = "extra_apk_url"
        private const val FOREGROUND_SERVICE_ID = 4
    }

    private val updateRepository: UpdateRepository = get()
    private val notificationHelper: NotificationHelper = get()

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val baseNotif = notificationHelper
            .notifBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_download)

        val startNotif = baseNotif
            .setContentTitle("Mengunduh pembaruan...")
            .setProgress(0, 0, true)
            .build()

        startForeground(FOREGROUND_SERVICE_ID, startNotif)

        val apkUrl = intent!!.getStringExtra(EXTRA_APK_URL)!!

        serviceScope.launch {
            val result = updateRepository.downloadApk(apkUrl) { progress ->
                val progressNotif = baseNotif
                    .setContentTitle("$progress% - Mengunduh pembaruan...")
                    .setProgress(100, progress, false)
                    .build()

                notificationHelper.notifySafe(FOREGROUND_SERVICE_ID, progressNotif)
            }

            updateRepository.activeDeferred.complete(result)
            notificationHelper.cancelSafe(FOREGROUND_SERVICE_ID)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        updateRepository.activeDeferred.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
