package com.gaje48.lms.ui.screens.meeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Meeting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeetingUiState(
    val course: Course? = null,
    val meetings: List<Meeting> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

class MeetingViewModel(
    private val courseCode: String,
    private val lmsRepository: LmsRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MeetingUiState> = combine(
        lmsRepository.courses,
        lmsRepository.observeMeetings(courseCode),
        _isRefreshing,
        _errorMessage
    ) { courses, meetings, isRefreshing, errorMessage ->
        MeetingUiState(
            course = courses.find { it.courseCode == courseCode },
            meetings = meetings,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MeetingUiState()
    )

    fun refreshDashboard() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null

            lmsRepository.syncAll().onFailure { e ->
                e.message?.let {
                    if (uiState.value.meetings.isEmpty()) _errorMessage.value = it
                    else _errorMessage.value = it
                }
            }
            _isRefreshing.value = false
        }
    }
}
