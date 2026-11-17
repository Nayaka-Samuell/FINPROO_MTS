package com.ecoloop.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.ecoloop.app.data.local.SessionStore
import com.ecoloop.app.data.remote.ApiClient
import com.ecoloop.app.data.repository.AuthRepository
import com.ecoloop.app.data.repository.CatalogRepository
import com.ecoloop.app.data.repository.DataChanges
import com.ecoloop.app.data.repository.ReservationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Dependency container sederhana; satu instance per proses aplikasi. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val session = SessionStore(context.applicationContext, appScope)
    private val api = ApiClient.create(session)
    private val changes = DataChanges()

    val dataChanges: DataChanges get() = changes
    val authRepository = AuthRepository(api, session)
    val catalogRepository = CatalogRepository(api, changes)
    val reservationRepository = ReservationRepository(api, changes)
}

/** Dipakai di blok `initializer { }` ViewModel factory. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as EcoLoopApplication).container
