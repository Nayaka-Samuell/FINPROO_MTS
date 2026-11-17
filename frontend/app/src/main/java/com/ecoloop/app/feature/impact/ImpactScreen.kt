package com.ecoloop.app.feature.impact

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecoloop.app.ui.UiState
import com.ecoloop.app.ui.components.EcoEmptyState
import com.ecoloop.app.ui.components.ErrorPanel
import com.ecoloop.app.ui.components.ListCardSkeleton
import com.ecoloop.app.ui.components.SkeletonLine
import com.ecoloop.app.ui.formatDate
import com.ecoloop.app.ui.formatKg
import com.ecoloop.app.ui.theme.EcoAccent
import com.ecoloop.app.ui.theme.EcoPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpactScreen(viewModel: ImpactViewModel = viewModel(factory = ImpactViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column {
                    Text("Dampak akunmu", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Kontribusi terverifikasi untuk SDG 12.5",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (val content = state.content) {
                UiState.Loading -> {
                    item { SkeletonLine(1f, 150.dp) }
                    items(2) { ListCardSkeleton() }
                }
                is UiState.Error -> item { ErrorPanel(content.message, onRetry = viewModel::load) }
                is UiState.Success -> {
                    val impact = content.data.impact
                    item { TotalImpactCard(impact.totalKg, impact.transferCount) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard("Disalurkan", impact.suppliedKg, Modifier.weight(1f))
                            MetricCard("Diterima", impact.receivedKg, Modifier.weight(1f))
                        }
                    }
                    item {
                        Text(
                            "Riwayat transfer selesai",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (content.data.history.isEmpty()) {
                        item {
                            EcoEmptyState(
                                title = "Belum ada transfer selesai",
                                message = "Dampak dihitung dari berat aktual saat serah terima QR berhasil.",
                                icon = Icons.Filled.History,
                            )
                        }
                    } else {
                        items(content.data.history, key = { it.reservation.id }) { HistoryRow(it) }
                    }
                }
            }
        }
    }
}

/** Angka kg naik dari 0 ke nilai sebenarnya. */
@Composable
private fun animatedKg(target: Double): Double {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animatable.animateTo(target.toFloat(), tween(durationMillis = 1_200, easing = FastOutSlowInEasing))
    }
    return animatable.value.toDouble()
}

@Composable
private fun TotalImpactCard(totalKg: Double, transferCount: Long) {
    val shown = animatedKg(totalKg)
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(EcoPrimary, EcoAccent)), MaterialTheme.shapes.large)
            .padding(22.dp),
    ) {
        Column {
            Text("Total material tersalurkan", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            Text(
                formatKg(shown),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize * 1.4f),
            )
            Text(
                "$transferCount transfer selesai",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, kg: Double, modifier: Modifier = Modifier) {
    val shown = animatedKg(kg)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatKg(shown), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HistoryRow(item: TransferHistoryItem) {
    val r = item.reservation
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.supplied) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(r.listingTitle ?: "Material", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (item.supplied) "Ke ${r.receiverName ?: "-"}" else "Dari ${r.supplierName ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatDate(r.completedAt ?: r.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatKg(r.actualKg ?: r.reservedKg),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
