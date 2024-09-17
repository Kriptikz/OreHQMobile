package com.example.orehqmobile.ui.screens.home_screen

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.orehqmobile.OreHQMobileApplication
import com.example.orehqmobile.data.repositories.IKeypairRepository
import com.example.orehqmobile.data.repositories.IPoolRepository
import com.example.orehqmobile.data.repositories.ISolanaRepository
import com.example.orehqmobile.data.models.Ed25519PublicKey
import com.example.orehqmobile.data.models.ServerMessage
import com.example.orehqmobile.data.repositories.toLittleEndianByteArray
import com.funkatronics.encoders.Base58
import com.funkatronics.encoders.Base64
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import uniffi.drillxmobile.DxSolution
import java.security.KeyPair

data class HomeUiState(
    var availableThreads: Int,
    var hashRate: Double,
    var difficulty: UInt,
    var selectedThreads: Int,
)

@OptIn(ExperimentalStdlibApi::class)
class HomeScreenViewModel(
    application: OreHQMobileApplication,
    private val solanaRepository: ISolanaRepository,
    private val poolRepository: IPoolRepository,
    private val keypairRepository: IKeypairRepository,
) : ViewModel() {
    private var keypair: AsymmetricCipherKeyPair? = null
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

        viewModelScope.launch {
            // load keypair
            loadOrGenerateKeypair()

            try {
                val result = poolRepository.fetchTimestamp()
                result.fold(
                    onSuccess = { timestamp ->
                        Log.d("HomeScreenViewModel", "Fetched timestamp: $timestamp")
                        val tsBytes = timestamp.toLittleEndianByteArray()
                        val signatureResult = solanaRepository.signMessage(tsBytes, listOf(keypair!!))

                        val sig = signatureResult.signature
                        val publicKey = (keypair!!.public as Ed25519PublicKeyParameters).encoded

                        //val privateKey = (keypair!!.private as Ed25519PrivateKeyParameters).encoded

                        // Connect to WebSocket
                        viewModelScope.launch {
                            try {
                                poolRepository.connectWebSocket(timestamp, Base58.encodeToString(sig), Base58.encodeToString(publicKey)
                                ) { sendReadyMessage() }.collect { serverMessage ->
                                    // Handle incoming WebSocket data
                                    Log.d("HomeScreenViewModel", "Received WebSocket data: $serverMessage")
                                    // Process the data as needed
                                    when (serverMessage) {
                                      is ServerMessage.StartMining -> handleStartMining(serverMessage)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("HomeScreenViewModel", "WebSocket error: ${e.message}")
                                when (e) {
                                    is io.ktor.client.plugins.ClientRequestException -> {
                                        val errorBody = e.response.bodyAsText()
                                        Log.e("HomeScreenViewModel", "Error body: $errorBody")
                                    }
                                    is io.ktor.client.plugins.ServerResponseException -> {
                                        val errorBody = e.response.bodyAsText()
                                        Log.e("HomeScreenViewModel", "Error body: $errorBody")
                                    }
                                    else -> {
                                        Log.e("HomeScreenViewModel", "Unexpected error", e)
                                    }
                                }
                            }
                        }


                    },
                    onFailure = { error ->
                        Log.e("HomeScreenViewModel", "Error fetching timestamp", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("HomeScreenViewModel", "Unexpected error", e)
            }


        }
    }
    

    private suspend fun loadOrGenerateKeypair() {
        val password = "password"
        var loadedKeypair = keypairRepository.loadEncryptedKeypair(password)
        if (loadedKeypair == null) {
            loadedKeypair = keypairRepository.generateNewKeypair()
            keypairRepository.saveEncryptedKeypair(loadedKeypair!!, password)
        }

        val publicKey = loadedKeypair.public as Ed25519PublicKey
        Log.d("HomeScreenViewModel", "Public key: $publicKey")

        val privateKey = Ed25519PrivateKeyParameters(loadedKeypair.private.encoded, 0)
        keypair = AsymmetricCipherKeyPair(privateKey.generatePublicKey(), privateKey)

//        val keyPairJson = """
//        [${privateKey.encoded.map { it.toUByte().toInt() }.joinToString(",")},${publicKey.encoded.map { it.toUByte().toInt() }.joinToString(",")}]
//        """.trimIndent()
//
//        Log.d("HomeScreenViewModel", "Keypair JSON: $keyPairJson")
    }

    private fun sendReadyMessage() {
      viewModelScope.launch {
          try {
              val now = System.currentTimeMillis() / 1000 // Current time in seconds
              val msg = now.toLittleEndianByteArray()
              val sig = solanaRepository.signMessage(msg, listOf(keypair!!)).signature
              val publicKey = (keypair!!.public as Ed25519PublicKeyParameters).encoded

              val binData = ByteArray(1 + 32 + 8 + sig.size).apply {
                  this[0] = 0 // Ready message type
                  System.arraycopy(publicKey, 0, this, 1, 32)
                  System.arraycopy(msg, 0, this, 33, 8)
                  System.arraycopy(sig, 0, this, 41, sig.size)
              }

              poolRepository.sendWebSocketMessage(binData)
              Log.d("HomeScreenViewModel", "Sent Ready message")
          } catch (e: Exception) {
              Log.e("HomeScreenViewModel", "Error sending Ready message", e)
          }
      }
  }

    private fun sendSubmissionMessage(submission: DxSolution) {
        viewModelScope.launch {
            try {
                val bestHashBin = submission.digest.toUByteArray()
                val bestNonceBin = submission.nonce.toUByteArray()

                val hashNonceMessage = UByteArray(24)
                bestHashBin.copyInto(hashNonceMessage, 0, 0, 16)
                bestNonceBin.copyInto(hashNonceMessage, 16, 0, 8)

                val signature = solanaRepository.signMessage(hashNonceMessage.toByteArray(), listOf(keypair!!)).signature
                val publicKey = (keypair!!.public as Ed25519PublicKeyParameters).encoded

                // Convert signature to Base58 string
                val signatureBase58 = Base58.encodeToString(signature)

                val binData = ByteArray(57 + signatureBase58.toByteArray().size).apply {
                    this[0] = 2 // BestSolution Message
                    System.arraycopy(bestHashBin.toByteArray(), 0, this, 1, 16)
                    System.arraycopy(bestNonceBin.toByteArray(), 0, this, 17, 8)
                    System.arraycopy(publicKey, 0, this, 25, 32)
                    System.arraycopy(signatureBase58.toByteArray(), 0, this, 57, signatureBase58.toByteArray().size)
                }

                poolRepository.sendWebSocketMessage(binData)
                Log.d("HomeScreenViewModel", "Sent BestSolution message")
            } catch (e: Exception) {
                Log.e("HomeScreenViewModel", "Error sending BestSolution message", e)
            }
        }
    }

//    private fun UByteArray.toByteArray(): ByteArray = ByteArray(size) { this[it].toByte() }
//    private fun List<UByte>.toUByteArray(): UByteArray = UByteArray(size) { this[it] }

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

    fun fetchTimestamp() {
        viewModelScope.launch {
            try {
                val result = poolRepository.fetchTimestamp()
                result.fold(
                    onSuccess = { timestamp ->
                        Log.d("HomeScreenViewModel", "Fetched timestamp: $timestamp")
                    },
                    onFailure = { error ->
                        Log.e("HomeScreenViewModel", "Error fetching timestamp", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("HomeScreenViewModel", "Unexpected error", e)
            }
        }
    }

    private fun handleStartMining(startMining: ServerMessage.StartMining) {
        Log.d("HomeScreenViewModel", "Received StartMining: hash=${Base64.encodeToString(startMining.challenge.toByteArray())}, " +
            "nonceRange=${startMining.nonceRange}, cutoff=${startMining.cutoff}")
        // Launch a coroutine for heavy computation in the background
        viewModelScope.launch(Dispatchers.Default) { // Use Dispatchers.Default for CPU-bound work
            try {
                val challenge: List<UByte> = startMining.challenge.toList()
                val startTime = System.nanoTime()
                val maxCutoff = 10uL
                val cutoff = minOf(startMining.cutoff, maxCutoff)
                val jobs = List(homeUiState.selectedThreads) {
                    async {
                        uniffi.drillxmobile.dxHash(challenge, cutoff, 0uL, 10000uL)
                    }
                }
                val results = jobs.awaitAll()
                val endTime = System.nanoTime()

                val bestResult = results.maxByOrNull { it.difficulty }
                val totalNoncesChecked = results.sumOf { it.noncesChecked }
                val elapsedTimeSeconds = (endTime - startTime) / 1_000_000_000.0
                val newHashrate = totalNoncesChecked.toDouble() / elapsedTimeSeconds

                bestResult?.let { solution ->
                    Log.d("HomeScreenViewModel", "Send submission with diff: ${solution.difficulty}")
                    sendSubmissionMessage(solution)
                }

                sendReadyMessage()

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

                bestResult?.let { solution ->
                    Log.d("HomeScreenViewModel", "Send submission with diff: ${solution.difficulty}")
                    sendSubmissionMessage(solution)
                }

                sendReadyMessage()

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
                val poolRepository = application.container.poolRepository
                val keypairRepository = application.container.keypairRepository
                HomeScreenViewModel(
                    application = application,
                    solanaRepository = solanaRepository,
                    poolRepository = poolRepository,
                    keypairRepository = keypairRepository,
                )
            }
        }
    }

}