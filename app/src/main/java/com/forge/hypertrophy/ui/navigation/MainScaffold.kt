package com.forge.hypertrophy.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.screens.dashboard.DashboardScreen
import com.forge.hypertrophy.ui.screens.routine.RoutineScreen
import com.forge.hypertrophy.ui.screens.settings.SettingsScreen
import com.forge.hypertrophy.ui.screens.today.TodayScreen
import com.forge.hypertrophy.ui.theme.Black
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.White
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object RoutineRoute

@Serializable
data object DashboardRoute

@Serializable
data object SettingsRoute

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val icon: ImageVector,
)

private val Destinations = listOf(
    TopLevelDestination(TodayRoute, R.string.nav_today, Icons.Filled.Home),
    TopLevelDestination(RoutineRoute, R.string.nav_routine, Icons.AutoMirrored.Filled.List),
    TopLevelDestination(DashboardRoute, R.string.nav_dashboard, Icons.Filled.Star),
    TopLevelDestination(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun MainScaffold(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        containerColor = Black,
        bottomBar = {
            NavigationBar(
                containerColor = Black,
                tonalElevation = 0.dp,
            ) {
                Destinations.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonAccent,
                            selectedTextColor = NeonAccent,
                            unselectedIconColor = White.copy(alpha = 0.72f),
                            unselectedTextColor = White.copy(alpha = 0.72f),
                            indicatorColor = Black,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> { TodayScreen() }
            composable<RoutineRoute> { RoutineScreen() }
            composable<DashboardRoute> { DashboardScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}
