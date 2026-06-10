package com.gaje48.lms.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.LmsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val authRepository: AuthRepository,
    private val lmsRepository: LmsRepository,
) : ViewModel() {
    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private val _isSplashReady = MutableStateFlow(false)
    val isSplashReady = _isSplashReady.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val isLoggedIn =
        authRepository.isLoggedIn.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    fun checkLoginStatus() {
        viewModelScope.launch {
            authRepository.savedCredential()?.let { authRepository.checkLoginStatus(it.first, it.second) }

            _isSplashReady.value = true
        }
    }

    fun refresh() {
        _isRefreshing.value = true

        viewModelScope.launch {
            lmsRepository.syncAll().onFailure {
                _snackbarEvent.send(it.message ?: "Failed to refresh data")
            }

            _isRefreshing.value = false
        }
    }
}
