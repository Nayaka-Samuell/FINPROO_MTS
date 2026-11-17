package com.ecoloop.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecoloop.app.data.remote.dto.ListingDto
import com.ecoloop.app.ui.UiState
import com.ecoloop.app.ui.components.CollectMessages
import com.ecoloop.app.ui.components.EcoField
import com.ecoloop.app.ui.components.EcoPrimaryButton
import com.ecoloop.app.ui.components.ErrorPanel
import com.ecoloop.app.ui.components.ListingCover
import com.ecoloop.app.ui.components.SkeletonLine
import com.ecoloop.app.ui.components.categoryStyle
import com.ecoloop.app.ui.components.shimmer
import com.ecoloop.app.ui.formatKg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(factory = DetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectMessages(viewModel.messages)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail material") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            val listing = (state.listing as? UiState.Success)?.data
            if (listing != null) {
                ReserveBar(
                    listing = listing,
                    isOwnListing = state.isOwnListing,
                    onReserve = viewModel::openReserveForm,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val listing = state.listing) {
                UiState.Loading -> DetailSkeleton()
                is UiState.Error -> ErrorPanel(
                    message = listing.message,
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(18.dp),
                )
                is UiState.Success -> DetailContent(listing.data)
            }
        }
    }

    if (state.form.visible) {
        val listing = (state.listing as? UiState.Success)?.data
        if (listing != null) {
            ReserveDialog(
                listing = listing,
                input = state.form.input,
                error = state.weightError.takeIf { state.form.showError || state.form.input.isNotEmpty() },
                submitting = state.form.submitting,
                onInputChange = viewModel::onWeightChange,
                onSubmit = viewModel::submitReservation,
                onDismiss = viewModel::dismissReserveForm,
            )
        }
    }
}

@Composable
private fun DetailContent(listing: ListingDto) {
    val style = categoryStyle(listing.categoryId, listing.categoryName)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        ListingCover(
            photoUrl = listing.coverPhoto,
            categoryId = listing.categoryId,
            categoryName = listing.categoryName,
            contentDescription = listing.title,
            iconSize = 64.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.large),
        )

        Text(
            text = (listing.categoryName ?: "Material").uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = style.foreground,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(listing.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(listing.district, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        StockCard(listing, Modifier.padding(top = 16.dp))
        SupplierCard(listing.supplierName ?: "Penyedia EcoLoop", listing.district, Modifier.padding(top = 12.dp))

        Text("Deskripsi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
        Text(
            listing.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun StockCard(listing: ListingDto, modifier: Modifier = Modifier) {
    val progress = if (listing.totalKg > 0) (listing.availableKg / listing.totalKg).toFloat().coerceIn(0f, 1f) else 0f
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Scale, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stok tersedia", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${formatKg(listing.availableKg)} / ${formatKg(listing.totalKg)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(8.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.primaryContainer,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun SupplierCard(name: String, district: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials(name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Penyedia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("Ambil sendiri di $district", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "E" }

@Composable
private fun ReserveBar(listing: ListingDto, isOwnListing: Boolean, onReserve: () -> Unit) {
    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            when {
                isOwnListing -> EcoPrimaryButton(text = "Ini listing milikmu", onClick = {}, enabled = false)
                listing.availableKg <= 0.0 || listing.status != "ACTIVE" ->
                    EcoPrimaryButton(text = "Stok habis", onClick = {}, enabled = false)
                else -> EcoPrimaryButton(text = "Ajukan reservasi", onClick = onReserve)
            }
        }
    }
}

@Composable
private fun ReserveDialog(
    listing: ListingDto,
    input: String,
    error: String?,
    submitting: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajukan reservasi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Berapa kg ${listing.title} yang kamu butuhkan?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                EcoField(
                    value = input,
                    onValueChange = onInputChange,
                    label = "Berat (kg)",
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    error = error,
                    supportingText = "Stok tersedia ${formatKg(listing.availableKg)}",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !submitting) {
                Text(if (submitting) "Mengirim…" else "Kirim permintaan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("Batal") }
        },
    )
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .shimmer(),
        )
        SkeletonLine(0.3f, 12.dp)
        SkeletonLine(0.8f, 22.dp)
        SkeletonLine(0.5f)
        SkeletonLine(1f, 70.dp)
        SkeletonLine(1f, 70.dp)
    }
}
