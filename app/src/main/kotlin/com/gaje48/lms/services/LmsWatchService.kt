package com.gaje48.lms.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.data.db.AssignmentDao
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LmsWatchService :
    Service(),
    KoinComponent {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ALARM_INTERVAL_MILLIS = 15 * 60 * 1000L // 15 minutes
    }

    private val lmsRepository: LmsRepository by inject()
    private val assignmentDao: AssignmentDao by inject()
    private val notificationHelper: NotificationHelper by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var syncJob: Job? = null
    private var countdownJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val nextCheckTime = System.currentTimeMillis() + ALARM_INTERVAL_MILLIS

        val initialNotification = notificationHelper.createWatcherNotification("15:00")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        syncJob?.cancel()
        syncJob =
            serviceScope.launch {
                val oldUrls = assignmentDao.getAllUrls()

                lmsRepository
                    .syncAll()
                    .onSuccess {
                        val newUrls = assignmentDao.getAllUrls()
                        val addedUrls = newUrls - oldUrls.toSet()

                        val newAssignments = assignmentDao.getAssignmentNotificationDetails(addedUrls)
                        newAssignments.forEach {
                            notificationHelper.showNewAssignmentNotification(
                                courseName = it.courseName,
                                title = it.description ?: "Tugas Baru",
                                deadline = it.deadline,
                            )
                        }
                    }.onFailure {
                        notificationHelper.showWatcherErrorNotification(it.message ?: "Gagal memperbarui data tugas")
                    }

                val alarmManager = getSystemService(AlarmManager::class.java)
                val intent =
                    Intent(this@LmsWatchService, LmsAlarmReceiver::class.java).apply {
                        action = LmsAlarmReceiver.ACTION_ALARM_TRIGGER
                    }
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        this@LmsWatchService,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                val canScheduleExact =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        alarmManager.canScheduleExactAlarms()
                    } else {
                        true
                    }

                if (canScheduleExact) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextCheckTime,
                        pendingIntent,
                    )
                } else {
                    notificationHelper.showWatcherErrorNotification("Izin alarm presisi tidak aktif.")
                }
            }

        countdownJob?.cancel()
        countdownJob =
            serviceScope.launch {
                val notificationManager = getSystemService(NotificationManager::class.java)
                while (true) {
                    val now = System.currentTimeMillis()
                    val remaining = nextCheckTime - now
                    if (remaining <= 0) {
                        val notification = notificationHelper.createWatcherNotification("00:00")
                        notificationManager.notify(NOTIFICATION_ID, notification)
                        break
                    }
                    val minutes = (remaining / 1000) / 60
                    val seconds = (remaining / 1000) % 60
                    val timeStr = "%02d:%02d".format(minutes, seconds)
                    val notification = notificationHelper.createWatcherNotification(timeStr)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    delay(1000L)
                }
            }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
