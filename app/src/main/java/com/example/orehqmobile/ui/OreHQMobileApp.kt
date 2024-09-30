package com.example.orehqmobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.orehqmobile.ui.screens.home_screen.HomeScreen
import com.example.orehqmobile.ui.screens.home_screen.HomeScreenViewModel
import com.example.orehqmobile.ui.screens.new_created_wallet_screen.CreatedWalletScreen
import com.example.orehqmobile.ui.screens.new_created_wallet_screen.CreatedWalletScreenViewModel
import com.example.orehqmobile.ui.screens.new_wallet_start_screen.NewWalletScreen
import com.example.orehqmobile.ui.screens.new_wallet_start_screen.NewWalletStartScreenViewModel
import com.example.orehqmobile.ui.screens.save_with_passcode_screen.SaveWithPasscodeScreen
import com.example.orehqmobile.ui.screens.save_with_passcode_screen.SaveWithPasscodeScreenViewModel

@Composable
fun OreHQMobileApp(
    hasEncryptedKey: Boolean = false,
    homeScreenViewModel: HomeScreenViewModel = viewModel(factory = HomeScreenViewModel.Factory),
    createdWalletScreenViewModel: CreatedWalletScreenViewModel = viewModel(factory = CreatedWalletScreenViewModel.Factory),
    saveWithPasscodeScreenViewModel: SaveWithPasscodeScreenViewModel = viewModel(factory = SaveWithPasscodeScreenViewModel.Factory),
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val startDestination = if(hasEncryptedKey) {
        "homeScreen"
    } else {
        "newWalletStartScreen"
    }

    NavHost(navController, startDestination) {
        composable("homeScreen") {
            HomeScreen(
                homeUiState = homeScreenViewModel.homeUiState,
                onIncreaseSelectedThreads = { homeScreenViewModel.increaseSelectedThreads() },
                onDecreaseSelectedThreads = { homeScreenViewModel.decreaseSelectedThreads() },
                onToggleMining = { homeScreenViewModel.toggleMining() },
                onClickSignup = { homeScreenViewModel.signUpClicked() },
                onConnectToWebsocket = { homeScreenViewModel.connectToWebsocket() }
            )
        }
        composable("newWalletStartScreen") {
            NewWalletScreen(
                onButtonGenerate = {
                    createdWalletScreenViewModel.generateNewWallet()
                    navController.navigate("createdWalletScreen")
                }
            )
        }
        composable("createdWalletScreen") {
            CreatedWalletScreen(
                createdWalletScreenState = createdWalletScreenViewModel.createdWalletScreenState,
                onClickContinue = {
                    navController.navigate("saveWithPasscodeScreen")
                }
            )
        }
        composable("saveWithPasscodeScreen") {
            SaveWithPasscodeScreen(
                saveWithPasscodeScreenState = saveWithPasscodeScreenViewModel.saveWithPasscodeScreenState,
                onNumberPress = { index, value ->
                    saveWithPasscodeScreenViewModel.setPasscodeValue(index, value)
                },
                setCurrentIndex = { index ->
                    saveWithPasscodeScreenViewModel.setSelectedIndex(index)
                },
                deletePasscodeValue = { index ->
                    saveWithPasscodeScreenViewModel.deletePasscodeValue(index)
                },
                onFinished = { passcode ->
                    saveWithPasscodeScreenViewModel.finish(passcode, navController, homeScreenViewModel, createdWalletScreenViewModel)
                }
            )
        }
    }
}