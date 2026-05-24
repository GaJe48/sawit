package com.gaje48.lms.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.LmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val lmsRepository: LmsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private fun setError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                isLoading = false,
            )
        }
    }

    fun manualLogin(
        nim: String,
        pwd: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository
                .login(nim, pwd)
                .onSuccess {
                    lmsRepository
                        .firstLogin()
                        .onSuccess {
                            authRepository.saveCredentials(nim, pwd)
                            _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                        }.onFailure {
                            setError(it.message ?: "Gagal memuat data akademik")
                        }
                }.onFailure { exception ->
                    val friendlyMessage =
                        when (exception) {
                            is uniffi.lms_rust.LmsException.CredentialException -> "NIM atau Password salah"
                            is uniffi.lms_rust.LmsException.CaptchaException -> "Jawaban Captcha salah"
                            else -> exception.message ?: "Terjadi kesalahan saat login"
                        }
                    setError(friendlyMessage)
                }
        }
    }
}
