package com.gaje48.lms.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.model.AuthStatus
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.lms_rust.LmsException
import kotlin.time.Duration.Companion.milliseconds

data class LoginUiState(
    val status: AuthStatus = AuthStatus.IDLE,
    val errorMessage: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val courseRepository: CourseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message, status = AuthStatus.IDLE) }
    }

    fun resetError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun manualLogin(nim: String, pwd: String) {
        _uiState.update { it.copy(status = AuthStatus.LOADING, errorMessage = null) }

        viewModelScope.launch {
            authRepository.login(nim, pwd).onErr { throwable ->
                val friendlyMessage =
                    when (throwable) {
                        is LmsException.InvalidCredentialsException -> "NIM atau Password salah"
                        is LmsException.InvalidCaptchaException -> "Jawaban Captcha salah"
                        else -> throwable.message ?: "Terjadi kesalahan saat login"
                    }
                setError(friendlyMessage)
                return@launch
            }

            courseRepository.syncAll().onErr {
                setError(it.message ?: "Gagal memuat data akademik")
                return@launch
            }

            _uiState.update { it.copy(status = AuthStatus.SUCCESS) }
            delay(500.milliseconds)

            authRepository.saveCredentials(nim, pwd)
        }
    }

    fun requestResetPassword(email: String) {
        _uiState.update { it.copy(status = AuthStatus.LOADING, errorMessage = null) }

        viewModelScope.launch {
            authRepository.requestResetPassword(email).onErr { throwable ->
                val friendlyMessage =
                    when (throwable) {
                        is LmsException.InvalidCaptchaException -> "Jawaban Captcha salah, silakan coba lagi"
                        is LmsException.UnregisteredEmailException -> "Email Anda belum terdaftar"
                        else -> throwable.message ?: "Terjadi kesalahan saat memproses reset password"
                    }
                _uiState.update { it.copy(status = AuthStatus.IDLE, errorMessage = friendlyMessage) }

                return@launch
            }

            _uiState.update { it.copy(status = AuthStatus.SUCCESS) }
        }
    }
}
