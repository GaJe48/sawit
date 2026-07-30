package com.gaje48.lms.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.IntentCompat
import com.gaje48.lms.data.AttendanceRepository
import com.gaje48.lms.data.TransferRepository
import com.gaje48.lms.util.DownloadRequest
import com.gaje48.lms.util.NotificationHelper
import com.gaje48.lms.util.TransferType
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicInteger

class LmsDownloadService : Service() {
    companion object {
        const val EXTRA_DOWNLOAD_REQUEST = "extra_download_request"
        private const val FOREGROUND_SERVICE_ID = 2
    }

    private val transferRepository: TransferRepository by inject()
    private val attendanceRepository: AttendanceRepository by inject()
    private val notificationHelper: NotificationHelper by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isForegroundActive = false
    private val activeDownloadsCount = AtomicInteger(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_DOWNLOAD_REQUEST, DownloadRequest::class.java)
        } ?: return START_NOT_STICKY

        val notifId = request.notifId
        val courseCode = request.courseCode
        val meetingNumber = request.meetingNumber

        if (!isForegroundActive) {
            isForegroundActive = true
            startForeground(FOREGROUND_SERVICE_ID, notificationHelper.createService())
        }

        activeDownloadsCount.incrementAndGet()

        serviceScope.launch {
            val fileName = transferRepository
                .downloadFile(request.fileUrl, request.baseName) { fileName, progress ->
                    notificationHelper.showProgress(TransferType.DOWNLOAD, notifId, fileName, progress)
                }.getOrElse { throwable ->
                    val msg = throwable.message ?: "Gagal mengunduh file"
                    notificationHelper.showFailure(TransferType.DOWNLOAD, notifId, msg)

                    checkAndStopService()
                    return@launch
                }

            if (courseCode == null || meetingNumber == null) {
                notificationHelper.showSuccess(TransferType.DOWNLOAD, notifId, fileName)

                checkAndStopService()
                return@launch
            }

            val isAlreadyAttended = attendanceRepository.isAttendanceAttended(courseCode, meetingNumber)

            if (!isAlreadyAttended) {
                attendanceRepository
                    .syncAttendancesByCourse(courseCode)
                    .onErr { throwable ->
                        val msg = throwable.message ?: "Gagal memperbarui data absensi"
                        notificationHelper.showFailure(TransferType.DOWNLOAD, notifId, msg)

                        checkAndStopService()
                        return@launch
                    }
            }

            notificationHelper.showSuccess(TransferType.DOWNLOAD, notifId, fileName)

            checkAndStopService()
        }

        return START_NOT_STICKY
    }

    private fun checkAndStopService() {
        if (activeDownloadsCount.decrementAndGet() == 0) {
            isForegroundActive = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
