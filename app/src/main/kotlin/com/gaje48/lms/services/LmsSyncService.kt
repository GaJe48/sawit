package com.gaje48.lms.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.data.db.AssignmentDao
import com.gaje48.lms.util.LmsLogger
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LmsSyncService :
    Service(),
    KoinComponent {
    companion object {
        private const val NOTIFICATION_ID = 1
    }

    private val lmsRepository: LmsRepository by inject()
    private val assignmentDao: AssignmentDao by inject()
    private val notificationHelper: NotificationHelper by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        LmsLogger.writeLog("SyncService onStartCommand dipanggil")

        val syncNotification = notificationHelper.createSyncNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, syncNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, syncNotification)
        }

        serviceScope.launch {
            runSync()

            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        LmsLogger.writeLog("SyncService onDestroy dipanggil\n")

        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runSync() {
        val oldUrls = assignmentDao.getAllUrls()

        lmsRepository
            .syncAll()
            .onSuccess {
                val newUrls = assignmentDao.getAllUrls()
                val addedUrls = newUrls - oldUrls.toSet()

                val newAssignments = assignmentDao.getAssignmentNotificationDetails(addedUrls)

                LmsLogger.writeLog("Sinkronisasi sukses. Ditemukan ${newAssignments.size} tugas baru.")

                newAssignments.forEach {
                    notificationHelper.showNewAssignmentNotification(
                        courseName = it.courseName,
                        title = it.description ?: "Tugas Baru",
                        deadline = it.deadline,
                    )
                }
            }.onFailure {
                val msg = it.message ?: "Gagal memperbarui data tugas"
                LmsLogger.writeLog("Sinkronisasi gagal: $msg\n")
                notificationHelper.showSyncErrorNotification(msg)
            }
    }
}
