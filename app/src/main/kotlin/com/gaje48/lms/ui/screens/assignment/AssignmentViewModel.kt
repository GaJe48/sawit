package com.gaje48.lms.ui.screens.assignment

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.AssignmentScreenData
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssignmentUiState(
    val courseName: String? = null,
    val assignmentScreenDatas: List<AssignmentScreenData> = emptyList(),
)

class AssignmentViewModel(
    private val courseCode: String,
    private val lmsRepository: LmsRepository,
    private val notificationHelper: NotificationHelper,
    private val externalScope: CoroutineScope,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    val uiState =
        combine(
            lmsRepository.courses,
            lmsRepository.observeAssignmentScreenDatas(courseCode),
        ) { courses, assignmentScreenDatas ->
            AssignmentUiState(
                courseName = courses.find { it.courseCode == courseCode }?.courseName,
                assignmentScreenDatas = assignmentScreenDatas,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AssignmentUiState(),
        )

    fun uploadSubmission(
        uri: Uri,
        assignmentUrl: String,
    ) {
        val notifId = System.currentTimeMillis().toInt()
        val meetingUrl =
            uiState.value.assignmentScreenDatas
                .firstOrNull()
                ?.meetingUrl
        if (meetingUrl == null) {
            _snackbarEvent.trySend("Data pertemuan tidak ditemukan")
            return
        }

        notificationHelper.showUploadStarted(notifId)

        externalScope.launch {
            val fileName =
                lmsRepository
                    .uploadSubmission(uri, assignmentUrl) { name, progress ->
                        notificationHelper.showUploadProgress(notifId, name, progress)
                    }.getOrElse { exception ->
                        val msg = exception.message ?: "Gagal mengunggah tugas"
                        notificationHelper.showUploadFailure(notifId, msg)
                        _snackbarEvent.trySend(msg)

                        return@launch
                    }

            notificationHelper.showUploadCompleting(notifId, fileName)

            lmsRepository
                .syncAssignment(assignmentUrl, meetingUrl)
                .onSuccess {
                    notificationHelper.showUploadSuccess(notifId, fileName)
                }.onFailure { exception ->
                    _snackbarEvent.trySend(exception.message ?: "Gagal memperbarui data")
                }
        }
    }

    fun downloadQuestion(fileUrl: String) {
        val state = uiState.value

        val meetingNumber = state.assignmentScreenDatas.find { it.assignmentFileUrl == fileUrl }?.meetingNumber
        val courseName = state.courseName?.replace(' ', '-')

        if (meetingNumber == null || courseName == null) {
            _snackbarEvent.trySend("Gagal membuat nama berkas")
            return
        }

        val rawFileName = "Tugas_${courseName}_Pertemuan-$meetingNumber"
        val notifId = System.currentTimeMillis().toInt()

        notificationHelper.showDownloadStarted(notifId)

        externalScope.launch {
            val downloadedFileName =
                lmsRepository
                    .downloadFile(fileUrl, rawFileName) { name, progress ->
                        notificationHelper.showDownloadProgress(notifId, name, progress)
                    }.getOrElse { exception ->
                        val msg = exception.message ?: "Gagal mengunduh berkas"
                        notificationHelper.showDownloadFailure(notifId, msg)
                        _snackbarEvent.trySend(msg)

                        return@launch
                    }

            notificationHelper.showDownloadSuccess(notifId, downloadedFileName)
        }
    }
}
