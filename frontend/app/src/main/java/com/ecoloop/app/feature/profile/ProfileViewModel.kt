package com.ecoloop.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ecoloop.app.appContainer
import com.ecoloop.app.data.remote.dto.User
import com.ecoloop.app.data.repository.AuthRepository
import com.ecoloop.app.data.repository.toUserMessage
import com.ecoloop.app.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _user = MutableStateFlow<UiState<User>>(
        auth.user.value?.let { UiState.Success(it) } ?: UiState.Loading,
    )
    val user: StateFlow<UiState<User>> = _user.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _user.value = try {
                UiState.Success(auth.refreshUser())
            } catch (e: Exception) {
                auth.user.value?.let { UiState.Success(it) } ?: UiState.Error(e.toUserMessage())
            }
        }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(appContainer.authRepository) }
        }
    }
}
