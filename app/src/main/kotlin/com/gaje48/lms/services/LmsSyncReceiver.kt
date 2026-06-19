package com.gaje48.lms.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gaje48.lms.util.LmsLogger
import com.gaje48.lms.util.LmsNetworkHelper

class LmsSyncReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SYNC_TRIGGER = "com.gaje48.lms.ACTION_SYNC_TRIGGER"
        const val ACTION_NETWORK_AVAILABLE = "com.gaje48.lms.ACTION_NETWORK_AVAILABLE"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val networkHelper = LmsNetworkHelper(context)
        val syncScheduler = LmsSyncScheduler(context)

        when (intent.action) {
            ACTION_SYNC_TRIGGER -> {
                if (!networkHelper.isNetworkAvailable()) {
                    LmsLogger.writeLog("Alarm: Tidak ada internet. Mendaftarkan tunggu internet...")
                    networkHelper.registerInternetCallback()
                    return
                }

                syncScheduler.startSync()
            }

            ACTION_NETWORK_AVAILABLE -> {
                LmsLogger.writeLog("Callback: Internet kembali tersedia! Memulai Service...")
                networkHelper.unregisterNetworkCallback()
                syncScheduler.startSync()
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                syncScheduler.handleBootCompleted()
            }
        }
    }
}
