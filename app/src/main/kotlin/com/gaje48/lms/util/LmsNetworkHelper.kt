package com.gaje48.lms.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.gaje48.lms.services.LmsSyncReceiver

class LmsNetworkHelper(
    context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val networkPendingIntent =
        Intent(context, LmsSyncReceiver::class.java)
            .apply {
                action = LmsSyncReceiver.ACTION_NETWORK_AVAILABLE
            }.let { intent ->
                val flags =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

                PendingIntent.getBroadcast(context, 100, intent, flags)
            }

    fun isNetworkAvailable(): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.run {
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false

    fun registerInternetCallback() {
        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
        cm.registerNetworkCallback(request, networkPendingIntent)
    }

    fun unregisterNetworkCallback() {
        cm.unregisterNetworkCallback(networkPendingIntent)
    }
}
