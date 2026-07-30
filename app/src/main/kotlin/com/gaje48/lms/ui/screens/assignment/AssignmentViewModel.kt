package com.gaje48.lms.ui.screens.assignment

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AssignmentRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.model.AssignmentScreenData
import com.gaje48.lms.util.NotificationHelper
import com.gaje48.lms.util.TransferHelper
import com.gaje48.lms.util.TransferType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

data class AssignmentUiState(
    val courseName: String? = null,
    val assignmentScreenDatas: List<AssignmentScreenData> = emptyList(),
)

class AssignmentViewModel(
    courseCode: String,
    courseRepository: CourseRepository,
    assignmentRepository: AssignmentRepository,
    private val notificationHelper: NotificationHelper,
    private val transferHelper: TransferHelper,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    val uiState = combine(
        courseRepository.courses,
        assignmentRepository.observeAssignmentScreenDatas(courseCode),
    ) { courses, assignmentScreenDatas ->
        AssignmentUiState(
            courseName = courses.find { it.courseCode == courseCode }?.courseName,
            assignmentScreenDatas = assignmentScreenDatas,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AssignmentUiState(),
    )

    fun uploadSubmission(uri: Uri, assignmentUrl: String) {
        val notifId = System.currentTimeMillis()
        notificationHelper.showStarted(TransferType.UPLOAD, notifId, "Mengunggah tugas...")

        transferHelper.uploadFile(notifId, uri, assignmentUrl)
    }

    fun downloadQuestion(url: String, courseName: String, meetingNumber: Int) {
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
}
