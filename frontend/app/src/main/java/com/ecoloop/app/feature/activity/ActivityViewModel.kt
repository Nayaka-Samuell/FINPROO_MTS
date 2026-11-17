package com.ecoloop.app.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ecoloop.app.appContainer
import com.ecoloop.app.data.remote.dto.ReservationDto
import com.ecoloop.app.data.repository.DataChanges
import com.ecoloop.app.data.repository.ReservationRepository
import com.ecoloop.app.data.repository.toUserMessage
import com.ecoloop.app.ui.UiState
import com.ecoloop.app.ui.validateWeight
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActivityTab(val label: String) {
    INCOMING("Masuk"),
    OUTGOING("Keluar"),
}

/** QR serah terima yang ditampilkan penerima. */
data class HandoverQr(val reservationId: String, val token: String, val expiresAt: String)

/** Form penyelesaian serah terima oleh penyedia (hasil scan atau input manual). */
data class CompleteForm(
    val reservation: ReservationDto,
    val token: String = "",
    val weight: String = "",
    val showErrors: Boolean = false,
    val submitting: Boolean = false,
) {
    val tokenError: String? get() = if (token.isBlank()) "Token QR wajib diisi" else null
    val weightError: String? get() = validateWeight(weight).error
}

data class ActivityUiState(
    val tab: ActivityTab = ActivityTab.INCOMING,
    val incoming: UiState<List<ReservationDto>> = UiState.Loading,
    val outgoing: UiState<List<ReservationDto>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val busyIds: Set<String> = emptySet(),
    val handoverQr: HandoverQr? = null,
    val scanningFor: ReservationDto? = null,
    val completeForm: CompleteForm? = null,
    val confirmCancel: ReservationDto? = null,
) {
    val current: UiState<List<ReservationDto>>
        get() = if (tab == ActivityTab.INCOMING) incoming else outgoing
}

class ActivityViewModel(
    private val reservations: ReservationRepository,
    changes: DataChanges,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        load()
        viewModelScope.launch { changes.events.collect { load() } }
    }

    fun selectTab(tab: ActivityTab) = _state.update { it.copy(tab = tab) }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val incoming = async { result { reservations.incoming() } }
            val outgoing = async { result { reservations.outgoing() } }
            val newIncoming = incoming.await()
            val newOutgoing = outgoing.await()
            _state.update {
                it.copy(
                    incoming = newIncoming.keepDataOnError(it.incoming),
                    outgoing = newOutgoing.keepDataOnError(it.outgoing),
                    isRefreshing = false,
                )
            }
            (newIncoming as? UiState.Error ?: newOutgoing as? UiState.Error)?.let { error ->
                if (_state.value.current is UiState.Success) _messages.send(error.message)
            }
        }
    }

    // ---- Aksi penyedia ----

    fun approve(r: ReservationDto) = mutate(r.id, "Reservasi disetujui. Stok sudah ditahan.") {
        reservations.approve(r.id)
    }

    fun reject(r: ReservationDto) = mutate(r.id, "Reservasi ditolak.") {
        reservations.reject(r.id)
    }

    fun startScan(r: ReservationDto) = _state.update { it.copy(scanningFor = r) }

    fun dismissScan() = _state.update { it.copy(scanningFor = null) }

    /** Token terbaca dari kamera → buka form berat aktual secara otomatis. */
    fun onTokenScanned(token: String) = _state.update {
        val reservation = it.scanningFor ?: return@update it
        it.copy(scanningFor = null, completeForm = CompleteForm(reservation, token = token.trim()))
    }

    /** Cadangan bila kamera tidak tersedia. */
    fun openManualEntry(r: ReservationDto) = _state.update {
        it.copy(scanningFor = null, completeForm = CompleteForm(r))
    }

    fun onCompleteTokenChange(value: String) = _state.update {
        it.copy(completeForm = it.completeForm?.copy(token = value))
    }

    fun onCompleteWeightChange(value: String) = _state.update {
        it.copy(completeForm = it.completeForm?.copy(weight = value))
    }

    fun dismissCompleteForm() = _state.update {
        if (it.completeForm?.submitting == true) it else it.copy(completeForm = null)
    }

    fun submitComplete() {
        val form = _state.value.completeForm ?: return
        val kg = validateWeight(form.weight).value
        if (form.tokenError != null || kg == null) {
            _state.update { it.copy(completeForm = form.copy(showErrors = true)) }
            return
        }
        _state.update { it.copy(completeForm = form.copy(submitting = true)) }
        viewModelScope.launch {
            try {
                reservations.complete(form.reservation.id, form.token, kg)
                _state.update { it.copy(completeForm = null) }
                _messages.send("Serah terima selesai. Dampak EcoLoop diperbarui.")
            } catch (e: Exception) {
                _state.update { it.copy(completeForm = it.completeForm?.copy(submitting = false)) }
                _messages.send(e.toUserMessage())
            }
        }
    }

    // ---- Aksi penerima ----

    fun showHandoverQr(r: ReservationDto) {
        if (r.id in _state.value.busyIds) return
        _state.update { it.copy(busyIds = it.busyIds + r.id) }
        viewModelScope.launch {
            try {
                val token = reservations.handoverToken(r.id)
                _state.update { it.copy(handoverQr = HandoverQr(r.id, token.token, token.expiresAt)) }
            } catch (e: Exception) {
                _messages.send(e.toUserMessage())
            } finally {
                _state.update { it.copy(busyIds = it.busyIds - r.id) }
            }
        }
    }

    fun dismissHandoverQr() = _state.update { it.copy(handoverQr = null) }

    fun askCancel(r: ReservationDto) = _state.update { it.copy(confirmCancel = r) }

    fun dismissCancel() = _state.update { it.copy(confirmCancel = null) }

    fun confirmCancel() {
        val r = _state.value.confirmCancel ?: return
        _state.update { it.copy(confirmCancel = null) }
        mutate(r.id, "Reservasi dibatalkan.") { reservations.cancel(r.id) }
    }

    // ---- Helper ----

    private fun mutate(id: String, successMessage: String, block: suspend () -> Unit) {
        if (id in _state.value.busyIds) return
        _state.update { it.copy(busyIds = it.busyIds + id) }
        viewModelScope.launch {
            try {
                block() // Repository memicu DataChanges → daftar dimuat ulang.
                _messages.send(successMessage)
            } catch (e: Exception) {
                _messages.send(e.toUserMessage())
            } finally {
                _state.update { it.copy(busyIds = it.busyIds - id) }
            }
        }
    }

    private suspend fun <T> result(block: suspend () -> T): UiState<T> = try {
        UiState.Success(block())
    } catch (e: Exception) {
        UiState.Error(e.toUserMessage())
    }

    /** Saat refresh gagal, tetap tampilkan data lama (pesan error dikirim lewat snackbar). */
    private fun <T> UiState<T>.keepDataOnError(previous: UiState<T>): UiState<T> =
        if (this is UiState.Error && previous is UiState.Success) previous else this

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                ActivityViewModel(container.reservationRepository, container.dataChanges)
            }
        }
    }
}
