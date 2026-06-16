package com.gaje48.lms.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.model.AuthStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class LoginUiState(
    val status: AuthStatus = AuthStatus.IDLE,
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
                status = AuthStatus.IDLE,
            )
        }
    }

    fun resetError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun manualLogin(
        nim: String,
        pwd: String,
    ) {
        _uiState.update { it.copy(status = AuthStatus.LOADING, errorMessage = null) }

        viewModelScope.launch {
            val authResult = authRepository.login(nim, pwd)
            authResult.onFailure { exception ->
                val friendlyMessage =
                    when (exception) {
                        is uniffi.lms_rust.LmsException.CredentialException -> "NIM atau Password salah"
                        is uniffi.lms_rust.LmsException.CaptchaException -> "Jawaban Captcha salah"
                        else -> exception.message ?: "Terjadi kesalahan saat login"
                    }
                setError(friendlyMessage)
                return@launch
            }

            val lmsResult = lmsRepository.login()
            lmsResult.onFailure {
                setError(it.message ?: "Gagal memuat data akademik")
                return@launch
            }

            _uiState.update { it.copy(status = AuthStatus.SUCCESS) }
            delay(500.milliseconds)

            authRepository.saveCredentials(nim, pwd)
        }
    }

    fun requestResetPassword(email: String) {
        _uiState.update {
            it.copy(
                status = AuthStatus.LOADING,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            val result = authRepository.requestResetPassword(email)
            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.SUCCESS,
                        )
                    }
                }.onFailure { exception ->
                    val friendlyMessage =
                        when (exception) {
                            is uniffi.lms_rust.LmsException.CaptchaException -> "Jawaban Captcha salah, silakan coba lagi"
                            is uniffi.lms_rust.LmsException.EmailNotRegisteredException -> "Email Anda belum terdaftar"
                            else -> exception.message ?: "Terjadi kesalahan saat memproses reset password"
                        }
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.IDLE,
                            errorMessage = friendlyMessage,
                        )
                    }
                }
        }
    }
}
