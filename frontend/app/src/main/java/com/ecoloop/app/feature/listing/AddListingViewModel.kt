package com.ecoloop.app.feature.listing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ecoloop.app.appContainer
import com.ecoloop.app.data.remote.dto.CategoryDto
import com.ecoloop.app.data.remote.dto.ListingCreateRequest
import com.ecoloop.app.data.repository.AuthRepository
import com.ecoloop.app.data.repository.CatalogRepository
import com.ecoloop.app.data.repository.DISTRICTS
import com.ecoloop.app.data.repository.toUserMessage
import com.ecoloop.app.ui.UiState
import com.ecoloop.app.ui.validateWeight
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddListingUiState(
    val categories: UiState<List<CategoryDto>> = UiState.Loading,
    val title: String = "",
    val category: CategoryDto? = null,
    val weight: String = "",
    val district: String = DISTRICTS.first(),
    val description: String = "",
    val photoUrl: String = "",
    val showErrors: Boolean = false,
    val submitting: Boolean = false,
) {
    val titleError: String? get() = if (title.isBlank()) "Judul wajib diisi" else null
    val categoryError: String? get() = if (category == null) "Pilih kategori" else null
    val weightError: String? get() = validateWeight(weight).error
    val descriptionError: String? get() = if (description.isBlank()) "Deskripsi wajib diisi" else null
    val photoError: String?
        get() = if (photoUrl.isNotBlank() && !photoUrl.trim().matches(Regex("^https?://\\S+$"))) {
            "URL harus diawali http:// atau https://"
        } else {
            null
        }

    val isValid: Boolean
        get() = listOf(titleError, categoryError, weightError, descriptionError, photoError).all { it == null }
}

/** Sinyal sukses agar layar menutup dirinya sendiri. */
data object ListingCreated

class AddListingViewModel(
    private val catalog: CatalogRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AddListingUiState(district = auth.user.value?.district?.takeIf { it in DISTRICTS } ?: DISTRICTS.first()),
    )
    val state: StateFlow<AddListingUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _created = Channel<ListingCreated>(Channel.CONFLATED)
    val created: Flow<ListingCreated> = _created.receiveAsFlow()

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _state.update { it.copy(categories = UiState.Loading) }
            val result = try {
                UiState.Success(catalog.categories())
            } catch (e: Exception) {
                UiState.Error(e.toUserMessage())
            }
            _state.update { it.copy(categories = result) }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }
    fun onCategoryChange(value: CategoryDto) = _state.update { it.copy(category = value) }
    fun onWeightChange(value: String) = _state.update { it.copy(weight = value) }
    fun onDistrictChange(value: String) = _state.update { it.copy(district = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onPhotoUrlChange(value: String) = _state.update { it.copy(photoUrl = value) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val category = current.category
        val kg = validateWeight(current.weight).value
        if (!current.isValid || category == null || kg == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                catalog.create(
                    ListingCreateRequest(
                        categoryId = category.id,
                        title = current.title.trim(),
                        description = current.description.trim(),
                        totalKg = kg,
                        district = current.district,
                        photos = listOfNotNull(current.photoUrl.trim().ifBlank { null }),
                    ),
                )
                _created.send(ListingCreated)
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false) }
                _messages.send(e.toUserMessage())
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                AddListingViewModel(container.catalogRepository, container.authRepository)
            }
        }
    }
}
