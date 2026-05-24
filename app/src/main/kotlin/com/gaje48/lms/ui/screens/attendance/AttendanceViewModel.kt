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
    val errorMessage: String? = null,
)

class AttendanceViewModel(
    private val courseCode: String,
    private val lmsRepository: LmsRepository,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isProcessingAttendance = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState =
        combine(
            lmsRepository.courses,
            combine(
                lmsRepository.observeAttendances(courseCode),
                lmsRepository.observeAttendanceVmDatas(courseCode),
            ) { attendances, attendanceVmDatas ->
                val attendanceVmDataMap =
                    attendanceVmDatas
                        .groupBy({ it.meetingNumber }, { it.contentUrl })

                attendances.mapIndexed { index, isAttended ->
                    val meetingNumber = index + 1

                    AttendanceScreenData(
                        isAttended = isAttended,
                        contentUrls = attendanceVmDataMap[meetingNumber] ?: emptyList(),
                    )
                }
            },
            _isLoading,
            _isRefreshing,
            _isProcessingAttendance,
        ) { courses, attendanceScreenDatas, isLoading, isRefreshing, isProcessingAttendance ->
            AttendanceUiState(
                courseName = courses.find { it.courseCode == courseCode }?.courseName,
                attendanceScreenDatas = attendanceScreenDatas,
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                isProcessingAttendance = isProcessingAttendance,
                errorMessage = _errorMessage.value,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AttendanceUiState(),
        )

    fun getAttendances(updateAction: UpdateAction = UpdateAction.LOADING) {
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
                val mess = it.message ?: "Gagal memperbarui data"
                if (uiState.value.attendanceScreenDatas.isEmpty()) {
                    _errorMessage.value = mess
                } else {
                    _snackbarEvent.trySend(mess)
                }
            }

            _isRefreshing.value = false
            _isLoading.value = false
        }
    }

    fun processAttendance(urls: List<String>) {
        viewModelScope.launch {
            _isProcessingAttendance.value = true

            lmsRepository
                .executeAttendances(urls)
                .onFailure {
                    _snackbarEvent.trySend(it.message ?: "Gagal melakukan absensi")
                }

            lmsRepository.syncAll().onFailure {
                _snackbarEvent.trySend(it.message ?: "Gagal memperbarui data")
            }

            _isProcessingAttendance.value = false
        }
    }
}
