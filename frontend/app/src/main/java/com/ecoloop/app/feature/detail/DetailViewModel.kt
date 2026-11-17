package com.ecoloop.app.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ecoloop.app.appContainer
import com.ecoloop.app.data.remote.dto.ListingDto
import com.ecoloop.app.data.repository.AuthRepository
import com.ecoloop.app.data.repository.CatalogRepository
import com.ecoloop.app.data.repository.ReservationRepository
import com.ecoloop.app.data.repository.toUserMessage
import com.ecoloop.app.ui.UiState
import com.ecoloop.app.ui.dataOrNull
import com.ecoloop.app.ui.navigation.Routes
import com.ecoloop.app.ui.validateWeight
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReserveForm(
    val visible: Boolean = false,
    val input: String = "",
    val showError: Boolean = false,
    val submitting: Boolean = false,
)

data class DetailUiState(
    val listing: UiState<ListingDto> = UiState.Loading,
    val currentUserId: String? = null,
    val form: ReserveForm = ReserveForm(),
) {
    val isOwnListing: Boolean
        get() = listing.dataOrNull?.supplierId != null && listing.dataOrNull?.supplierId == currentUserId

    val weightError: String?
        get() = validateWeight(form.input, listing.dataOrNull?.availableKg).error
}

class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val reservations: ReservationRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val listingId: String = checkNotNull(savedStateHandle[Routes.DETAIL_ARG_ID])

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            auth.user.collect { user -> _state.update { it.copy(currentUserId = user?.id) } }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_state.value.listing !is UiState.Success) _state.update { it.copy(listing = UiState.Loading) }
            val result = try {
                UiState.Success(catalog.listing(listingId))
            } catch (e: Exception) {
                UiState.Error(e.toUserMessage())
            }
            _state.update { it.copy(listing = result) }
        }
    }

    fun openReserveForm() = _state.update { it.copy(form = ReserveForm(visible = true)) }

    fun dismissReserveForm() = _state.update {
        if (it.form.submitting) it else it.copy(form = ReserveForm())
    }

    fun onWeightChange(value: String) = _state.update { it.copy(form = it.form.copy(input = value)) }

    fun submitReservation() {
        val current = _state.value
        val listing = current.listing.dataOrNull ?: return
        val check = validateWeight(current.form.input, listing.availableKg)
        val kg = check.value
        if (kg == null) {
            _state.update { it.copy(form = it.form.copy(showError = true)) }
            return
        }
        _state.update { it.copy(form = it.form.copy(submitting = true)) }
        viewModelScope.launch {
            try {
                reservations.reserve(listing.id, kg)
                _state.update { it.copy(form = ReserveForm()) }
                _messages.send("Reservasi diajukan. Pantau statusnya di tab Aktivitas.")
                load()
            } catch (e: Exception) {
                _state.update { it.copy(form = it.form.copy(submitting = false)) }
                _messages.send(e.toUserMessage())
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                DetailViewModel(
                    createSavedStateHandle(),
                    container.catalogRepository,
                    container.reservationRepository,
                    container.authRepository,
                )
            }
        }
    }
}
