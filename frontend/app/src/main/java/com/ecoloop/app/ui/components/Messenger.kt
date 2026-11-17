package com.ecoloop.app.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Menampilkan snackbar dari scope level aplikasi, sehingga pesan tetap tampil
 * walaupun layar asalnya sudah ditutup (mis. setelah tambah listing → kembali ke Beranda).
 */
class Messenger(
    private val host: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun show(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message)
        }
    }
}

val LocalMessenger = staticCompositionLocalOf<Messenger> {
    error("Messenger belum disediakan")
}

/** Meneruskan pesan satu-kali dari ViewModel ke snackbar. */
@Composable
fun CollectMessages(messages: Flow<String>) {
    val messenger = LocalMessenger.current
    LaunchedEffect(messages) {
        messages.collect { messenger.show(it) }
    }
}
