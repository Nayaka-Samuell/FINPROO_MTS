package com.ecoloop.app.feature.impact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ecoloop.app.appContainer
import com.ecoloop.app.data.remote.dto.ImpactDto
import com.ecoloop.app.data.remote.dto.ReservationDto
import com.ecoloop.app.data.repository.DataChanges
import com.ecoloop.app.data.repository.ReservationRepository
import com.ecoloop.app.data.repository.toUserMessage
import com.ecoloop.app.ui.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Satu baris riwayat transfer selesai, dari sisi akun ini. */
data class TransferHistoryItem(
    val reservation: ReservationDto,
    val supplied: Boolean,
)

data class ImpactContent(
    val impact: ImpactDto,
    val history: List<TransferHistoryItem>,
)

data class ImpactUiState(
    val content: UiState<ImpactContent> = UiState.Loading,
    val isRefreshing: Boolean = false,
)

class ImpactViewModel(
    private val reservations: ReservationRepository,
    changes: DataChanges,
) : ViewModel() {

    private val _state = MutableStateFlow(ImpactUiState())
    val state: StateFlow<ImpactUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { changes.events.collect { load() } }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_state.value.content !is UiState.Success) _state.update { it.copy(content = UiState.Loading) }
            val result = try {
                UiState.Success(fetch())
            } catch (e: Exception) {
                UiState.Error(e.toUserMessage())
            }
            _state.update { it.copy(content = result, isRefreshing = false) }
        }
    }

    private suspend fun fetch(): ImpactContent = coroutineScope {
        val impact = async { reservations.impact() }
        val incoming = async { reservations.incoming() }
        val outgoing = async { reservations.outgoing() }
        val history = (
            incoming.await().map { TransferHistoryItem(it, supplied = true) } +
                outgoing.await().map { TransferHistoryItem(it, supplied = false) }
            )
            .filter { it.reservation.status == "COMPLETED" }
            .sortedByDescending { it.reservation.completedAt ?: it.reservation.updatedAt }
        ImpactContent(impact.await(), history)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                ImpactViewModel(container.reservationRepository, container.dataChanges)
            }
        }
    }
}
