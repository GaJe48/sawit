package com.gaje48.lms.ui.screens.attendance

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.gaje48.lms.data.AttendanceRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.model.AttendanceScreenData
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class AttendanceUiState(
    val courseName: String? = null,
    val attendanceScreenDatas: List<AttendanceScreenData> = emptyList(),
    val isProcessingAttendance: Boolean = false,
)

interface AttendanceComponent {
    val uiState: Value<AttendanceUiState>
    val snackbarEvent: Flow<String>

    fun processAttendance(urls: List<String>)

    fun onBackClick()
}

class DefaultAttendanceComponent(
    componentContext: ComponentContext,
    private val courseCode: String,
    private val onBack: () -> Unit,
) : AttendanceComponent, ComponentContext by componentContext, KoinComponent {
    private val courseRepository: CourseRepository by inject()
    private val attendanceRepository: AttendanceRepository by inject()

    private val scope = coroutineScope()

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    override val snackbarEvent: Flow<String> = _snackbarEvent.receiveAsFlow()

    private val _isProcessingAttendance = MutableStateFlow(false)

    private val _uiState = MutableValue(AttendanceUiState())
    override val uiState: Value<AttendanceUiState> = _uiState

    init {
        scope.launch {
            combine(
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
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    override fun processAttendance(urls: List<String>) {
        _isProcessingAttendance.value = true

        scope.launch {
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

    override fun onBackClick() {
        onBack()
    }
}
