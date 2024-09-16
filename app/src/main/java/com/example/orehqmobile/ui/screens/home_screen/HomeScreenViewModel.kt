package com.example.orehqmobile.ui.screens.home_screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.orehqmobile.OreHQMobileApplication
import com.example.orehqmobile.data.repositories.ISolanaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    var availableThreads: Int,
    var hashRate: Double,
    var difficulty: UInt,
    var selectedThreads: Int,
)

class HomeScreenViewModel(
    application: OreHQMobileApplication,
    private val solanaRepository: ISolanaRepository
) : ViewModel() {
    var homeUiState: HomeUiState by mutableStateOf(
        HomeUiState(
            availableThreads = 1,
            hashRate = 0.0,
            difficulty = 0u,
            selectedThreads =  1,
        )
    )
        private set

    init {
        viewModelScope.launch(Dispatchers.Main) {
            val runtimeAvailableThreads = Runtime.getRuntime().availableProcessors()
            homeUiState = homeUiState.copy(availableThreads = runtimeAvailableThreads)
        }
    }

    fun decreaseSelectedThreads() {
        if (homeUiState.selectedThreads > 1) {
            homeUiState = homeUiState.copy(selectedThreads = homeUiState.selectedThreads - 1)
        }
    }

    fun increaseSelectedThreads() {
        if (homeUiState.selectedThreads < homeUiState.availableThreads) {
            homeUiState = homeUiState.copy(selectedThreads = homeUiState.selectedThreads + 1)
        }
    }

    fun mine() {
        // Launch a coroutine for heavy computation in the background
        viewModelScope.launch(Dispatchers.Default) { // Use Dispatchers.Default for CPU-bound work
            try {
                val challenge: List<UByte> = List(32) { 0.toUByte() }
                val startTime = System.nanoTime()
                val jobs = List(homeUiState.selectedThreads) {
                    async {
                        uniffi.drillxmobile.dxHash(challenge, 30uL, 0uL, 10000uL)
                    }
                }
                val results = jobs.awaitAll()
                val endTime = System.nanoTime()

                val bestResult = results.maxByOrNull { it.difficulty }
                val totalNoncesChecked = results.sumOf { it.noncesChecked }
                val elapsedTimeSeconds = (endTime - startTime) / 1_000_000_000.0
                val newHashrate = totalNoncesChecked.toDouble() / elapsedTimeSeconds

                withContext(Dispatchers.Main) {
                    homeUiState = homeUiState.copy(
                        hashRate = newHashrate,
                        difficulty = bestResult?.difficulty ?: 0u
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
//                    hashrate = "Error: ${e.message}"
//                    difficulty = "Error occurred"
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OreHQMobileApplication)
                val solanaRepository = application.container.solanaRepository
                HomeScreenViewModel(
                    application = application,
                    solanaRepository = solanaRepository,
                )
            }
        }
    }

}