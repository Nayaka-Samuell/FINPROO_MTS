package com.ecoloop.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ecoloop.app.feature.activity.ActivityScreen
import com.ecoloop.app.feature.detail.DetailScreen
import com.ecoloop.app.feature.home.HomeScreen
import com.ecoloop.app.feature.impact.ImpactScreen
import com.ecoloop.app.feature.listing.AddListingScreen
import com.ecoloop.app.feature.profile.ProfileScreen

/** Shell aplikasi setelah login: bottom bar, FAB, snackbar, dan NavHost. */
@Composable
fun EcoLoopApp(snackbarHostState: SnackbarHostState) {
    val navController = rememberNavController()
    // currentBackStackEntryAsState() memicu recomposition setiap kali tujuan berubah.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.HOME) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.ADD_LISTING) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Bagikan") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                    onShareMaterial = { navController.navigate(Routes.ADD_LISTING) },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument(Routes.DETAIL_ARG_ID) { type = NavType.StringType }),
            ) {
                DetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ADD_LISTING) {
                AddListingScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { navController.popBackStack(Routes.HOME, inclusive = false) },
                )
            }
            composable(Routes.ACTIVITY) { ActivityScreen() }
            composable(Routes.IMPACT) { ImpactScreen() }
            composable(Routes.PROFILE) { ProfileScreen() }
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
