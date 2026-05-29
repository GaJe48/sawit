package com.gaje48.lms.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Student
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val student: Student? = null,
    val courses: List<Course> = emptyList(),
    val allPresences: List<AttendancesByCourse> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val lmsRepository: LmsRepository,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState =
        combine(
            lmsRepository.student,
            lmsRepository.courses,
            lmsRepository.allAttendances,
            _isRefreshing,
            _errorMessage,
        ) { student, courses, attendances, isRefreshing, errorMessage ->
            DashboardUiState(
                student = student,
                courses = courses,
                allPresences = attendances,
                isRefreshing = isRefreshing,
                errorMessage = errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState(),
        )

    fun refreshDashboard() {
        _isRefreshing.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            lmsRepository.syncAll().onFailure {
                val msg = it.message ?: "Gagal memperbarui data"
                if (uiState.value.courses.isEmpty()) {
                    _errorMessage.value = msg
                } else {
                    _snackbarEvent.trySend(msg)
                }
            }

            _isRefreshing.value = false
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.clearCredential() }
    }
}
