package com.omersusin.wellread.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omersusin.wellread.ui.screens.home.HomeScreen
import com.omersusin.wellread.ui.screens.library.LibraryScreen
import com.omersusin.wellread.ui.screens.reader.ReaderScreen
import com.omersusin.wellread.ui.screens.stats.StatsScreen
import com.omersusin.wellread.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Library : Screen("library")
    object Stats : Screen("stats")
    object Settings : Screen("settings")
    object Reader : Screen("reader/{bookId}/{mode}") {
        fun createRoute(bookId: Long, mode: String) = "reader/$bookId/$mode"
    }
}

@Composable
fun WellReadNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 }
        },
        exitTransition = {
            fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 4 }
        },
        popEnterTransition = {
            fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 4 }
        },
        popExitTransition = {
            fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it / 4 }
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLibrary = { navController.navigate(Screen.Library.route) },
                onNavigateToReader = { bookId, mode ->
                    navController.navigate(Screen.Reader.createRoute(bookId, mode))
                },
                onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReader = { bookId, mode ->
                    navController.navigate(Screen.Reader.createRoute(bookId, mode))
                }
            )
        }
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType },
                navArgument("mode") { type = NavType.StringType }
            )
        ) { backStack ->
            val bookId = backStack.arguments?.getLong("bookId") ?: 0L
            val mode = backStack.arguments?.getString("mode") ?: "BIONIC"
            ReaderScreen(
                bookId = bookId,
                initialMode = mode,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Stats.route) {
            StatsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
