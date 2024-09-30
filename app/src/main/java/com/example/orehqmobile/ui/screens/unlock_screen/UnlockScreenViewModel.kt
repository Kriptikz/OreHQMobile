package com.example.orehqmobile.ui.screens.unlock_screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.example.orehqmobile.OreHQMobileApplication
import com.example.orehqmobile.data.repositories.IKeypairRepository
import com.example.orehqmobile.ui.screens.home_screen.HomeScreenViewModel
import com.example.orehqmobile.ui.screens.new_created_wallet_screen.CreatedWalletScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UnlockScreenState(
    var selectedIndex: Int,
    var passcode: IntArray,
)

class UnlockScreenViewModel(
    application: OreHQMobileApplication,
    private val keypairRepository: IKeypairRepository,
) : ViewModel() {
    var unlockScreenState: UnlockScreenState by mutableStateOf(
        UnlockScreenState(
            selectedIndex = 0,
            passcode = intArrayOf(-1, -1, -1, -1, -1, -1)
        )
    )
        private set

    fun setPasscodeValue(index: Int, value: Int, navController: NavController, homeScreenViewModel: HomeScreenViewModel) {
        var p = unlockScreenState.passcode.clone()

        p[index] = value

        var newIndex = 5
        if (unlockScreenState.selectedIndex < 5) {
            newIndex = unlockScreenState.selectedIndex + 1
        } else {
            finish(p, navController, homeScreenViewModel)
        }

        unlockScreenState = unlockScreenState.copy(
            selectedIndex = newIndex,
            passcode = p
        )
    }

    fun setSelectedIndex(newIndex: Int) {
        if (newIndex <= 5) {
            unlockScreenState = unlockScreenState.copy(
                selectedIndex = newIndex
            )
        }
    }

    fun deletePasscodeValue(index: Int) {
        var p = unlockScreenState.passcode.clone()

        p[index] = -1

        var newIndex = 0
        if (unlockScreenState.selectedIndex > 0) {
            newIndex = unlockScreenState.selectedIndex - 1
        }

        unlockScreenState = unlockScreenState.copy(
            selectedIndex = newIndex,
            passcode = p
        )

    }

    fun finish(passcode: IntArray, navController: NavController, homeScreenViewModel: HomeScreenViewModel) {
        var passcodeStr = ""
        for (pc in passcode) {
            if (pc == -1) {
                // TODO: handle not set value
            } else {
                passcodeStr += pc.toString()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (homeScreenViewModel.loadKeypair(passcodeStr)) {
                homeScreenViewModel.connectToWebsocket()
                homeScreenViewModel.fetchUiState()
                withContext(Dispatchers.Main) {
                    navController.navigate("homeScreen")
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OreHQMobileApplication)
                val keypairRepository = application.container.keypairRepository
                UnlockScreenViewModel(
                    application = application,
                    keypairRepository = keypairRepository,
                )
            }
        }
    }
}
