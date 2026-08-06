package com.gaje48.lms.ui.screens.login

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.model.AuthStatus
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uniffi.lms_rust.LmsException
import kotlin.time.Duration.Companion.milliseconds

data class LoginUiState(
    val status: AuthStatus = AuthStatus.IDLE,
    val errorMessage: String? = null,
)

interface LoginComponent {
    val uiState: Value<LoginUiState>

    fun resetError()
    fun manualLogin(nim: String, pwd: String)
    fun requestResetPassword(email: String)
}

class DefaultLoginComponent(
    componentContext: ComponentContext,
) : LoginComponent, ComponentContext by componentContext, KoinComponent {
    private val authRepository: AuthRepository by inject()
    private val courseRepository: CourseRepository by inject()

    private val scope = coroutineScope()

    private val _uiState = MutableValue(LoginUiState())
    override val uiState: Value<LoginUiState> = _uiState

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message, status = AuthStatus.IDLE) }
    }

    override fun resetError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun manualLogin(nim: String, pwd: String) {
        _uiState.update { it.copy(status = AuthStatus.LOADING, errorMessage = null) }

        scope.launch {
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

    override fun requestResetPassword(email: String) {
        _uiState.update { it.copy(status = AuthStatus.LOADING, errorMessage = null) }

        scope.launch {
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
