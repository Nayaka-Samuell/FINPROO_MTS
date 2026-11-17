package com.ecoloop.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Efek kedip lembut untuk placeholder saat memuat. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmer-alpha",
    )
    background(Color(0xFFDDE7E0).copy(alpha = alpha), RoundedCornerShape(8.dp))
}

@Composable
fun SkeletonLine(width: Float = 1f, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(width)
            .height(height)
            .shimmer(),
    )
}

/** Placeholder seukuran [EcoMaterialCard]. */
@Composable
fun MaterialCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .shimmer(),
        )
        Column(Modifier.padding(12.dp)) {
            SkeletonLine(0.4f, 10.dp)
            SkeletonLine(0.9f, modifier = Modifier.padding(top = 8.dp))
            SkeletonLine(0.6f, modifier = Modifier.padding(top = 6.dp))
            SkeletonLine(0.5f, 12.dp, Modifier.padding(top = 10.dp))
        }
    }
}

/** Placeholder untuk kartu daftar (aktivitas, riwayat). */
@Composable
fun ListCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            SkeletonLine(0.7f, 16.dp)
            SkeletonLine(0.45f, modifier = Modifier.padding(top = 8.dp))
            SkeletonLine(0.3f, 22.dp, Modifier.padding(top = 12.dp))
        }
    }
}
