package com.gaje48.lms.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _isSplashReady = MutableStateFlow(false)
    val isSplashReady = _isSplashReady.asStateFlow()

    val isLoggedIn = authRepository.isLoggedIn

    fun checkLoginStatus() {
        viewModelScope.launch {
            authRepository.savedCredential()?.let { authRepository.checkLoginStatus(it.first, it.second) }

            _isSplashReady.value = true
        }
    }
}
