package com.gaje48.lms.ui.screens.meeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.data.MeetingRepository
import com.gaje48.lms.model.ContentVmData
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Meeting
import com.gaje48.lms.util.NotificationHelper
import com.gaje48.lms.util.TransferHelper
import com.gaje48.lms.util.TransferType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

data class MeetingWithContent(
    val meeting: Meeting,
    val files: List<ContentVmData>,
    val links: List<ContentVmData>,
)

data class MeetingUiState(
    val course: Course? = null,
    val meetings: List<MeetingWithContent> = emptyList(),
)

class MeetingViewModel(
    courseCode: String,
    courseRepository: CourseRepository,
    meetingRepository: MeetingRepository,
    private val notificationHelper: NotificationHelper,
    private val transferHelper: TransferHelper,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    val uiState = combine(
        courseRepository.courses,
        meetingRepository.observeMeetings(courseCode),
        meetingRepository.observeContentVmDatasByCourse(courseCode),
    ) { courses, meetings, contents ->
        val fileKeywords = listOf("pdf", "word", "powerpoint", "excel", "archive")
        val meetingsWithContent =
            meetings.map { meeting ->
                val meetingContents = contents.filter { it.meetingNumber == meeting.meetingNumber }
                val (files, links) =
                    meetingContents.partition { item ->
                        fileKeywords.any { keyword ->
                            item.type.contains(keyword)
                        }
                    }
                MeetingWithContent(meeting, files, links)
            }
        MeetingUiState(
            course = courses.find { it.courseCode == courseCode },
            meetings = meetingsWithContent,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MeetingUiState(),
    )

    fun downloadFile(content: ContentVmData) {
        val baseName = with(content) {
            val common = "${courseName}_Pertemuan-$meetingNumber"

            when {
                title.contains("Materi") || title.contains("Import File") -> common
                title.contains("tugas", ignoreCase = true) -> "Tugas_$common"
                else -> "${common}_$title"
            }.replace(' ', '-')
        }

        val notifId = System.currentTimeMillis()
        notificationHelper.showStarted(TransferType.DOWNLOAD, notifId, baseName)

        transferHelper.downloadFile(
            notifId = notifId,
            fileUrl = content.contentUrl,
            baseName = baseName,
            courseCode = content.courseCode,
            meetingNumber = content.meetingNumber,
        )
    }
}
