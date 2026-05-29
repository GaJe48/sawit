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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContentUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val contents: List<Content> = emptyList(),
    val errorMessage: String? = null,
)

class ContentViewModel(
    meetingUrl: String,
    private val lmsRepository: LmsRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val contentVmDatas = lmsRepository.observeContentVmDatas(meetingUrl)

    val uiState =
        combine(
            contentVmDatas,
            _isLoading,
            _isRefreshing,
            _errorMessage,
        ) { contentVmDatas, isLoading, isRefreshing, errorMessage ->
            ContentUiState(
                contents =
                    contentVmDatas.map {
                        Content(
                            it.type,
                            it.title,
                            it.contentUrl,
                        )
                    },
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                errorMessage = errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ContentUiState(),
        )

    fun fetchContents(updateAction: UpdateAction = UpdateAction.LOADING) {
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
                if (uiState.value.contents.isEmpty()) {
                    _errorMessage.value = msg
                } else {
                    _snackbarEvent.trySend(msg)
                }
            }

            _isRefreshing.value = false
            _isLoading.value = false
        }
    }

    fun downloadFile(fileUrl: String) {
        val notifId = System.currentTimeMillis().toInt()
        notificationHelper.showDownloadStarted(notifId)

        viewModelScope.launch {
            val contentVmData = contentVmDatas.first().find { it.contentUrl == fileUrl }
            if (contentVmData == null) {
                _snackbarEvent.trySend("Gagal membuat nama berkas")
                return@launch
            }

            val courseCode = contentVmData.courseCode
            val fileName =
                "Materi_${contentVmData.courseName}_Pertemuan-${contentVmData.meetingNumber}_${contentVmData.title}"
                    .replace(' ', '-')

            lmsRepository
                .downloadFile(fileUrl, fileName) { name, progress ->
                    notificationHelper.showDownloadProgress(notifId, name, progress)
                }.onSuccess { downloadedData ->
                    notificationHelper.showDownloadSuccess(notifId, downloadedData)
                }.onFailure { exception ->
                    val msg = exception.message ?: "Gagal mengunduh berkas"
                    notificationHelper.showDownloadFailure(notifId, msg)
                    _snackbarEvent.trySend(msg)

                    return@launch
                }

            lmsRepository.syncAttendancesByCourse(courseCode).onFailure { exception ->
                _snackbarEvent.trySend(exception.message ?: "Gagal memperbarui data")
            }
        }
    }
}
