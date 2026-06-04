package com.gaje48.lms.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Student
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val student: Student? = null,
    val courses: List<Course> = emptyList(),
    val allPresences: List<AttendancesByCourse> = emptyList(),
)

class DashboardViewModel(
    private val authRepository: AuthRepository,
    lmsRepository: LmsRepository,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    val uiState =
        combine(
            lmsRepository.student,
            lmsRepository.courses,
            lmsRepository.allAttendances,
        ) { student, courses, attendances ->
            DashboardUiState(
                student = student,
                courses = courses,
                allPresences = attendances,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState(),
        )

    fun logout() {
        viewModelScope.launch { authRepository.clearCredential() }
    }
}
