package com.example.orehqmobile.ui.screens.save_with_passcode_screen

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.orehqmobile.OreHQMobileApplication
import com.example.orehqmobile.data.repositories.IKeypairRepository
import com.example.orehqmobile.data.repositories.IPoolRepository
import com.example.orehqmobile.data.repositories.ISolanaRepository
import com.example.orehqmobile.ui.screens.home_screen.HomeUiState

data class SaveWithPasscodeScreenState(
    var selectedIndex: Int,
    var passcode: IntArray,
)

class SaveWithPasscodeScreenViewModel(
    application: OreHQMobileApplication,
    private val keypairRepository: IKeypairRepository,
) : ViewModel() {
    var saveWithPasscodeScreenState: SaveWithPasscodeScreenState by mutableStateOf(
        SaveWithPasscodeScreenState(
            selectedIndex = 0,
            passcode = intArrayOf(-1, -1, -1, -1, -1, -1)
        )
    )
        private set

    fun setPasscodeValue(index: Int, value: Int) {
        var p = saveWithPasscodeScreenState.passcode.clone()

        p[index] = value

        var newIndex = 5
        if (saveWithPasscodeScreenState.selectedIndex < 5) {
            newIndex = saveWithPasscodeScreenState.selectedIndex + 1
        }

        saveWithPasscodeScreenState = saveWithPasscodeScreenState.copy(
            selectedIndex = newIndex,
            passcode = p
        )
    }

    fun setSelectedIndex(newIndex: Int) {
        if (newIndex <= 5) {
            saveWithPasscodeScreenState = saveWithPasscodeScreenState.copy(
                selectedIndex = newIndex
            )
        }
    }

    fun deletePasscodeValue(index: Int) {
        var p = saveWithPasscodeScreenState.passcode.clone()

        p[index] = -1

        var newIndex = 0
        if (saveWithPasscodeScreenState.selectedIndex > 0) {
            newIndex = saveWithPasscodeScreenState.selectedIndex - 1
        }

        saveWithPasscodeScreenState = saveWithPasscodeScreenState.copy(
            selectedIndex = newIndex,
            passcode = p
        )

    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OreHQMobileApplication)
                val keypairRepository = application.container.keypairRepository
                SaveWithPasscodeScreenViewModel(
                    application = application,
                    keypairRepository = keypairRepository,
                )
            }
        }
    }
}
