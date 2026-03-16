package com.twinmind.recorder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.twinmind.recorder.ui.dashboard.DashboardScreen
import com.twinmind.recorder.ui.recording.RecordingScreen
import com.twinmind.recorder.ui.summary.SummaryScreen

sealed class Screen(val route: String) {
    object Dashboard  : Screen("dashboard")
    object Recording  : Screen("recording/{meetingId}") {
        fun createRoute(meetingId: String) = "recording/$meetingId"
    }
    object Summary    : Screen("summary/{meetingId}") {
        fun createRoute(meetingId: String) = "summary/$meetingId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Dashboard.route) {

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNewRecording = { meetingId ->
                    navController.navigate(Screen.Recording.createRoute(meetingId))
                },
                onMeetingClick = { meetingId ->
                    navController.navigate(Screen.Summary.createRoute(meetingId))
                }
            )
        }

        composable(
            route = Screen.Recording.route,
            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
        ) { backStack ->
            val meetingId = backStack.arguments?.getString("meetingId") ?: return@composable
            RecordingScreen(
                meetingId = meetingId,
                onNavigateBack = { navController.popBackStack() },
                onRecordingStopped = { id ->
                    navController.popBackStack()
                    navController.navigate(Screen.Summary.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.Summary.route,
            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
        ) { backStack ->
            val meetingId = backStack.arguments?.getString("meetingId") ?: return@composable
            SummaryScreen(
                meetingId = meetingId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
