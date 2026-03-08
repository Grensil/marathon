package com.example.marathon

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.history.AudioEvent
import com.example.history.HistoryListScreen
import com.example.history.HistoryScreen
import com.example.history.RunDetailScreen
import com.example.marathon.service.RunningService


@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != MainRoute.RunDetail.path

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.height(72.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val items = listOf(
                        BottomNavItem(MainRoute.Run.path, R.string.nav_run, R.drawable.icon_run),
                        BottomNavItem(MainRoute.HistoryList.path, R.string.nav_history, R.drawable.icon_history),
                        BottomNavItem(MainRoute.Settings.path, R.string.nav_settings, R.drawable.icon_settings),
                    )

                    items.forEach { item ->
                        val selected = currentRoute == item.route
                        val label = stringResource(item.labelRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(item.icon),
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            MainNavGraph(navController = navController)
        }
    }
}

private data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val icon: Int
)

@Composable
fun MainNavGraph(navController: NavHostController) {
    val context = LocalContext.current

    // remember: 람다를 캐싱하여 매 리컴포지션마다 새 객체 생성 방지
    val onAudioEvent = remember<(AudioEvent) -> Unit>(context) {
        { event ->
            when (event) {
                is AudioEvent.RunStarted -> RunningService.startService(context)
                is AudioEvent.RunPaused -> RunningService.pauseService(context)
                is AudioEvent.RunResumed -> RunningService.resumeService(context)
                is AudioEvent.RunCompleted -> RunningService.stopService(
                    context, event.distanceKm, event.elapsedTime
                )
                is AudioEvent.KilometerReached -> RunningService.announceKilometer(
                    context, event.km, event.pace, event.elapsedTime
                )
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = MainRoute.Run.path
    ) {
        composable(route = MainRoute.Run.path) {
            HistoryScreen(onAudioEvent = onAudioEvent)
        }

        composable(route = MainRoute.HistoryList.path) {
            HistoryListScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(MainRoute.RunDetail.createRoute(sessionId))
                }
            )
        }

        composable(route = MainRoute.Settings.path) {
            SettingsScreen()
        }

        composable(
            route = MainRoute.RunDetail.path,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            RunDetailScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }

    }
}
