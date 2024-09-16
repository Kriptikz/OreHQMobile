package com.example.orehqmobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.orehqmobile.ui.screens.home_screen.HomeScreen
import com.example.orehqmobile.ui.screens.home_screen.HomeScreenViewModel

@Composable
fun OreHQMobileApp(
    homeScreenViewModel: HomeScreenViewModel = viewModel(factory = HomeScreenViewModel.Factory),
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "homeScreen") {
        composable("homeScreen") {
            HomeScreen(
                homeUiState = homeScreenViewModel.homeUiState,
                onIncreaseSelectedThreads = { homeScreenViewModel.increaseSelectedThreads() },
                onDecreaseSelectedThreads = { homeScreenViewModel.decreaseSelectedThreads() },
                onRunBenchmark = { homeScreenViewModel.mine() }
            )
        }

    }
}