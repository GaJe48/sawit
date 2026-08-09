package com.gaje48.lms.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )

                confirmationIntent?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
