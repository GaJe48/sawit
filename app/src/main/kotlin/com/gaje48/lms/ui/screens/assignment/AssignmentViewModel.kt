package com.gaje48.lms.ui.screens.assignment

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.LoadMode
import com.gaje48.lms.model.Assignment
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssignmentUiState(
    val courseName: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val assignments: List<Assignment> = emptyList(),
    val errorMessage: String? = null,
)

class AssignmentViewModel(
    private val courseCode: String,
    private val lmsRepository: LmsRepository,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssignmentUiState())
    val uiState = _uiState.asStateFlow()

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    init { fetchAssignments() }

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun emitSnackbar(message: String) {
        _snackbarEvent.trySend(message)
    }

    fun fetchAssignments(loadMode: LoadMode = LoadMode.LOADING) {
        val dashboardData = lmsRepository.dashboardData.value ?: return
        val allMeeting = dashboardData.allMeetings.find { it.courseCode == courseCode }?.meetings ?: emptyList()
        val courseName = dashboardData.courses.find { it.courseCode == courseCode }?.courseName

        viewModelScope.launch {
            when (loadMode) {
                LoadMode.REFRESH -> _uiState.update { it.copy(isRefreshing = true, errorMessage = null, courseName = courseName) }
                LoadMode.LOADING -> _uiState.update {
                    it.copy(isLoading = true, errorMessage = null, assignments = emptyList(), courseName = courseName)
                }
            }

            lmsRepository.fetchTasks(allMeeting)
                .onSuccess { tasks -> 
                    _uiState.update { it.copy(assignments = tasks) }
                }
                .onFailure { e ->
                    e.message?.let {
                        if (_uiState.value.assignments.isEmpty()) setError(it)
                        else emitSnackbar(it)
                    }
                }

            _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
        }
    }

    fun uploadSubmission(uri: Uri, taskUrl: String) {
        val notifId = System.currentTimeMillis().toInt()
        lateinit var currentFileName: String

        viewModelScope.launch {
            notificationHelper.showUploadStarted(notifId)

            lmsRepository.uploadTask(uri, taskUrl) { fileName, progress ->
                currentFileName = fileName
                notificationHelper.showUploadProgress(notifId, fileName, progress)
            }
            .onSuccess {
                notificationHelper.showUploadSuccess(notifId, currentFileName)
            }
            .onFailure { e ->
                e.message?.let {
                    notificationHelper.showUploadFailure(notifId, it)
                    emitSnackbar(it)
                }
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
                    emitSnackbar(it)
                }
            }
        }
    }
}
