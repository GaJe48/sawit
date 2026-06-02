package com.gaje48.lms.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LmsAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM_TRIGGER = "com.gaje48.lms.action.ALARM_TRIGGER"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == ACTION_ALARM_TRIGGER) {
            val serviceIntent = Intent(context, LmsWatchService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
