package com.gaje48.lms.ui.screens.assignment

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.AssignmentScreenData
import com.gaje48.lms.model.UpdateAction
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssignmentUiState(
    val courseName: String? = null,
    val assignmentScreenDatas: List<AssignmentScreenData> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

class AssignmentViewModel(
    private val courseCode: String,
    private val lmsRepository: LmsRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState =
        combine(
            lmsRepository.courses,
            lmsRepository.observeAssignmentScreenDatas(courseCode),
            _isLoading,
            _isRefreshing,
            _errorMessage,
        ) { courses, assignmentScreenDatas, isLoading, isRefreshing, errorMessage ->
            AssignmentUiState(
                courseName = courses.find { it.courseCode == courseCode }?.courseName,
                assignmentScreenDatas = assignmentScreenDatas,
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                errorMessage = errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AssignmentUiState(),
        )

    fun fetchAssignments(updateAction: UpdateAction = UpdateAction.LOADING) {
        when (updateAction) {
            UpdateAction.REFRESH -> {
                _isRefreshing.value = true
                _errorMessage.value = null
            }

            UpdateAction.LOADING -> {
                _isLoading.value = true
                _errorMessage.value = null
            }
        }

        viewModelScope.launch {
            lmsRepository.syncAll().onFailure {
                val msg = it.message ?: "Gagal memperbarui data"
                if (uiState.value.assignmentScreenDatas.isEmpty()) {
                    _errorMessage.value = msg
                } else {
                    _snackbarEvent.trySend(msg)
                }
            }

            _isRefreshing.value = false
            _isLoading.value = false
        }
    }

    fun uploadSubmission(
        uri: Uri,
        assignmentUrl: String,
    ) {
        val notifId = System.currentTimeMillis().toInt()
        notificationHelper.showUploadStarted(notifId)

        viewModelScope.launch {
            lmsRepository
                .uploadTask(uri, assignmentUrl) { fileName, progress ->
                    notificationHelper.showUploadProgress(notifId, fileName, progress)
                }.onSuccess { fileName ->
                    notificationHelper.showUploadCompleting(notifId, fileName)

                    lmsRepository
                        .syncAssignment(
                            assignmentUrl,
                            uiState.value.assignmentScreenDatas
                                .first()
                                .meetingUrl,
                        ).onSuccess { notificationHelper.showUploadSuccess(notifId, fileName) }
                        .onFailure {
                            _snackbarEvent.trySend(
                                it.message ?: "Gagal memperbarui data",
                            )
                        }
                }.onFailure {
                    val msg = it.message ?: "Gagal mengunggah tugas"
                    notificationHelper.showUploadFailure(notifId, msg)
                    _snackbarEvent.trySend(msg)
                }
        }
    }

    fun downloadAssignmentFile(fileUrl: String) {
        val state = uiState.value
        val meetingNumber =
            state.assignmentScreenDatas.find { it.assignmentFileUrl == fileUrl }?.meetingNumber
                ?: run {
                    _snackbarEvent.trySend("Gagal membuat nama berkas")
                    return
                }
        val courseName =
            state.courseName?.replace(" ", "-") ?: run {
                _snackbarEvent.trySend("Gagal membuat nama berkas")
                return
            }
        val rawFileName = "Tugas_${courseName}_Pertemuan-$meetingNumber"

        val notifId = System.currentTimeMillis().toInt()
        notificationHelper.showDownloadStarted(notifId)

        viewModelScope.launch {
            lmsRepository
                .downloadFile(fileUrl, rawFileName) { fileName, progress ->
                    notificationHelper.showDownloadProgress(notifId, fileName, progress)
                }.onSuccess { notificationHelper.showDownloadSuccess(notifId, it) }
                .onFailure {
                    val msg = it.message ?: "Gagal mengunduh berkas"
                    notificationHelper.showDownloadFailure(notifId, msg)
                    _snackbarEvent.trySend(msg)
                }
        }
    }
}
