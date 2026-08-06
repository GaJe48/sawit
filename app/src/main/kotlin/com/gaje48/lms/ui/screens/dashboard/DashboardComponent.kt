package com.gaje48.lms.ui.screens.dashboard

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.gaje48.lms.data.AssignmentRepository
import com.gaje48.lms.data.AttendanceRepository
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Student
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
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

interface DashboardComponent {
    val uiState: Value<DashboardUiState>
    val snackbarEvent: Flow<String>

    fun logout()

    fun onCourseClick(courseCode: String)

    fun onAttendanceClick(courseCode: String)

    fun onAssignmentClick(courseCode: String)
}

class DefaultDashboardComponent(
    componentContext: ComponentContext,
    private val onNavigateToMeeting: (courseCode: String) -> Unit,
    private val onNavigateToAttendance: (courseCode: String) -> Unit,
    private val onNavigateToAssignment: (courseCode: String) -> Unit,
) : DashboardComponent, ComponentContext by componentContext, KoinComponent {
    private val authRepository: AuthRepository = get()
    private val courseRepository: CourseRepository = get()
    private val attendanceRepository: AttendanceRepository = get()
    private val assignmentRepository: AssignmentRepository = get()

    private val scope = coroutineScope()

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    override val snackbarEvent: Flow<String> = _snackbarEvent.receiveAsFlow()

    private val _uiState = MutableValue(DashboardUiState())
    override val uiState: Value<DashboardUiState> = _uiState

    init {
        scope.launch {
            combine(
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
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    override fun logout() {
        scope.launch { authRepository.logout() }
    }

    override fun onCourseClick(courseCode: String) {
        onNavigateToMeeting(courseCode)
    }

    override fun onAttendanceClick(courseCode: String) {
        onNavigateToAttendance(courseCode)
    }

    override fun onAssignmentClick(courseCode: String) {
        onNavigateToAssignment(courseCode)
    }

    private fun formatLastSyncTime(timestamp: Long): String {
        if (timestamp == 0L) return "Belum pernah sinkron"
        val dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.forLanguageTag("id-ID"))
        return "Terakhir sinkron: ${dateTime.format(formatter)}"
    }
}
