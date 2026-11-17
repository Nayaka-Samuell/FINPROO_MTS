package com.ecoloop.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.sessionDataStore by preferencesDataStore(name = "ecoloop_session")
private val TOKEN_KEY = stringPreferencesKey("jwt")

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data object LoggedIn : SessionState
}

/**
 * Menyimpan JWT di DataStore dan menyimpan salinannya di memori.
 *
 * Interceptor OkHttp hanya membaca [token] (field volatile) sehingga tidak pernah
 * memblokir thread jaringan dengan `runBlocking`.
 */
class SessionStore(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    @Volatile
    var token: String? = null
        private set

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Memancarkan event saat sesi diakhiri paksa oleh server (401), bukan logout manual. */
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    init {
        scope.launch {
            token = context.sessionDataStore.data.first()[TOKEN_KEY]
            _state.value = if (token.isNullOrBlank()) SessionState.LoggedOut else SessionState.LoggedIn
        }
    }

    suspend fun save(newToken: String) {
        token = newToken
        context.sessionDataStore.edit { it[TOKEN_KEY] = newToken }
        _state.value = SessionState.LoggedIn
    }

    suspend fun clear() {
        token = null
        _state.value = SessionState.LoggedOut
        context.sessionDataStore.edit { it.remove(TOKEN_KEY) }
    }

    /** Dipanggil dari thread OkHttp ketika server membalas 401 untuk request bertoken. */
    fun expire() {
        if (token == null) return
        token = null
        _state.value = SessionState.LoggedOut
        _expired.tryEmit(Unit)
        scope.launch { context.sessionDataStore.edit { it.remove(TOKEN_KEY) } }
    }
}
