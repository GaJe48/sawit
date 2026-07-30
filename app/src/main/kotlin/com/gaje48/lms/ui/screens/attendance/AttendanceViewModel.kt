package com.gaje48.lms.ui.screens.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AttendanceRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.model.AttendanceScreenData
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val courseName: String? = null,
    val attendanceScreenDatas: List<AttendanceScreenData> = emptyList(),
    val isProcessingAttendance: Boolean = false,
)

class AttendanceViewModel(
    private val courseCode: String,
    courseRepository: CourseRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isProcessingAttendance = MutableStateFlow(false)

    val uiState = combine(
        courseRepository.courses,
        attendanceRepository.observeAttendances(courseCode),
        attendanceRepository.observeAttendanceVmDatas(courseCode),
        _isProcessingAttendance,
    ) { courses, attendances, attendanceVmDatas, isProcessingAttendance ->
        val attendanceVmDataMap = attendanceVmDatas
            .groupBy({ it.meetingNumber }, { it.contentUrl })

        val attendanceScreenDatas = attendances.mapIndexed { index, isAttended ->
            val meetingNumber = index + 1

            AttendanceScreenData(
                isAttended = isAttended,
                contentUrls = attendanceVmDataMap[meetingNumber] ?: emptyList(),
            )
        }

        AttendanceUiState(
            courseName = courses.find { it.courseCode == courseCode }?.courseName,
            attendanceScreenDatas = attendanceScreenDatas,
            isProcessingAttendance = isProcessingAttendance,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AttendanceUiState(),
    )

    fun processAttendance(urls: List<String>) {
        _isProcessingAttendance.value = true

        viewModelScope.launch {
            attendanceRepository.executeAttendances(urls).onErr {
                _snackbarEvent.trySend(it.message ?: "Gagal melakukan absensi")

                _isProcessingAttendance.value = false
                return@launch
            }

            attendanceRepository.syncAttendancesByCourse(courseCode).onErr {
                _snackbarEvent.trySend(it.message ?: "Gagal memperbarui data")
            }

            _isProcessingAttendance.value = false
        }
    }
}
