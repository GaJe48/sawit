package com.gaje48.lms.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gaje48.lms.MainActivity
import com.gaje48.lms.R

class NotificationHelper(
    context: Context,
) {
    private companion object {
        const val CHANNEL_ID_HIGH = "lms_high"
        const val CHANNEL_NAME_HIGH = "High"
        const val CHANNEL_ID_DEFAULT = "lms_default"
        const val CHANNEL_NAME_DEFAULT = "Default"
        const val CHANNEL_ID_LOW = "lms_low"
        const val CHANNEL_NAME_LOW = "Low"
        const val GROUP_KEY = "lms"
        const val SUMMARY_ID = 0
    }

    private val appContext = context.applicationContext

    private val notificationManager =
        appContext
            .getSystemService(NotificationManager::class.java)
            .apply {
                createNotificationChannels(
                    listOf(
                        NotificationChannel(
                            CHANNEL_ID_HIGH,
                            CHANNEL_NAME_HIGH,
                            NotificationManager.IMPORTANCE_HIGH,
                        ),
                        NotificationChannel(
                            CHANNEL_ID_DEFAULT,
                            CHANNEL_NAME_DEFAULT,
                            NotificationManager.IMPORTANCE_DEFAULT,
                        ),
                        NotificationChannel(
                            CHANNEL_ID_LOW,
                            CHANNEL_NAME_LOW,
                            NotificationManager.IMPORTANCE_LOW,
                        ),
                    ),
                )
            }

    private val lastNotifUpdate = mutableMapOf<Int, Long>()

    private fun updateGroupSummary() {
        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(R.drawable.icon_notification)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .also { notificationManager.notify(SUMMARY_ID, it.build()) }
    }

    fun showDownloadStarted(notifId: Int) {
        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Menyiapkan Download...")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showDownloadProgress(
        notifId: Int,
        fileName: String,
        progress: Float,
    ) {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val now = System.currentTimeMillis()

        if (now - (lastNotifUpdate[notifId] ?: 0) < 500) return
        lastNotifUpdate[notifId] = now

        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Mengunduh File...")
            .setContentText("$fileName - $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showDownloadSuccess(
        notifId: Int,
        fileName: String,
    ) {
        lastNotifUpdate.remove(notifId)

        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Selesai!")
            .setContentText(fileName)
            .setGroup(GROUP_KEY)
            .also {
                notificationManager.notify(notifId, it.build())
                updateGroupSummary()
            }
    }

    fun showDownloadFailure(
        notifId: Int,
        message: String,
    ) {
        lastNotifUpdate.remove(notifId)

        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Gagal")
            .setContentText(message)
            .setGroup(GROUP_KEY)
            .also {
                notificationManager.notify(notifId, it.build())
                updateGroupSummary()
            }
    }

    fun showUploadStarted(notifId: Int) {
        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Menyiapkan Upload...")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showUploadProgress(
        notifId: Int,
        fileName: String,
        progress: Float,
    ) {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val now = System.currentTimeMillis()

        if (now - (lastNotifUpdate[notifId] ?: 0) < 500) return
        lastNotifUpdate[notifId] = now

        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Mengunggah Tugas...")
            .setContentText("$fileName - $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showUploadCompleting(
        notifId: Int,
        fileName: String,
    ) {
        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Menyelesaikan proses upload...")
            .setContentText(fileName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showUploadSuccess(
        notifId: Int,
        fileName: String,
    ) {
        lastNotifUpdate.remove(notifId)

        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Upload Selesai!")
            .setContentText(fileName)
            .setGroup(GROUP_KEY)
            .also {
                notificationManager.notify(notifId, it.build())
                updateGroupSummary()
            }
    }

    fun showUploadFailure(
        notifId: Int,
        message: String,
    ) {
        lastNotifUpdate.remove(notifId)

        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Upload Gagal")
            .setContentText(message)
            .setGroup(GROUP_KEY)
            .also {
                notificationManager.notify(notifId, it.build())
                updateGroupSummary()
            }
    }

    fun createSyncNotification(): Notification {
        val intent =
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return Notification
            .Builder(appContext, CHANNEL_ID_LOW)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle("Sinkronisasi LMS")
            .setContentText("Sedang memeriksa tugas baru...")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun showNewAssignmentNotification(
        courseName: String,
        title: String,
        deadline: String,
    ) {
        val intent =
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notifId = System.currentTimeMillis().toInt()
        Notification
            .Builder(appContext, CHANNEL_ID_HIGH)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle("Ada Tugas Baru!")
            .setContentText("[$courseName] $title (Deadline: $deadline)")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showSyncErrorNotification(message: String) {
        val intent =
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notifId = System.currentTimeMillis().toInt()
        Notification
            .Builder(appContext, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Gagal Memeriksa Tugas")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }
}
