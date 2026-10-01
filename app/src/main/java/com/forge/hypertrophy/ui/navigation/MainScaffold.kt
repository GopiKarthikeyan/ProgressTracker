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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.forge.hypertrophy.R
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import com.forge.hypertrophy.ui.screens.baseline.BaselineSetupScreen
import com.forge.hypertrophy.ui.screens.cardio.CardioScreen
import com.forge.hypertrophy.ui.screens.diagnostics.DiagnosticsScreen
import com.forge.hypertrophy.ui.screens.session.SessionDetailScreen
import com.forge.hypertrophy.ui.screens.session.SessionListScreen
import com.forge.hypertrophy.ui.screens.snapshots.SnapshotsScreen
import com.forge.hypertrophy.ui.screens.dashboard.DashboardScreen
import com.forge.hypertrophy.ui.screens.media.GalleryScreen
import com.forge.hypertrophy.ui.screens.media.PhysiqueCaptureScreen
import com.forge.hypertrophy.ui.screens.media.VideoCaptureScreen
import com.forge.hypertrophy.ui.screens.media.VideoComparisonScreen
import com.forge.hypertrophy.ui.screens.review.WeeklyReviewScreen
import com.forge.hypertrophy.ui.screens.settings.SettingsScreen
import com.forge.hypertrophy.ui.screens.settings.SettingsViewModel
import com.forge.hypertrophy.ui.screens.today.TodayScreen
import com.forge.hypertrophy.ui.screens.transfer.ImportPreviewScreen
import com.forge.hypertrophy.ui.screens.transfer.ImportPreviewViewModel
import com.forge.hypertrophy.ui.theme.Black
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.White
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object CardioRoute

@Serializable
data object RoutineRoute

@Serializable
data object DashboardRoute

@Serializable
data object SettingsRoute

@Serializable
data object WeeklyReviewRoute

@Serializable
data object BaselineSetupRoute

@Serializable
data object SessionListRoute

@Serializable
data class SessionDetailRoute(val sessionId: Long)

@Serializable
data object SnapshotsRoute

@Serializable
data object DiagnosticsRoute

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BreadcrumbEntryPoint {
    fun breadcrumbs(): Breadcrumbs
}

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
fun MainScaffold(
    modifier: Modifier = Modifier,
    openToday: StateFlow<Int> = MutableStateFlow(0),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val openTodayRequest by openToday.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(currentDestination?.route) {
        val route = currentDestination?.route ?: return@LaunchedEffect
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, BreadcrumbEntryPoint::class.java)
                .breadcrumbs()
                .record("screen:$route")
        }
    }
    LaunchedEffect(openTodayRequest) {
        if (openTodayRequest > 0) {
            navController.navigate(TodayRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
            composable<TodayRoute> {
                TodayScreen(
                    onOpenCardio = { navController.navigate(CardioRoute) },
                    onOpenGallery = { navController.navigate(GalleryRoute) },
                )
            }
            composable<CardioRoute> {
                CardioScreen(onBack = { navController.popBackStack() })
            }
            composable<GalleryRoute> {
                GalleryScreen(
                    onBack = { navController.popBackStack() },
                    onRecord = { exerciseId, setEntryId ->
                        navController.navigate(VideoCaptureRoute(exerciseId, setEntryId))
                    },
                    onCompare = { leftId, rightId -> navController.navigate(VideoComparisonRoute(leftId, rightId)) },
                    onPhysique = { navController.navigate(PhysiqueCaptureRoute) },
                )
            }
            composable<VideoCaptureRoute> {
                VideoCaptureScreen(onBack = { navController.popBackStack() })
            }
            composable<PhysiqueCaptureRoute> {
                PhysiqueCaptureScreen(onBack = { navController.popBackStack() })
            }
            composable<VideoComparisonRoute> {
                VideoComparisonScreen(onBack = { navController.popBackStack() })
            }
            routineGraph(navController)
            composable<DashboardRoute> {
                DashboardScreen(
                    onOpenWeeklyReview = { navController.navigate(WeeklyReviewRoute) },
                    onOpenSessions = { navController.navigate(SessionListRoute) },
                )
            }
            composable<WeeklyReviewRoute> {
                WeeklyReviewScreen(onBack = { navController.popBackStack() })
            }
            composable<SettingsRoute> {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = viewModel,
                    onLoadSample = { navController.navigate(ImportPreviewRoute(sample = true)) },
                    onOpenBaselines = { navController.navigate(BaselineSetupRoute) },
                    onOpenSnapshots = { navController.navigate(SnapshotsRoute) },
                    onOpenDiagnostics = { navController.navigate(DiagnosticsRoute) },
                )
            }
            composable<BaselineSetupRoute> {
                BaselineSetupScreen(onBack = { navController.popBackStack() })
            }
            composable<SessionListRoute> {
                SessionListScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate(SessionDetailRoute(id)) },
                )
            }
            composable<SessionDetailRoute> {
                SessionDetailScreen(onBack = { navController.popBackStack() })
            }
            composable<SnapshotsRoute> {
                SnapshotsScreen(onBack = { navController.popBackStack() })
            }
            composable<DiagnosticsRoute> {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable<ImportPreviewRoute> {
                val viewModel: ImportPreviewViewModel = hiltViewModel()
                ImportPreviewScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onDone = {
                        navController.navigate(BaselineSetupRoute) {
                            popUpTo<ImportPreviewRoute> { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
