package com.ecoloop.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecoloop.app.data.local.SessionState
import com.ecoloop.app.data.local.SessionStore
import com.ecoloop.app.feature.auth.AuthScreen
import com.ecoloop.app.ui.components.LocalMessenger
import com.ecoloop.app.ui.components.Messenger
import com.ecoloop.app.ui.navigation.EcoLoopApp

/** Memilih layar berdasarkan status sesi: splash → login → aplikasi utama. */
@Composable
fun EcoLoopRoot(session: SessionStore) {
    val state by session.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember { Messenger(snackbarHostState, scope) }

    LaunchedEffect(session) {
        session.expired.collect { messenger.show("Sesi berakhir. Silakan masuk kembali.") }
    }

    CompositionLocalProvider(LocalMessenger provides messenger) {
        when (state) {
            SessionState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            SessionState.LoggedOut -> AuthScreen(snackbarHostState)
            SessionState.LoggedIn -> EcoLoopApp(snackbarHostState)
        }
    }
}
