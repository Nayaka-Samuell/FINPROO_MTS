package com.ecoloop.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ecoloop.app.appContainer
import com.ecoloop.app.data.repository.AuthRepository
import com.ecoloop.app.data.repository.DISTRICTS
import com.ecoloop.app.data.repository.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isRegister: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val district: String = DISTRICTS.first(),
    val showErrors: Boolean = false,
    val submitting: Boolean = false,
    val serverError: String? = null,
) {
    val nameError: String?
        get() = if (isRegister && name.isBlank()) "Nama wajib diisi" else null

    val emailError: String?
        get() = when {
            email.isBlank() -> "Email wajib diisi"
            !EMAIL_REGEX.matches(email.trim()) -> "Format email tidak valid"
            else -> null
        }

    val passwordError: String?
        get() = when {
            password.isEmpty() -> "Password wajib diisi"
            isRegister && password.length < 8 -> "Minimal 8 karakter"
            else -> null
        }

    val isValid: Boolean get() = nameError == null && emailError == null && passwordError == null

    private companion object {
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, serverError = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, serverError = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, serverError = null) }
    fun onDistrictChange(value: String) = _state.update { it.copy(district = value) }

    fun toggleMode() = _state.update {
        it.copy(isRegister = !it.isRegister, showErrors = false, serverError = null)
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        if (!current.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(submitting = true, serverError = null) }
        viewModelScope.launch {
            try {
                if (current.isRegister) {
                    auth.register(current.name, current.email, current.password, current.district)
                } else {
                    auth.login(current.email, current.password)
                }
                // SessionStore berpindah ke LoggedIn; bersihkan password dari memori form.
                _state.value = AuthUiState(email = current.email.trim())
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, serverError = e.toUserMessage()) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(appContainer.authRepository) }
        }
    }
}
