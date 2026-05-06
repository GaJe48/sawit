package com.gaje48.lms.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.Student
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val student: Student? = null,
    val allCourse: List<Course> = emptyList(),
    val allPresences: List<AttendancesByCourse> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(private val lmsRepository: LmsRepository) : ViewModel() {

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        lmsRepository.student,
        lmsRepository.courses,
        lmsRepository.allAttendances,
        _isRefreshing,
        _errorMessage
    ) { student, courses, attendances, isRefreshing, errorMessage ->
        DashboardUiState(
            student = student,
            allCourse = courses,
            allPresences = attendances,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun refreshDashboard() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null

            lmsRepository.syncAll().onFailure { e ->
                e.message?.let {
                    if (uiState.value.allCourse.isEmpty()) _errorMessage.value = it
                    else _snackbarEvent.trySend(it)
                }
            }

            _isRefreshing.value = false
        }
    }

    fun logout() {
        viewModelScope.launch { lmsRepository.clearCredential() }
    }
}
