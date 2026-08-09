package com.gaje48.lms.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.IntentCompat
import com.gaje48.lms.data.AssignmentRepository
import com.gaje48.lms.data.TransferRepository
import com.gaje48.lms.util.NotificationHelper
import com.gaje48.lms.util.TransferType
import com.gaje48.lms.util.UploadRequest
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import java.util.concurrent.atomic.AtomicInteger

class LmsUploadService : Service() {
    companion object {
        const val EXTRA_UPLOAD_REQUEST = "extra_upload_request"
        private const val FOREGROUND_SERVICE_ID = 3
    }

    private val transferRepository: TransferRepository = get()
    private val assignmentRepository: AssignmentRepository = get()
    private val notificationHelper: NotificationHelper = get()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isForegroundActive = false
    private val activeUploadsCount = AtomicInteger(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = IntentCompat.getParcelableExtra(
            intent!!,
            EXTRA_UPLOAD_REQUEST,
            UploadRequest::class.java,
        )!!

        val notifId = request.notifId
        val assignmentUrl = request.assignmentUrl

        if (!isForegroundActive) {
            isForegroundActive = true
            startForeground(FOREGROUND_SERVICE_ID, notificationHelper.createService())
        }

        activeUploadsCount.incrementAndGet()

        serviceScope.launch {
            val fileName = transferRepository
                .uploadSubmission(request.uri, assignmentUrl) { name, progress ->
                    notificationHelper.showProgress(TransferType.UPLOAD, notifId, name, progress)
                }.getOrElse { throwable ->
                    val msg = throwable.message ?: "Gagal mengunggah tugas"
                    notificationHelper.showFailure(TransferType.UPLOAD, notifId, msg)

                    checkAndStopService()
                    return@launch
                }

            assignmentRepository.syncAssignmentStatus(assignmentUrl).onErr { throwable ->
                val msg = throwable.message ?: "Gagal memperbarui status tugas"
                notificationHelper.showFailure(TransferType.UPLOAD, notifId, msg)

                checkAndStopService()
                return@launch
            }

            notificationHelper.showSuccess(TransferType.UPLOAD, notifId, fileName)

            checkAndStopService()
        }

        return START_NOT_STICKY
    }

    private fun checkAndStopService() {
        if (activeUploadsCount.decrementAndGet() == 0) {
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
