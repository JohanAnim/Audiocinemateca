package com.johang.audiocinemateca.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.domain.model.LoginResult
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.domain.usecase.LoadCatalogUseCase
import com.johang.audiocinemateca.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.media.SoundEffectsPlayer
import com.johang.audiocinemateca.media.VoicePlayer
import kotlinx.coroutines.delay

sealed class LoginErrorEvent {
    object InvalidCredentials : LoginErrorEvent()
    object ServerError : LoginErrorEvent()
    object MissingUsername : LoginErrorEvent()
    object MissingPassword : LoginErrorEvent()
    data class Unknown(val message: String) : LoginErrorEvent()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val loadCatalogUseCase: LoadCatalogUseCase,
    private val soundEffectsPlayer: SoundEffectsPlayer,
    private val voicePlayer: VoicePlayer
) : ViewModel() {

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _banMessage = MutableStateFlow<String?>(null)
    val banMessage: StateFlow<String?> = _banMessage

    private val _loginSuccess = MutableStateFlow<Boolean>(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess

    private val _catalogDownloadState = MutableStateFlow<AuthCatalogRepository.LoadCatalogResultWithProgress?>(null)
    val catalogDownloadState: StateFlow<AuthCatalogRepository.LoadCatalogResultWithProgress?> = _catalogDownloadState

    private val _isUserLoggedIn = MutableStateFlow<Boolean>(false)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn

    private val _showErrorAlert = MutableSharedFlow<LoginErrorEvent>()
    val showErrorAlert: SharedFlow<LoginErrorEvent> = _showErrorAlert

    init {
        checkBanStatus()
        viewModelScope.launch {
            _isUserLoggedIn.value = loginUseCase.isUserLoggedIn()
        }
    }

    fun onCatalogDownloadStart() {
        voicePlayer.playVoice(R.raw.voz_descargando)
        viewModelScope.launch {
            loadCatalogUseCase.execute().collect {
                _catalogDownloadState.value = it
                if (it is AuthCatalogRepository.LoadCatalogResultWithProgress.Success) {
                    voicePlayer.playVoice(R.raw.voz_descarga_lista)
                }
            }
        }
    }

    fun onUsernameChange(newUsername: String) {
        _username.value = newUsername
        _errorMessage.value = null // Clear error when user types
    }

    fun onPasswordChange(newPassword: String) {
        _password.value = newPassword
        _errorMessage.value = null // Clear error when user types
    }

    fun checkBanStatus() {
        if (loginUseCase.isBanned()) {
            _banMessage.value = "Usted ha sido baneado del servidor. Le quedan ${loginUseCase.getRemainingBanTime()} para poder intentar de nuevo."
        } else {
            _banMessage.value = null
        }
    }

    fun onLoginClick() {
        viewModelScope.launch {
            _errorMessage.value = null
            checkBanStatus()

            if (loginUseCase.isBanned()) {
                return@launch
            }

            if (_username.value.trim().isEmpty()) {
                _showErrorAlert.emit(LoginErrorEvent.MissingUsername)
                return@launch
            }

            if (_password.value.trim().isEmpty()) {
                _showErrorAlert.emit(LoginErrorEvent.MissingPassword)
                return@launch
            }

            when (val result = loginUseCase.execute(_username.value, _password.value)) {
                is LoginResult.Success -> {
                    _loginSuccess.value = true
                    _isUserLoggedIn.value = true
                    _banMessage.value = null
                }
                is LoginResult.Error -> {
                    checkBanStatus()
                    when (result.statusCode) {
                        401, 403 -> _showErrorAlert.emit(LoginErrorEvent.InvalidCredentials)
                        in 500..599 -> _showErrorAlert.emit(LoginErrorEvent.ServerError)
                        else -> _showErrorAlert.emit(LoginErrorEvent.Unknown(result.message))
                    }
                }
                is LoginResult.Banned -> {
                    _errorMessage.value = "Demasiados intentos fallidos. Intente de nuevo más tarde."
                    checkBanStatus()
                }
            }
        }
    }

    fun playWelcomeSounds() {
        viewModelScope.launch {
            soundEffectsPlayer.loadSound(R.raw.efecto_bienvenida) { resId ->
                soundEffectsPlayer.playSound(resId, 0.5f) // Volumen a la mitad
                viewModelScope.launch {
                    delay(500)
                    voicePlayer.playVoice(R.raw.voz_bienvenida) {
                        // Se ejecuta al completar la primera voz
                        viewModelScope.launch {
                            delay(1000) // Retraso de 1 segundo
                            voicePlayer.playVoice(R.raw.voz_instrucciones_login)
                        }
                    }
                }
            }
        }
    }

    fun stopCurrentVoice() {
        voicePlayer.stopVoice()
    }
}