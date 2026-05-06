package com.gaje48.lms.ui.screens.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.Content
import com.gaje48.lms.model.UpdateAction
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContentUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val contents: List<Content> = emptyList(),
    val errorMessage: String? = null
)

class ContentViewModel(
    meetingUrl: String,
    private val lmsRepository: LmsRepository,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        lmsRepository.observeContents(meetingUrl),
        _isLoading,
        _isRefreshing,
        _errorMessage
    ) { contents, isLoading, isRefreshing, errorMessage ->
        ContentUiState(
            contents = contents,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContentUiState()
    )

    fun fetchContents(updateAction: UpdateAction = UpdateAction.LOADING) {
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
                    if (uiState.value.contents.isEmpty()) _errorMessage.value = it
                    else _snackbarEvent.trySend(it)
                }
            }

            _isRefreshing.value = false
            _isLoading.value = false
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
