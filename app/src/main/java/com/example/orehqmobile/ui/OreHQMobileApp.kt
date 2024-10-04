package com.example.orehqmobile.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.orehqmobile.service.OreHQMobileForegroundService
import com.example.orehqmobile.ui.screens.home_screen.HomeScreen
import com.example.orehqmobile.ui.screens.home_screen.HomeScreenViewModel
import com.example.orehqmobile.ui.screens.new_created_wallet_screen.CreatedWalletScreen
import com.example.orehqmobile.ui.screens.new_created_wallet_screen.CreatedWalletScreenViewModel
import com.example.orehqmobile.ui.screens.new_wallet_start_screen.NewWalletScreen
import com.example.orehqmobile.ui.screens.new_wallet_start_screen.NewWalletStartScreenViewModel
import com.example.orehqmobile.ui.screens.save_with_passcode_screen.SaveWithPasscodeScreen
import com.example.orehqmobile.ui.screens.save_with_passcode_screen.SaveWithPasscodeScreenViewModel
import com.example.orehqmobile.ui.screens.unlock_screen.UnlockScreen
import com.example.orehqmobile.ui.screens.unlock_screen.UnlockScreenViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@Composable
fun OreHQMobileApp(
    activity_sender: ActivityResultSender,
    hasEncryptedKey: Boolean = false,
    threadCount: Int,
    hashpower: UInt,
    difficulty: UInt,
    lastDifficulty: UInt,
    oreHqMobileService: OreHQMobileForegroundService? = null,
    onClickService: () -> Unit,
    serviceRunning: Boolean,
    homeScreenViewModel: HomeScreenViewModel = viewModel(factory = HomeScreenViewModel.Factory),
    createdWalletScreenViewModel: CreatedWalletScreenViewModel = viewModel(factory = CreatedWalletScreenViewModel.Factory),
    saveWithPasscodeScreenViewModel: SaveWithPasscodeScreenViewModel = viewModel(factory = SaveWithPasscodeScreenViewModel.Factory),
    unlockScreenViewModel: UnlockScreenViewModel = viewModel(factory = UnlockScreenViewModel.Factory),
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val startDestination = if(hasEncryptedKey) {
        "unlockScreen"
    } else {
        "newWalletStartScreen"
    }

    NavHost(navController, startDestination) {
        composable("homeScreen") {
            HomeScreen(
                homeUiState = homeScreenViewModel.homeUiState,
                serviceRunning = serviceRunning,
                threadCount = threadCount,
                hashpower = hashpower,
                difficulty = difficulty,
                lastDifficulty = lastDifficulty,
                onIncreaseSelectedThreads = { oreHqMobileService?.increaseSelectedThreads() },
                onDecreaseSelectedThreads = { oreHqMobileService?.decreaseSelectedThreads() },
                onToggleMining = onClickService,
                onClickSignup = { homeScreenViewModel.signUpClicked() },
                onClickConnectWallet = { homeScreenViewModel.connectSecureWallet(activity_sender) },
                onClickDepositSol = { homeScreenViewModel.depositSol(activity_sender) },
                onClickWithdrawSol = { homeScreenViewModel.withdrawSol(activity_sender) },
                onClickClaim = { homeScreenViewModel.onClaimClicked() },
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
        composable("unlockScreen") {
            UnlockScreen(
                unlockScreenState = unlockScreenViewModel.unlockScreenState,
                onNumberPress = { index, value ->
                    unlockScreenViewModel.setPasscodeValue(index, value, navController, homeScreenViewModel)
                },
                setCurrentIndex = { index ->
                    unlockScreenViewModel.setSelectedIndex(index)
                },
                deletePasscodeValue = { index ->
                    unlockScreenViewModel.deletePasscodeValue(index)
                },
                onFinished = { passcode ->
                    unlockScreenViewModel.finish(passcode, navController, homeScreenViewModel)
                }
            )
        }
    }
}