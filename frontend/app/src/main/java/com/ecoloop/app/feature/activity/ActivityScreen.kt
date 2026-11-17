package com.ecoloop.app.feature.activity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecoloop.app.data.remote.dto.ReservationDto
import com.ecoloop.app.feature.activity.scan.QrScannerDialog
import com.ecoloop.app.ui.UiState
import com.ecoloop.app.ui.components.CollectMessages
import com.ecoloop.app.ui.components.EcoEmptyState
import com.ecoloop.app.ui.components.EcoField
import com.ecoloop.app.ui.components.EcoStatusBadge
import com.ecoloop.app.ui.components.ErrorPanel
import com.ecoloop.app.ui.components.ListCardSkeleton
import com.ecoloop.app.ui.formatDate
import com.ecoloop.app.ui.formatKg
import kotlinx.coroutines.delay
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(viewModel: ActivityViewModel = viewModel(factory = ActivityViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectMessages(viewModel.messages)

    Column(Modifier.fillMaxSize()) {
        Text(
            "Aktivitas",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
        )
        PrimaryTabRow(
            selectedTabIndex = state.tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            ActivityTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(if (tab == ActivityTab.INCOMING) "Masuk (penyedia)" else "Keluar (penerima)")
                    },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            ReservationList(state = state, viewModel = viewModel)
        }
    }

    state.handoverQr?.let { qr ->
        HandoverQrDialog(qr = qr, onDismiss = viewModel::dismissHandoverQr)
    }
    state.scanningFor?.let { reservation ->
        QrScannerDialog(
            onTokenScanned = viewModel::onTokenScanned,
            onManualEntry = { viewModel.openManualEntry(reservation) },
            onDismiss = viewModel::dismissScan,
        )
    }
    state.completeForm?.let { form ->
        CompleteHandoverDialog(
            form = form,
            onTokenChange = viewModel::onCompleteTokenChange,
            onWeightChange = viewModel::onCompleteWeightChange,
            onSubmit = viewModel::submitComplete,
            onDismiss = viewModel::dismissCompleteForm,
        )
    }
    state.confirmCancel?.let { reservation ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCancel,
            title = { Text("Batalkan reservasi?") },
            text = { Text("Reservasi ${reservation.listingTitle ?: "material"} akan dibatalkan dan stok dikembalikan ke penyedia.") },
            confirmButton = { TextButton(onClick = viewModel::confirmCancel) { Text("Batalkan reservasi") } },
            dismissButton = { TextButton(onClick = viewModel::dismissCancel) { Text("Kembali") } },
        )
    }
}

@Composable
private fun ReservationList(state: ActivityUiState, viewModel: ActivityViewModel) {
    val isIncoming = state.tab == ActivityTab.INCOMING
    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val records = state.current) {
            UiState.Loading -> items(3) { ListCardSkeleton() }
            is UiState.Error -> item { ErrorPanel(message = records.message, onRetry = viewModel::refresh) }
            is UiState.Success -> if (records.data.isEmpty()) {
                item {
                    EcoEmptyState(
                        title = if (isIncoming) "Belum ada permintaan masuk" else "Belum ada reservasi",
                        message = if (isIncoming) {
                            "Permintaan untuk material yang kamu bagikan akan muncul di sini."
                        } else {
                            "Cari material di Beranda lalu ajukan reservasi."
                        },
                    )
                }
            } else {
                items(records.data, key = { it.id }) { reservation ->
                    ReservationCard(
                        reservation = reservation,
                        isIncoming = isIncoming,
                        busy = reservation.id in state.busyIds,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(
    reservation: ReservationDto,
    isIncoming: Boolean,
    busy: Boolean,
    viewModel: ActivityViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(reservation.listingTitle ?: "Material", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isIncoming) {
                            "Pemesan: ${reservation.receiverName ?: "-"}"
                        } else {
                            "Penyedia: ${reservation.supplierName ?: "-"}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EcoStatusBadge(reservation.status)
            }
            Text(
                text = buildString {
                    append(formatKg(reservation.actualKg ?: reservation.requestedKg))
                    reservation.listingDistrict?.let { append(" · $it") }
                    append(" · ${formatDate(reservation.createdAt)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            StatusTimeline(reservation.status, Modifier.padding(top = 14.dp))

            ReservationActions(reservation, isIncoming, busy, viewModel)
        }
    }
}

@Composable
private fun ReservationActions(
    reservation: ReservationDto,
    isIncoming: Boolean,
    busy: Boolean,
    viewModel: ActivityViewModel,
) {
    val status = reservation.status
    val hasActions = status == "REQUESTED" || status == "APPROVED"
    if (!hasActions) return

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
    if (isIncoming) {
        when (status) {
            "REQUESTED" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.reject(reservation) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Tolak")
                }
                Button(onClick = { viewModel.approve(reservation) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Setujui")
                }
            }
            "APPROVED" -> Column {
                Button(onClick = { viewModel.startScan(reservation) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan QR")
                }
                TextButton(onClick = { viewModel.openManualEntry(reservation) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Masukkan token manual")
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.askCancel(reservation) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Batalkan")
            }
            if (status == "APPROVED") {
                Button(onClick = { viewModel.showHandoverQr(reservation) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tampilkan QR")
                }
            }
        }
    }
}

/** Timeline Diminta → Disetujui → Selesai. Status ditolak/dibatalkan menghentikan timeline. */
@Composable
private fun StatusTimeline(status: String, modifier: Modifier = Modifier) {
    val reached = when (status) {
        "REQUESTED" -> 1
        "APPROVED" -> 2
        "COMPLETED" -> 3
        else -> 1
    }
    val stopped = status == "REJECTED" || status == "CANCELLED"
    val labels = listOf(
        "Diminta",
        if (stopped) (if (status == "REJECTED") "Ditolak" else "Dibatalkan") else "Disetujui",
        "Selesai",
    )
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outline
    val stopColor = MaterialTheme.colorScheme.error

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { index, label ->
            val done = index < reached
            val isStopStep = stopped && index == 1
            val lit = done || isStopStep
            val color = when {
                isStopStep -> stopColor
                done -> active
                else -> inactive
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Garis kiri (tidak ada untuk langkah pertama)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (index == 0) Color.Transparent else color),
                    )
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(if (lit) color else inactive.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isStopStep -> Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            done -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    // Garis kanan (tidak ada untuk langkah terakhir)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                when {
                                    index == labels.lastIndex -> Color.Transparent
                                    index + 1 < reached -> active
                                    else -> inactive
                                },
                            ),
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lit) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HandoverQrDialog(qr: HandoverQr, onDismiss: () -> Unit) {
    val bitmap = remember(qr.token) { createQrBitmap(qr.token).asImageBitmap() }
    val expiresAt = remember(qr.expiresAt) {
        runCatching { Instant.parse(qr.expiresAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis() + 15 * 60_000)
    }
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (now.longValue < expiresAt) {
            delay(1_000)
            now.longValue = System.currentTimeMillis()
        }
    }
    val remaining = ((expiresAt - now.longValue) / 1000).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR serah terima") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(bitmap, contentDescription = "QR serah terima", modifier = Modifier.size(220.dp))
                Text(
                    text = if (remaining > 0) {
                        "Berlaku ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
                    } else {
                        "QR kedaluwarsa. Tutup lalu buat QR baru."
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (remaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Tunjukkan QR ini ke penyedia saat mengambil material. Sekali pakai.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun CompleteHandoverDialog(
    form: CompleteForm,
    onTokenChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selesaikan serah terima") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${form.reservation.listingTitle ?: "Material"} · diminta ${formatKg(form.reservation.requestedKg)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                EcoField(
                    value = form.token,
                    onValueChange = onTokenChange,
                    label = "Token QR",
                    error = form.tokenError.takeIf { form.showErrors },
                    supportingText = if (form.token.isNotBlank()) "Token terbaca" else null,
                )
                EcoField(
                    value = form.weight,
                    onValueChange = onWeightChange,
                    label = "Berat aktual (kg)",
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    error = form.weightError.takeIf { form.showErrors || form.weight.isNotEmpty() },
                    supportingText = "Timbang material saat serah terima",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !form.submitting) {
                Text(if (form.submitting) "Menyimpan…" else "Selesaikan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !form.submitting) { Text("Batal") }
        },
    )
}
