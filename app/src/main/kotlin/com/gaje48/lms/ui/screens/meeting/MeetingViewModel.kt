package com.gaje48.lms.ui.screens.meeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Meeting
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeetingUiState(
    val course: Course? = null,
    val meetings: List<Meeting> = emptyList(),
)

class MeetingViewModel(
    private val courseCode: String,
    private val lmsRepository: LmsRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    val uiState =
        combine(
            lmsRepository.courses,
            lmsRepository.observeMeetings(courseCode),
        ) { courses, meetings ->
            MeetingUiState(
                course = courses.find { it.courseCode == courseCode },
                meetings = meetings,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MeetingUiState(),
        )

    fun observeContentVmDatas(meetingUrl: String) = lmsRepository.observeContentVmDatas(meetingUrl)

    fun downloadFile(
        fileUrl: String,
        meetingUrl: String,
    ) {
        val notifId = System.currentTimeMillis().toInt()
        notificationHelper.showDownloadStarted(notifId)

        viewModelScope.launch {
            val contentVmDatas = lmsRepository.observeContentVmDatas(meetingUrl).first()
            val contentVmData = contentVmDatas.find { it.contentUrl == fileUrl }
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
                }.onSuccess {
                    notificationHelper.showDownloadSuccess(notifId, it)
                }.onFailure {
                    val msg = it.message ?: "Gagal mengunduh berkas"
                    notificationHelper.showDownloadFailure(notifId, msg)
                    _snackbarEvent.trySend(msg)

                    return@launch
                }

            lmsRepository.syncAttendancesByCourse(courseCode).onFailure {
                _snackbarEvent.trySend(it.message ?: "Gagal memperbarui data")
            }
        }
    }
}
