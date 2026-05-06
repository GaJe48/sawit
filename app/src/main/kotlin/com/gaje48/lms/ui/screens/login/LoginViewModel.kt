package com.gaje48.lms.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.LmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isSplashReady: Boolean = false,
    val isLoading: Boolean = false,
    val isAutoLoginLoading: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val lmsRepository: LmsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    val isLoggedIn = lmsRepository.isLoggedIn

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message, isLoading = false, isAutoLoginLoading = false) }
    }

    fun checkLoginStatus() {
        viewModelScope.launch {
            val credentials = lmsRepository.savedCredential() ?: run {
                _uiState.update { it.copy(isSplashReady = true) }
                return@launch
            }

            val (username, password) = credentials
            lmsRepository.checkLoginStatus(username, password)
                .onSuccess { _uiState.update { it.copy(errorMessage = null) } }
                .onFailure { e -> e.message?.let { setError(it) } }

            _uiState.update { it.copy(isSplashReady = true) }
        }
    }

    fun manualLogin(nim: String, pwd: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            lmsRepository.login(nim, pwd)
                .onSuccess { _uiState.update { it.copy(isLoading = false, errorMessage = null) } }
                .onFailure { e -> e.message?.let { setError(it) } }
        }
    }
}
