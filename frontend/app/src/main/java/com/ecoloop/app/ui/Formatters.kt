package com.ecoloop.app.ui

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IndonesianLocale: Locale = Locale.forLanguageTag("id-ID")
private val KgFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(IndonesianLocale))
private val DateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", IndonesianLocale)

/** 12.5 -> "12,5 kg" */
fun formatKg(value: Double): String = "${KgFormat.format(value)} kg"

fun formatDate(iso: String?): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DateFormat)
}.getOrDefault("-")

/** Hasil validasi input berat dari pengguna. */
data class WeightCheck(val value: BigDecimal?, val error: String?)

private val MaxWeight = BigDecimal("99999999.99")

/**
 * Validasi berat: wajib > 0, maksimal 2 desimal, menerima koma sebagai desimal,
 * dan (opsional) tidak melebihi [maxKg].
 */
fun validateWeight(raw: String, maxKg: Double? = null): WeightCheck {
    val normalized = raw.trim().replace(',', '.')
    if (normalized.isEmpty()) return WeightCheck(null, "Berat wajib diisi")
    val value = normalized.toBigDecimalOrNull()
        ?: return WeightCheck(null, "Gunakan angka, contoh 2,5")
    return when {
        value.signum() <= 0 -> WeightCheck(null, "Berat harus lebih dari 0")
        value.stripTrailingZeros().scale() > 2 -> WeightCheck(null, "Maksimal 2 angka di belakang koma")
        value > MaxWeight -> WeightCheck(null, "Berat terlalu besar")
        maxKg != null && value > BigDecimal.valueOf(maxKg) ->
            WeightCheck(null, "Melebihi stok tersedia (${formatKg(maxKg)})")
        else -> WeightCheck(value.setScale(2, java.math.RoundingMode.UNNECESSARY), null)
    }
}
