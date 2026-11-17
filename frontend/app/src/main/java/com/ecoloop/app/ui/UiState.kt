package com.ecoloop.app.ui

/** State standar untuk setiap data yang dimuat dari server. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

val <T> UiState<T>.dataOrNull: T?
    get() = (this as? UiState.Success)?.data
