package com.ecoloop.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecoloop.app.data.remote.dto.CategoryDto
import com.ecoloop.app.data.repository.DISTRICTS
import com.ecoloop.app.data.repository.PILOT_CITY
import com.ecoloop.app.ui.UiState
import com.ecoloop.app.ui.components.CategoryIcon
import com.ecoloop.app.ui.components.EcoEmptyState
import com.ecoloop.app.ui.components.EcoMaterialCard
import com.ecoloop.app.ui.components.ErrorPanel
import com.ecoloop.app.ui.components.MaterialCardSkeleton
import com.ecoloop.app.ui.formatKg
import com.ecoloop.app.ui.theme.EcoAccent
import com.ecoloop.app.ui.theme.EcoPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenListing: (String) -> Unit,
    onShareMaterial: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        // LazyVerticalGrid adalah satu-satunya container scroll di layar ini.
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "greeting", span = { GridItemSpan(maxLineSpan) }) {
                Greeting(
                    name = state.user?.name,
                    district = state.user?.district,
                )
            }
            item(key = "banner", span = { GridItemSpan(maxLineSpan) }) {
                ImpactBanner(totalKg = state.impactKg, onShareMaterial = onShareMaterial)
            }
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                SearchBar(query = state.filter.q, onQueryChange = viewModel::onQueryChange)
            }
            item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                CategoryChips(
                    categories = state.categories,
                    selectedId = state.filter.categoryId,
                    onSelect = viewModel::onCategorySelected,
                )
            }
            item(key = "section", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    district = state.filter.district,
                    onDistrictSelected = viewModel::onDistrictSelected,
                )
            }
            listingContent(
                state = state,
                onOpenListing = onOpenListing,
                onRetry = viewModel::refresh,
                onClearFilters = viewModel::clearFilters,
                onShareMaterial = onShareMaterial,
            )
        }
    }
}

private fun LazyGridScope.listingContent(
    state: HomeUiState,
    onOpenListing: (String) -> Unit,
    onRetry: () -> Unit,
    onClearFilters: () -> Unit,
    onShareMaterial: () -> Unit,
) {
    when (val listings = state.listings) {
        UiState.Loading -> items(6, key = { "skeleton-$it" }) { MaterialCardSkeleton() }
        is UiState.Error -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
            ErrorPanel(message = listings.message, onRetry = onRetry)
        }
        is UiState.Success -> if (listings.data.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                if (state.filter.isActive) {
                    EcoEmptyState(
                        title = "Belum ada material yang cocok",
                        message = "Coba kata kunci lain atau ubah kategori dan wilayah.",
                        icon = Icons.Filled.SearchOff,
                        actionText = "Hapus filter",
                        onAction = onClearFilters,
                    )
                } else {
                    EcoEmptyState(
                        title = "Belum ada material",
                        message = "Jadilah yang pertama membagikan material sisa.",
                        actionText = "Bagikan material",
                        onAction = onShareMaterial,
                    )
                }
            }
        } else {
            items(listings.data, key = { it.id }) { listing ->
                EcoMaterialCard(item = listing, onClick = { onOpenListing(listing.id) })
            }
        }
    }
}

@Composable
private fun Greeting(name: String?, district: String?) {
    Column {
        Text(
            text = "Halo, ${name?.substringBefore(' ') ?: "Teman"} 👋",
            style = MaterialTheme.typography.headlineMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Icon(
                Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = listOfNotNull(district?.takeIf { it.isNotBlank() }, PILOT_CITY).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ImpactBanner(totalKg: Double?, onShareMaterial: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(EcoPrimary, EcoAccent)),
                MaterialTheme.shapes.large,
            )
            .padding(20.dp),
    ) {
        Column {
            Text(
                "Material tersalurkan",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = totalKg?.let(::formatKg) ?: "– kg",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                "Bahan sisa, peluang baru. Bagikan material agar bisa dipakai lagi.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            Button(
                onClick = onShareMaterial,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = EcoPrimary),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bagikan material")
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Cari kain, kardus, botol…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
private fun CategoryChips(
    categories: List<CategoryDto>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("Semua") },
                colors = ecoChipColors(),
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(if (selectedId == category.id) null else category.id) },
                label = { Text(category.name) },
                leadingIcon = { CategoryIcon(category.id, category.name) },
                colors = ecoChipColors(),
            )
        }
    }
}

@Composable
private fun ecoChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun SectionHeader(district: String?, onDistrictSelected: (String?) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text("Material untukmu", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Box {
            FilterChip(
                selected = district != null,
                onClick = { menuOpen = true },
                label = {
                    Text(
                        district ?: "Semua wilayah",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = { Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = ecoChipColors(),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Semua wilayah") },
                    onClick = {
                        onDistrictSelected(null)
                        menuOpen = false
                    },
                )
                DISTRICTS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onDistrictSelected(option)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}
