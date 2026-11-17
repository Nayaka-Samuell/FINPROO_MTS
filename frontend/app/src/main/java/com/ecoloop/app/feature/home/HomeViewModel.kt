package com.ecoloop.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ecoloop.app.appContainer
import com.ecoloop.app.data.remote.dto.CategoryDto
import com.ecoloop.app.data.remote.dto.ListingDto
import com.ecoloop.app.data.remote.dto.User
import com.ecoloop.app.data.repository.AuthRepository
import com.ecoloop.app.data.repository.CatalogRepository
import com.ecoloop.app.data.repository.DataChanges
import com.ecoloop.app.data.repository.ReservationRepository
import com.ecoloop.app.data.repository.toUserMessage
import com.ecoloop.app.ui.UiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Satu-satunya sumber filter katalog. */
data class ListingFilter(
    val q: String = "",
    val categoryId: String? = null,
    val district: String? = null,
) {
    val isActive: Boolean get() = q.isNotBlank() || categoryId != null || district != null
}

data class HomeUiState(
    val user: User? = null,
    val filter: ListingFilter = ListingFilter(),
    val categories: List<CategoryDto> = emptyList(),
    val impactKg: Double? = null,
    val listings: UiState<List<ListingDto>> = UiState.Loading,
    val isRefreshing: Boolean = false,
)

private const val SEARCH_DEBOUNCE_MS = 350L

class HomeViewModel(
    private val auth: AuthRepository,
    private val catalog: CatalogRepository,
    private val reservations: ReservationRepository,
    changes: DataChanges,
) : ViewModel() {

    private val filter = MutableStateFlow(ListingFilter())
    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.user.collect { user -> _state.update { it.copy(user = user) } }
        }
        observeListings()
        loadHeader()
        viewModelScope.launch {
            changes.events.collect { refresh() }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeListings() {
        var lastQuery = filter.value.q
        val debouncedFilter = filter
            // Ketikan pencarian ditunda 350 ms; perubahan chip/wilayah langsung diproses.
            .debounce { next ->
                val delay = if (next.q != lastQuery) SEARCH_DEBOUNCE_MS else 0L
                lastQuery = next.q
                delay
            }
            .distinctUntilChanged()

        viewModelScope.launch {
            combine(debouncedFilter, reloadRequests.onStart { emit(Unit) }) { current, _ -> current }
                // collectLatest membatalkan request lama saat filter berubah, jadi
                // state loading selalu diakhiri oleh Success/Error request terbaru.
                .collectLatest { current -> loadListings(current) }
        }
    }

    private suspend fun loadListings(current: ListingFilter) {
        if (!_state.value.isRefreshing) _state.update { it.copy(listings = UiState.Loading) }
        val result = try {
            UiState.Success(
                catalog.listings(
                    q = current.q.trim().ifBlank { null },
                    categoryId = current.categoryId,
                    district = current.district,
                ),
            )
        } catch (e: Exception) {
            UiState.Error(e.toUserMessage())
        }
        _state.update { it.copy(listings = result, isRefreshing = false) }
    }

    private fun loadHeader() {
        viewModelScope.launch {
            runCatching { catalog.categories() }
                .onSuccess { categories -> _state.update { it.copy(categories = categories) } }
        }
        viewModelScope.launch { runCatching { auth.refreshUser() } }
        viewModelScope.launch {
            runCatching { reservations.impact() }
                .onSuccess { impact -> _state.update { it.copy(impactKg = impact.totalKg) } }
        }
    }

    fun onQueryChange(q: String) = updateFilter { it.copy(q = q) }

    fun onCategorySelected(categoryId: String?) = updateFilter { it.copy(categoryId = categoryId) }

    fun onDistrictSelected(district: String?) = updateFilter { it.copy(district = district) }

    fun clearFilters() = updateFilter { ListingFilter() }

    private fun updateFilter(transform: (ListingFilter) -> ListingFilter) {
        filter.update(transform)
        _state.update { it.copy(filter = filter.value) }
    }

    /** Pull-to-refresh dan "Coba lagi". */
    fun refresh() {
        _state.update { it.copy(isRefreshing = it.listings is UiState.Success) }
        reloadRequests.tryEmit(Unit)
        loadHeader()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                HomeViewModel(
                    container.authRepository,
                    container.catalogRepository,
                    container.reservationRepository,
                    container.dataChanges,
                )
            }
        }
    }
}
