package com.gaje48.lms.ui.screens.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.AttendanceScreenData
import com.gaje48.lms.model.UpdateAction
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
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

class AttendanceViewModel(
    private val courseCode: String,
    private val lmsRepository: LmsRepository
) : ViewModel() {

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isProcessingAttendance = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        lmsRepository.courses,
        combine(
            lmsRepository.observeAttendances(courseCode),
            lmsRepository.observeAttendanceVmDatas(courseCode)
        ) { attendances, attendanceVmDatas ->
            val attendanceVmDataMap = attendanceVmDatas.associate { it.meetingNumber to it.contentUrl }

            attendances.mapIndexed { index, isAttended ->
                val meetingNumber = index + 1

                AttendanceScreenData(
                    isAttended = isAttended,
                    contentUrl = attendanceVmDataMap[meetingNumber]
                )
            }
        },
        _isLoading,
        _isRefreshing,
        _isProcessingAttendance
    ) { courses, attendanceScreenDatas, isLoading, isRefreshing, isProcessingAttendance ->
        AttendanceUiState(
            courseName = courses.find { it.courseCode == courseCode }?.courseName,
            attendanceScreenDatas = attendanceScreenDatas,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            isProcessingAttendance = isProcessingAttendance,
            errorMessage = _errorMessage.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AttendanceUiState()
    )

    fun getAttendances(updateAction: UpdateAction = UpdateAction.LOADING) {
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
                    if (uiState.value.attendanceScreenDatas.isEmpty()) _errorMessage.value = it
                    else _snackbarEvent.trySend(it)
                }
            }

            _isRefreshing.value = false
            _isLoading.value = false
        }
    }

    fun processAttendance(url: String) {
        viewModelScope.launch {
            _isProcessingAttendance.value = true

            lmsRepository.executeAttendance(url).onFailure { e ->
                e.message?.let { _snackbarEvent.trySend(it) }
            }

            lmsRepository.syncAll().onFailure { e ->
                e.message?.let { _snackbarEvent.trySend(it) }
            }

            _isProcessingAttendance.value = false
        }
    }
}