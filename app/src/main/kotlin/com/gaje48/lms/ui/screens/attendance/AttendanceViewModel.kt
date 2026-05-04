package com.gaje48.lms.ui.screens.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.LoadMode
import com.gaje48.lms.model.StatusPresensi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val courseName: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPresenceSubmitting: Boolean = false,
    val allPresenceStatus: List<StatusPresensi> = emptyList(),
    val errorMessage: String? = null
)

class AttendanceViewModel(
    private val courseCode: String,
    private val lmsRepository: LmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState = _uiState.asStateFlow()

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    init { getAttendances() }

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun emitSnackbar(message: String) {
        _snackbarEvent.trySend(message)
    }

    fun getAttendances(loadMode: LoadMode = LoadMode.LOADING) {
        val dashboardData = lmsRepository.dashboardData.value ?: return
        val presence = dashboardData.allPresences.find { it.courseCode == courseCode }?.attendances ?: emptyList()
        val allMeeting = dashboardData.allMeetings.find { it.courseCode == courseCode }?.meetings ?: emptyList()
        val courseName = dashboardData.courses.find { it.courseCode == courseCode }?.courseName

        viewModelScope.launch {
            when (loadMode) {
                LoadMode.REFRESH -> _uiState.update { it.copy(isRefreshing = true, errorMessage = null, courseName = courseName) }
                LoadMode.LOADING -> _uiState.update {
                    it.copy(isLoading = true, errorMessage = null, allPresenceStatus = emptyList(), courseName = courseName)
                }
            }

            lmsRepository.fetchPresenceDetail(allMeeting, presence)
                .onSuccess { statusList -> 
                    _uiState.update { it.copy(allPresenceStatus = statusList) } 
                }
                .onFailure { e ->
                    e.message?.let {
                        if (_uiState.value.allPresenceStatus.isEmpty()) setError(it)
                        else emitSnackbar(it)
                    }
                }

            _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
        }
    }

    fun submitPresence(url: String) {
        val dashboardData = lmsRepository.dashboardData.value ?: return
        val presence = dashboardData.allPresences.find { it.courseCode == courseCode }?.attendances ?: emptyList()
        val allMeeting = dashboardData.allMeetings.find { it.courseCode == courseCode }?.meetings ?: emptyList()

        viewModelScope.launch {
            _uiState.update { it.copy(isPresenceSubmitting = true) }

            lmsRepository.executePresence(url).onFailure { e -> e.message?.let { emitSnackbar(it) } }

            lmsRepository.fetchPresenceDetail(allMeeting, presence)
                .onSuccess { statusList -> 
                    _uiState.update { it.copy(allPresenceStatus = statusList) } 
                }
                .onFailure { e -> e.message?.let { emitSnackbar(it) } }

            _uiState.update { it.copy(isPresenceSubmitting = false) }
        }
    }
}
