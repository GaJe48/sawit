package com.gaje48.lms.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AssignmentRepository
import com.gaje48.lms.data.AttendanceRepository
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Student
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DashboardUiState(
    val student: Student? = null,
    val courses: List<Course> = emptyList(),
    val allPresences: List<AttendancesByCourse> = emptyList(),
    val unsubmittedCounts: Map<String, Int> = emptyMap(),
    val lastSyncText: String = "Belum pernah sinkron",
)

class DashboardViewModel(
    private val authRepository: AuthRepository,
    courseRepository: CourseRepository,
    attendanceRepository: AttendanceRepository,
    assignmentRepository: AssignmentRepository,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    val uiState = combine(
        courseRepository.student,
        courseRepository.courses,
        attendanceRepository.allAttendances,
        assignmentRepository.unsubmittedAssignmentCounts,
        courseRepository.lastSyncTime,
    ) { student, courses, attendances, unsubmittedCounts, lastSyncTime ->
        DashboardUiState(
            student = student,
            courses = courses,
            allPresences = attendances,
            unsubmittedCounts = unsubmittedCounts,
            lastSyncText = formatLastSyncTime(lastSyncTime),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(),
    )

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private fun formatLastSyncTime(timestamp: Long): String {
        if (timestamp == 0L) return "Belum pernah sinkron"
        val dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.forLanguageTag("id-ID"))
        return "Terakhir sinkron: ${dateTime.format(formatter)}"
    }
}
