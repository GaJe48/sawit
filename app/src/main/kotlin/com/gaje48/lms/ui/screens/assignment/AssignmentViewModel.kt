package com.gaje48.lms.ui.screens.assignment

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.UpdateAction
import com.gaje48.lms.model.AssignmentScreenData
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
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val assignmentScreenDatas: List<AssignmentScreenData> = emptyList(),
    val errorMessage: String? = null,
)

class AssignmentViewModel(
    private val courseCode: String,
    private val lmsRepository: LmsRepository,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        lmsRepository.courses,
        lmsRepository.observeAssignmentScreenDatas(courseCode),
        _isLoading,
        _isRefreshing,
        _errorMessage
    ) { courses, assignmentScreenDatas, isLoading, isRefreshing, errorMessage ->
        AssignmentUiState(
            courseName = courses.find { it.courseCode == courseCode }?.courseName,
            assignmentScreenDatas = assignmentScreenDatas,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AssignmentUiState()
    )

    fun fetchAssignments(updateAction: UpdateAction = UpdateAction.LOADING) {
        viewModelScope.launch {
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

            lmsRepository.syncAll().onFailure { e ->
                e.message?.let {
                    if (uiState.value.assignmentScreenDatas.isEmpty()) _errorMessage.value = it
                    else _snackbarEvent.trySend(it)
                }
            }

            _isRefreshing.value = false
            _isLoading.value = false
        }
    }

    fun uploadSubmission(uri: Uri, taskUrl: String) {
        val notifId = System.currentTimeMillis().toInt()
        lateinit var currentFileName: String

        viewModelScope.launch {
            notificationHelper.showUploadStarted(notifId)

            val uploadStatus = lmsRepository.uploadTask(uri, taskUrl) { fileName, progress ->
                currentFileName = fileName
                notificationHelper.showUploadProgress(notifId, fileName, progress)
            }
            .onFailure { e ->
                e.message?.let {
                    notificationHelper.showUploadFailure(notifId, it)
                    _snackbarEvent.trySend(it)
                }
            }

            if (uploadStatus.isSuccess) {
                lmsRepository.syncAll()
                    .onSuccess { notificationHelper.showUploadSuccess(notifId, currentFileName) }
                    .onFailure { e -> e.message?.let { _snackbarEvent.trySend(it) } }
            }
        }
    }

    fun downloadFile(fileUrl: String) {
        val notifId = System.currentTimeMillis().toInt()
        lateinit var currentFileName: String

        viewModelScope.launch {
            notificationHelper.showDownloadStarted(notifId)

            lmsRepository.downloadFile(fileUrl) { fileName, progress ->
                currentFileName = fileName
                notificationHelper.showDownloadProgress(notifId, fileName, progress)
            }
            .onSuccess {
                notificationHelper.showDownloadSuccess(notifId, currentFileName)
            }
            .onFailure { e ->
                e.message?.let {
                    notificationHelper.showDownloadFailure(notifId, it)
                    _snackbarEvent.trySend(it)
                }
            }
        }
    }
}
