package com.gaje48.lms.ui.screens.meeting

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.data.MeetingRepository
import com.gaje48.lms.model.ContentVmData
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Meeting
import com.gaje48.lms.util.NotificationHelper
import com.gaje48.lms.util.TransferHelper
import com.gaje48.lms.util.TransferType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

data class MeetingWithContent(
    val meeting: Meeting,
    val files: List<ContentVmData>,
    val links: List<ContentVmData>,
)

data class MeetingUiState(
    val course: Course? = null,
    val meetings: List<MeetingWithContent> = emptyList(),
)

interface MeetingComponent {
    val uiState: Value<MeetingUiState>
    val snackbarEvent: Flow<String>

    fun downloadFile(content: ContentVmData)

    fun onBackClick()
}

class DefaultMeetingComponent(
    componentContext: ComponentContext,
    courseCode: String,
    private val onBack: () -> Unit,
) : MeetingComponent, ComponentContext by componentContext, KoinComponent {
    private val courseRepository: CourseRepository = get()
    private val meetingRepository: MeetingRepository = get()
    private val notificationHelper: NotificationHelper = get()
    private val transferHelper: TransferHelper = get()

    private val scope = coroutineScope()

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    override val snackbarEvent: Flow<String> = _snackbarEvent.receiveAsFlow()

    private val _uiState = MutableValue(MeetingUiState())
    override val uiState: Value<MeetingUiState> = _uiState

    init {
        scope.launch {
            combine(
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
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    override fun downloadFile(content: ContentVmData) {
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

    override fun onBackClick() {
        onBack()
    }
}
