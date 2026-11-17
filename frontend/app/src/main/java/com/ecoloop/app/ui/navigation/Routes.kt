package com.ecoloop.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val IMPACT = "impact"
    const val PROFILE = "profile"
    const val ADD_LISTING = "listing/new"

    const val DETAIL_ARG_ID = "id"
    const val DETAIL = "detail/{$DETAIL_ARG_ID}"
    fun detail(id: String) = "detail/$id"
}

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "Beranda", Icons.Filled.Home),
    ACTIVITY(Routes.ACTIVITY, "Aktivitas", Icons.AutoMirrored.Filled.ReceiptLong),
    IMPACT(Routes.IMPACT, "Dampak", Icons.Filled.Eco),
    PROFILE(Routes.PROFILE, "Profil", Icons.Filled.Person),
}
