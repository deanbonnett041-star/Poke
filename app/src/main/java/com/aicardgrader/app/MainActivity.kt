package com.aicardgrader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aicardgrader.app.nav.AppNavGraph
import com.aicardgrader.app.nav.Routes
import com.aicardgrader.app.ui.theme.AiCardGraderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CardGraderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiCardGraderTheme {
                AppScaffold(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(viewModel: CardGraderViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    val title = when (route) {
        Routes.HISTORY -> "History"
        Routes.HISTORY_DETAIL_PATTERN -> "Card detail"
        Routes.ABOUT -> "How this works"
        Routes.RESULTS -> "Results"
        else -> null
    }

    Scaffold(
        topBar = {
            if (title != null) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            AppNavGraph(navController = navController, viewModel = viewModel)
        }
    }
}
