package com.gaje48.lms.ui.screens.assignment

import android.net.Uri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.gaje48.lms.data.AssignmentRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.model.AssignmentScreenData
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

data class AssignmentUiState(
    val courseName: String? = null,
    val assignmentScreenDatas: List<AssignmentScreenData> = emptyList(),
)

interface AssignmentComponent {
    val uiState: Value<AssignmentUiState>
    val snackbarEvent: Flow<String>

    fun uploadSubmission(uri: Uri, assignmentUrl: String)

    fun downloadQuestion(url: String, courseName: String, meetingNumber: Int)

    fun onBackClick()
}

class DefaultAssignmentComponent(
    componentContext: ComponentContext,
    courseCode: String,
    private val onBack: () -> Unit,
) : AssignmentComponent, ComponentContext by componentContext, KoinComponent {
    private val courseRepository: CourseRepository = get()
    private val assignmentRepository: AssignmentRepository = get()
    private val notificationHelper: NotificationHelper = get()
    private val transferHelper: TransferHelper = get()

    private val scope = coroutineScope()

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    override val snackbarEvent: Flow<String> = _snackbarEvent.receiveAsFlow()

    private val _uiState = MutableValue(AssignmentUiState())
    override val uiState: Value<AssignmentUiState> = _uiState

    init {
        scope.launch {
            combine(
                courseRepository.courses,
                assignmentRepository.observeAssignmentScreenDatas(courseCode),
            ) { courses, assignmentScreenDatas ->
                AssignmentUiState(
                    courseName = courses.find { it.courseCode == courseCode }?.courseName,
                    assignmentScreenDatas = assignmentScreenDatas,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    override fun uploadSubmission(uri: Uri, assignmentUrl: String) {
        val notifId = System.currentTimeMillis()
        notificationHelper.showStarted(TransferType.UPLOAD, notifId, "Mengunggah tugas...")

        transferHelper.uploadFile(notifId, uri, assignmentUrl)
    }

    override fun downloadQuestion(url: String, courseName: String, meetingNumber: Int) {
        val baseName =
            when (meetingNumber) {
                8 -> "UTS_$courseName"
                16 -> "UAS_$courseName"
                else -> "Tugas_${courseName}_Pertemuan-$meetingNumber"
            }.replace(' ', '-')

        val notifId = System.currentTimeMillis()
        notificationHelper.showStarted(TransferType.DOWNLOAD, notifId, baseName)

        transferHelper.downloadFile(notifId, url, baseName)
    }

    override fun onBackClick() {
        onBack()
    }
}
