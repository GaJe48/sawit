package com.gaje48.lms.ui.screens.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.LoadMode
import com.gaje48.lms.model.Content
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeetingDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val allContent: List<Content> = emptyList(),
    val errorMessage: String? = null
)

class ContentViewModel(
    private val meetingUrl: String,
    private val lmsRepository: LmsRepository,
    private val notificationHelper: NotificationHelper
) : ViewModel() {
    private val _uiState = MutableStateFlow(MeetingDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    init { loadMeetingDetail() }

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun emitSnackbar(message: String) {
        _snackbarEvent.trySend(message)
    }

    fun loadMeetingDetail(loadMode: LoadMode = LoadMode.LOADING) {
        viewModelScope.launch {
            when (loadMode) {
                LoadMode.REFRESH -> _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                LoadMode.LOADING -> _uiState.update {
                    it.copy(isLoading = true, errorMessage = null, allContent = emptyList())
                }
            }

            lmsRepository.fetchMeetingDetail(meetingUrl)
                .onSuccess { content ->
                    _uiState.update { it.copy(allContent = content) }
                }
                .onFailure { e ->
                    e.message?.let {
                        if (_uiState.value.allContent.isEmpty()) setError(it)
                        else emitSnackbar(it)
                    }
                }

            _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
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
