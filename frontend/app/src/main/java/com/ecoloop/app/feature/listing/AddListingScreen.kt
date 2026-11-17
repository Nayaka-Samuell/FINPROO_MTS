package com.ecoloop.app.feature.listing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecoloop.app.data.repository.DISTRICTS
import com.ecoloop.app.ui.UiState
import com.ecoloop.app.ui.components.CollectMessages
import com.ecoloop.app.ui.components.EcoDropdownField
import com.ecoloop.app.ui.components.EcoField
import com.ecoloop.app.ui.components.EcoPrimaryButton
import com.ecoloop.app.ui.components.ErrorPanel
import com.ecoloop.app.ui.components.LocalMessenger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddListingScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: AddListingViewModel = viewModel(factory = AddListingViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    CollectMessages(viewModel.messages)

    LaunchedEffect(viewModel) {
        viewModel.created.collect {
            messenger.show("Listing berhasil diterbitkan")
            onCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bagikan material") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    EcoPrimaryButton(
                        text = "Terbitkan listing",
                        onClick = viewModel::submit,
                        loading = state.submitting,
                        enabled = state.categories is UiState.Success,
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Bantu material sisa menemukan penggunaan berikutnya. Penerima akan mengambil sendiri di wilayahmu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            val showErrors = state.showErrors
            EcoField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = "Judul material",
                leadingIcon = Icons.Filled.Title,
                error = state.titleError.takeIf { showErrors },
            )

            when (val categories = state.categories) {
                is UiState.Error -> ErrorPanel(message = categories.message, onRetry = viewModel::loadCategories)
                else -> EcoDropdownField(
                    label = if (categories is UiState.Loading) "Memuat kategori…" else "Kategori",
                    selected = state.category,
                    options = (categories as? UiState.Success)?.data.orEmpty(),
                    optionLabel = { it.name },
                    onSelect = viewModel::onCategoryChange,
                    leadingIcon = Icons.Filled.Category,
                    error = state.categoryError.takeIf { showErrors },
                )
            }

            EcoField(
                value = state.weight,
                onValueChange = viewModel::onWeightChange,
                label = "Total berat (kg)",
                leadingIcon = Icons.Filled.Scale,
                keyboardType = KeyboardType.Decimal,
                // Tampilkan error format segera setelah mengetik, error "wajib" setelah submit.
                error = state.weightError.takeIf { showErrors || state.weight.isNotEmpty() },
                supportingText = "Maksimal 2 desimal, boleh pakai koma (contoh 2,5)",
            )

            EcoDropdownField(
                label = "Wilayah pengambilan",
                selected = state.district,
                options = DISTRICTS,
                optionLabel = { it },
                onSelect = viewModel::onDistrictChange,
                leadingIcon = Icons.Filled.Place,
            )

            EcoField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = "Deskripsi (kondisi, ukuran, cara ambil)",
                singleLine = false,
                minLines = 3,
                error = state.descriptionError.takeIf { showErrors },
            )

            EcoField(
                value = state.photoUrl,
                onValueChange = viewModel::onPhotoUrlChange,
                label = "URL foto (opsional)",
                leadingIcon = Icons.Filled.Image,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                error = state.photoError,
                supportingText = "Kosongkan untuk memakai ilustrasi kategori",
            )
        }
    }
}
