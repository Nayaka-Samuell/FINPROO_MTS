package com.ecoloop.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private data class BadgeStyle(val label: String, val background: Color, val foreground: Color)

private fun badgeStyle(status: String) = when (status) {
    "REQUESTED" -> BadgeStyle("Menunggu persetujuan", Color(0xFFFFEFD2), Color(0xFF8A4B08))
    "APPROVED" -> BadgeStyle("Siap diambil", Color(0xFFDCEFE3), Color(0xFF176347))
    "COMPLETED" -> BadgeStyle("Selesai", Color(0xFFD7F0D8), Color(0xFF205C2A))
    "REJECTED" -> BadgeStyle("Ditolak", Color(0xFFFFE1E1), Color(0xFF9B2525))
    "CANCELLED" -> BadgeStyle("Dibatalkan", Color(0xFFE8E9E8), Color(0xFF4B514E))
    else -> BadgeStyle(status, Color(0xFFE8E9E8), Color(0xFF4B514E))
}

@Composable
fun EcoStatusBadge(status: String, modifier: Modifier = Modifier) {
    val style = badgeStyle(status)
    Surface(color = style.background, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text = style.label,
            color = style.foreground,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
