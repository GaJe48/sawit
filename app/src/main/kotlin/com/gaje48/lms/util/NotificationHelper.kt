package com.gaje48.lms.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.gaje48.lms.MainActivity
import com.gaje48.lms.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class NotificationHelper(private val context: Context) {
    private companion object {
        const val CHANNEL_ID_HIGH = "lms_high"
        const val CHANNEL_NAME_HIGH = "High"
        const val CHANNEL_ID_LOW = "lms_low"
        const val CHANNEL_NAME_LOW = "Low"
        private val MIN_DELAY = 200.milliseconds
    }

    private val notificationManager = context
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
                        CHANNEL_ID_LOW,
                        CHANNEL_NAME_LOW,
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                ),
            )
        }

    private val lock = Any()

    private val scope = CoroutineScope(Dispatchers.Default)
    private val pendingNotifyMap = LinkedHashMap<Int, Notification>()
    private var lastNotifyTime = 0L

    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (trigger in triggerChannel) {
                processNotificationQueue()
            }
        }
    }

    fun notifySafe(notifId: Int, notification: Notification) {
        synchronized(lock) {
            pendingNotifyMap[notifId] = notification
        }

        triggerChannel.trySend(Unit)
    }

    private suspend fun processNotificationQueue() {
        while (true) {
            val notifId = synchronized(lock) {
                pendingNotifyMap.keys.firstOrNull() ?: break
            }

            val now = SystemClock.elapsedRealtime()
            val timeSinceLast = (now - lastNotifyTime).milliseconds
            if (timeSinceLast < MIN_DELAY) {
                delay(MIN_DELAY - timeSinceLast)
            }

            val notification = synchronized(lock) {
                pendingNotifyMap.remove(notifId)
            }

            notification?.let {
                notificationManager.notify(notifId, notification)
                lastNotifyTime = SystemClock.elapsedRealtime()
            }
        }
    }

    fun notifBuilder() = Notification.Builder(context, CHANNEL_ID_LOW)

    fun cancelSafe(notifId: Int) {
        synchronized(lock) {
            pendingNotifyMap.remove(notifId)
        }
    }

    fun createService() = Notification
        .Builder(context, CHANNEL_ID_LOW)
        .setSmallIcon(R.drawable.icon_notification)
        .setWhen(0)
        .build()

    fun showStarted(
        transferType: TransferType,
        notifId: Long,
        title: String,
    ) {
        val icon =
            when (transferType) {
                TransferType.DOWNLOAD -> android.R.drawable.stat_sys_download
                TransferType.UPLOAD -> android.R.drawable.stat_sys_upload
            }

        Notification
            .Builder(context, CHANNEL_ID_LOW)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setWhen(notifId)
            .setGroup(notifId.toString())
            .build()
            .also { notifySafe(notifId.toInt(), it) }
    }

    fun showProgress(
        transferType: TransferType,
        notifId: Long,
        fileName: String,
        progress: Int,
    ) {
        val icon =
            when (transferType) {
                TransferType.DOWNLOAD -> android.R.drawable.stat_sys_download
                TransferType.UPLOAD -> android.R.drawable.stat_sys_upload
            }

        Notification
            .Builder(context, CHANNEL_ID_LOW)
            .setSmallIcon(icon)
            .setContentTitle("$progress% - $fileName")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(notifId)
            .setGroup(notifId.toString())
            .build()
            .also { notifySafe(notifId.toInt(), it) }
    }

    fun showSuccess(
        transferType: TransferType,
        notifId: Long,
        fileName: String,
    ) {
        val icon =
            when (transferType) {
                TransferType.DOWNLOAD -> android.R.drawable.stat_sys_download_done
                TransferType.UPLOAD -> android.R.drawable.stat_sys_upload_done
            }

        val msg =
            when (transferType) {
                TransferType.DOWNLOAD -> "Download Selesai!"
                TransferType.UPLOAD -> "Upload Selesai!"
            }

        Notification
            .Builder(context, CHANNEL_ID_LOW)
            .setSmallIcon(icon)
            .setContentTitle(fileName)
            .setContentText(msg)
            .setWhen(notifId)
            .setGroup(notifId.toString())
            .build()
            .also { notifySafe(notifId.toInt(), it) }
    }

    fun showFailure(
        transferType: TransferType,
        notifId: Long,
        error: String,
    ) {
        val msg =
            when (transferType) {
                TransferType.DOWNLOAD -> "Download Gagal"
                TransferType.UPLOAD -> "Upload Gagal"
            }

        Notification
            .Builder(context, CHANNEL_ID_LOW)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(msg)
            .setContentText(error)
            .setWhen(notifId)
            .setGroup(notifId.toString())
            .build()
            .also { notifySafe(notifId.toInt(), it) }
    }

    fun createSync(): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification
            .Builder(context, CHANNEL_ID_LOW)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle("Sinkronisasi LMS")
            .setContentText("Sedang memeriksa tugas baru...")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun showNewAssignment(
        courseName: String,
        title: String,
        deadline: String,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        Notification
            .Builder(context, CHANNEL_ID_HIGH)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle("Ada Tugas Baru!")
            .setContentText("[$courseName] $title (Deadline: $deadline)")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            .also { notifySafe(System.currentTimeMillis().toInt(), it) }
    }

    fun showSyncErrorNotification(message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        Notification
            .Builder(context, CHANNEL_ID_LOW)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Gagal Memeriksa Tugas")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            .also { notifySafe(System.currentTimeMillis().toInt(), it) }
    }
}

enum class TransferType { DOWNLOAD, UPLOAD }
