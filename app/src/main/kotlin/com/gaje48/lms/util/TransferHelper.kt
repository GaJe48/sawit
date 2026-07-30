package com.gaje48.lms.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import com.gaje48.lms.services.LmsDownloadService
import com.gaje48.lms.services.LmsUploadService
import kotlinx.parcelize.Parcelize

@Parcelize
data class DownloadRequest(
    val notifId: Long,
    val fileUrl: String,
    val baseName: String,
    val courseCode: String? = null,
    val meetingNumber: Int? = null,
) : Parcelable

@Parcelize
data class UploadRequest(
    val notifId: Long,
    val uri: Uri,
    val assignmentUrl: String,
) : Parcelable

class TransferHelper(private val context: Context) {
    fun downloadFile(
        notifId: Long,
        fileUrl: String,
        baseName: String,
        courseCode: String? = null,
        meetingNumber: Int? = null,
    ) {
        val request =
            DownloadRequest(
                notifId = notifId,
                fileUrl = fileUrl,
                baseName = baseName,
                courseCode = courseCode,
                meetingNumber = meetingNumber,
            )

        val intent = Intent(context, LmsDownloadService::class.java).apply {
            putExtra(LmsDownloadService.EXTRA_DOWNLOAD_REQUEST, request)
        }
        context.startForegroundService(intent)
    }

    fun uploadFile(
        notifId: Long,
        uri: Uri,
        assignmentUrl: String,
    ) {
        val request = UploadRequest(notifId, uri, assignmentUrl)

        val intent = Intent(context, LmsUploadService::class.java).apply {
            putExtra(LmsUploadService.EXTRA_UPLOAD_REQUEST, request)
        }
        context.startForegroundService(intent)
    }
}
