package com.aicardgrader.app.nav

import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.Composable
import com.aicardgrader.app.CardGraderViewModel
import com.aicardgrader.app.camera.CaptureSide
import com.aicardgrader.app.camera.CardCaptureScreen
import com.aicardgrader.app.ui.AboutScreen
import com.aicardgrader.app.ui.AnalyzingScreen
import com.aicardgrader.app.ui.HistoryDetailScreen
import com.aicardgrader.app.ui.HistoryScreen
import com.aicardgrader.app.ui.HomeScreen
import com.aicardgrader.app.ui.ResultsScreen

@Composable
fun AppNavGraph(navController: NavHostController, viewModel: CardGraderViewModel) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartGrading = {
                    viewModel.resetCapture()
                    navController.navigate(Routes.CAPTURE_FRONT)
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.CAPTURE_FRONT) {
            CardCaptureScreen(
                side = CaptureSide.FRONT,
                onCaptured = { bitmap ->
                    viewModel.setFront(bitmap)
                    navController.navigate(Routes.CAPTURE_BACK)
                },
                onClose = { navController.popBackStack(Routes.HOME, false) }
            )
        }

        composable(Routes.CAPTURE_BACK) {
            CardCaptureScreen(
                side = CaptureSide.BACK,
                onCaptured = { bitmap ->
                    viewModel.setBack(bitmap)
                    navController.navigate(Routes.ANALYZING)
                },
                onSkipBack = {
                    viewModel.setBack(null)
                    navController.navigate(Routes.ANALYZING)
                },
                onClose = { navController.popBackStack(Routes.HOME, false) }
            )
        }

        composable(Routes.ANALYZING) {
            AnalyzingScreen(viewModel) {
                navController.navigate(Routes.RESULTS) {
                    popUpTo(Routes.HOME) { inclusive = false }
                }
            }
        }

        composable(Routes.RESULTS) {
            ResultsScreen(
                viewModel = viewModel,
                onGradeAnother = {
                    viewModel.resetCapture()
                    navController.navigate(Routes.CAPTURE_FRONT) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onDone = { navController.popBackStack(Routes.HOME, false) }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(viewModel) { id -> navController.navigate(Routes.historyDetail(id)) }
        }

        composable(
            Routes.HISTORY_DETAIL_PATTERN,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            HistoryDetailScreen(viewModel = viewModel, recordId = id, onDeleted = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen()
        }
    }
}
