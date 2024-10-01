package com.example.orehqmobile.ui.screens.home_screen

import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.orehqmobile.OreHQMobileApplication
import com.example.orehqmobile.data.database.AppRoomDatabase
import com.example.orehqmobile.data.entities.Wallet
import com.example.orehqmobile.data.repositories.IKeypairRepository
import com.example.orehqmobile.data.repositories.IPoolRepository
import com.example.orehqmobile.data.repositories.ISolanaRepository
import com.example.orehqmobile.data.models.Ed25519PublicKey
import com.example.orehqmobile.data.models.ServerMessage
import com.example.orehqmobile.data.models.toLittleEndianByteArray
import com.example.orehqmobile.data.repositories.WalletRepository
import com.funkatronics.encoders.Base58
import com.funkatronics.encoders.Base64
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.successPayload
import com.solana.transaction.blockhash
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import uniffi.orehqmobileffi.DxSolution

data class HomeUiState(
    var availableThreads: Int,
    var hashRate: UInt,
    var difficulty: UInt,
    var lastDifficulty: UInt,
    var selectedThreads: Int,
    var isMiningEnabled: Boolean,
    var solBalance: Double,
    var claimableBalance: Double,
    var walletTokenBalance: Double,
    var activeMiners: Int,
    var poolBalance: Double,
    var poolMultiplier: Double,
    var topStake: Double,
    var isWebsocketConnected: Boolean,
    var isSignedUp: Boolean,
    var isLoadingUi: Boolean,
    var secureWalletPubkey: String?,
)

@OptIn(ExperimentalStdlibApi::class)
class HomeScreenViewModel(
    application: OreHQMobileApplication,
    private val solanaRepository: ISolanaRepository,
    private val poolRepository: IPoolRepository,
    private val keypairRepository: IKeypairRepository,
) : ViewModel() {
    private var keypair: AsymmetricCipherKeyPair? = null
    private var poolAuthorityPubkey: String? = null
    private var isFetchingUiState = false
    var homeUiState: HomeUiState by mutableStateOf(
        HomeUiState(
            availableThreads = 1,
            hashRate = 0u,
            difficulty = 0u,
            lastDifficulty = 0u,
            selectedThreads =  1,
            isMiningEnabled = false,
            claimableBalance = 0.0,
            solBalance = 0.0,
            walletTokenBalance = 0.0,
            activeMiners = 0,
            poolBalance = 0.0,
            poolMultiplier = 0.0,
            topStake = 0.0,
            isWebsocketConnected = false,
            isSignedUp = false,
            isLoadingUi = true,
            secureWalletPubkey = null,
        )
    )
        private set

    private val walletRepository: WalletRepository

    private val solanaUri = Uri.parse("https://orehqmobile.com")
    private val iconUri = Uri.parse("favicon.ico") // resolves to https://yourdapp.com/favicon.ico
    private val identityName = "Ore HQ Mobile"

    private var walletAdapter = MobileWalletAdapter(connectionIdentity =
        ConnectionIdentity(
            identityUri = solanaUri,
            iconUri = iconUri,
            identityName = identityName
        )
    )


    init {
        walletAdapter.blockchain = Solana.Mainnet

        val appDb = AppRoomDatabase.getInstance(application)
        val walletDao = appDb.walletDao()
        walletRepository = WalletRepository(walletDao)

        loadSecureWallet()

        val runtimeAvailableThreads = Runtime.getRuntime().availableProcessors()
        homeUiState = homeUiState.copy(availableThreads = runtimeAvailableThreads)

        loadPoolAuthorityPubkey()

        viewModelScope.launch(Dispatchers.IO) {
            // Fetch miner claimable rewards every 1 minute
            while (true) {
                if (keypair != null && homeUiState.isSignedUp) {
                    val publicKey = Base58.encodeToString((keypair!!.public as Ed25519PublicKeyParameters).encoded)
                    val balanceRewardsResult = poolRepository.fetchMinerRewards(publicKey)
                    balanceRewardsResult.fold(
                        onSuccess = { balance ->
                            homeUiState = homeUiState.copy(claimableBalance = balance)
                        },
                        onFailure = { error ->
                            Log.e("HomeScreenViewModel", "Error fetching wallet rewards balance", error)
                        }
                    )
                }
                delay(60_000) // Delay for 1 minute
            }
        }
    }

    fun setKeypair(newKeypair: AsymmetricCipherKeyPair) {
        keypair = newKeypair
    }

    private fun loadSecureWallet() {
        viewModelScope.launch(Dispatchers.IO) {
            val secureWallet = walletRepository.getAllWallets()

            if (secureWallet.size > 0) {
                Log.d("HomeScreenViewModel", "Found secure wallet db data.")
                withContext(Dispatchers.Main) {
                    walletAdapter.authToken = secureWallet[0].authToken
                    homeUiState = homeUiState.copy(
                        secureWalletPubkey = secureWallet[0].publicKey
                    )
                }
            } else {
                Log.d("HomeScreenViewModel", "No secure wallet data in db.")
            }
        }
    }

    private fun loadPoolAuthorityPubkey() {
        viewModelScope.launch(Dispatchers.IO) {
            val poolAuthPubkeyResult = poolRepository.fetchPoolAuthorityPubkey()
            poolAuthPubkeyResult.fold(
                onSuccess = { pubkey ->
                    withContext(Dispatchers.Main) {
                        poolAuthorityPubkey = pubkey
                    }
                },
                onFailure = { error ->
                    Log.e("HomeScreenViewModel", "Error fetching wallet rewards balance", error)
                }
            )
        }
    }

    suspend fun loadKeypair(password: String): Boolean {
        var loadedKeypair = keypairRepository.loadEncryptedKeypair(password) ?: return false

        val privateKey = Ed25519PrivateKeyParameters(loadedKeypair.private.encoded, 0)
        keypair = AsymmetricCipherKeyPair(privateKey.generatePublicKey(), privateKey)

        return true
    }

    fun connectToWebsocket() {
        if (!homeUiState.isWebsocketConnected && homeUiState.isSignedUp) {
            viewModelScope.launch(Dispatchers.IO) {
                homeUiState = homeUiState.copy(isWebsocketConnected = true)
                val result = poolRepository.fetchTimestamp()
                result.fold(
                    onSuccess = { timestamp ->
                        Log.d("HomeScreenViewModel", "Fetched timestamp: $timestamp")
                        val tsBytes = timestamp.toLittleEndianByteArray()

                        if (keypair != null) {
                            Log.d("HomeScreenViewModel", "Keypair is not null")
                        } else {
                            Log.d("HomeScreenViewModel", "Keypair is null")

                        }
                        val signatureResult = solanaRepository.signMessage(tsBytes, listOf(keypair!!))

                        val sig = signatureResult.signature
                        val publicKey = (keypair!!.public as Ed25519PublicKeyParameters).encoded

                        //val privateKey = (keypair!!.private as Ed25519PrivateKeyParameters).encoded

                        // Connect to WebSocket
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                poolRepository.connectWebSocket(timestamp, Base58.encodeToString(sig), Base58.encodeToString(publicKey)
                                ).collect { serverMessage ->
                                    // Handle incoming WebSocket data
                                    Log.d("HomeScreenViewModel", "Received WebSocket data: $serverMessage")
                                    // Process the data as needed
                                    when (serverMessage) {
                                        is ServerMessage.StartMining -> handleStartMining(serverMessage)
                                        is ServerMessage.PoolSubmissionResult -> handlePoolSubmissionResult(serverMessage)
                                    }
                                }

                                homeUiState = homeUiState.copy(isWebsocketConnected = false)
                            } catch (e: Exception) {
                                homeUiState = homeUiState.copy(isWebsocketConnected = false)
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

                            homeUiState = homeUiState.copy(
                                isWebsocketConnected = false,
                                isMiningEnabled = false,
                            )
                        }


                    },
                    onFailure = { error ->
                        homeUiState = homeUiState.copy(
                            isWebsocketConnected = false,
                            isMiningEnabled = false,
                        )
                        Log.e("HomeScreenViewModel", "Error fetching timestamp", error)
                    }
                )

            }
        }
    }

    suspend fun fetchUiState() {
        if (keypair != null) {
            if (!isFetchingUiState) {
                isFetchingUiState = true

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        // Fetch wallet token balance
                        val publicKey = Base58.encodeToString((keypair!!.public as Ed25519PublicKeyParameters).encoded)

                        val solBalanceResult = poolRepository.fetchSolBalance(publicKey)
                        solBalanceResult.fold(
                            onSuccess = { balance ->
                                homeUiState = homeUiState.copy(solBalance = balance)
                            },
                            onFailure = { error ->
                                Log.e("HomeScreenViewModel", "Error fetching sol balance", error)
                            }
                        )

                        val balanceResult = poolRepository.fetchMinerBalance(publicKey)
                        balanceResult.fold(
                            onSuccess = { balance ->
                                homeUiState = homeUiState.copy(walletTokenBalance = balance)
                            },
                            onFailure = { error ->
                                Log.e("HomeScreenViewModel", "Error fetching wallet token balance", error)
                            }
                        )

                        // Fetch miner claimable rewards
                        val balanceRewardsResult = poolRepository.fetchMinerRewards(publicKey)
                        balanceRewardsResult.fold(
                            onSuccess = { balance ->
                                homeUiState = homeUiState.copy(
                                    claimableBalance = balance,
                                    isSignedUp = true,
                                )
                            },
                            onFailure = { error ->
                                homeUiState = homeUiState.copy(isSignedUp = false)
                                Log.e("HomeScreenViewModel", "Error fetching wallet rewards balance", error)
                            }
                        )

                        val activeMinersCountResult = poolRepository.fetchActiveMinersCount()
                        activeMinersCountResult.fold(
                            onSuccess = { activeMinersCount ->
                                homeUiState = homeUiState.copy(activeMiners = activeMinersCount)
                            },
                            onFailure = { error ->
                                Log.e("HomeScreenViewModel", "Error fetching wallet rewards balance", error)
                            }
                        )


                        Log.d("HomeScreenViewModel", "Fetching pool balance")
                        val poolBalanceResult = poolRepository.fetchPoolBalance()
                        poolBalanceResult.fold(
                            onSuccess = { balance ->
                                Log.d("HomeScreenViewModel", "SUCCESS fetching pool balance")
                                homeUiState = homeUiState.copy(poolBalance = balance)
                            },
                            onFailure = { error ->
                                Log.e("HomeScreenViewModel", "Error fetching pool balance", error)
                            }
                        )

                        val poolMultiplierResult = poolRepository.fetchPoolMultiplier()
                        poolMultiplierResult.fold(
                            onSuccess = { data ->
                                homeUiState = homeUiState.copy(poolMultiplier = data)
                            },
                            onFailure = { error ->
                                Log.e("HomeScreenViewModel", "Error fetching pool multiplier", error)
                            }
                        )

                        homeUiState.isLoadingUi = false

                        // TODO add top stake fetch

                    } catch (e: Exception) {
                        Log.e("HomeScreenViewModel", "Unexpected error", e)
                    }
                }
            }

        }
    }

    private suspend fun loadSolBalance() {
        val kp = keypair
        if (kp != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Fetch wallet token balance
                    val publicKey = Base58.encodeToString((kp.public as Ed25519PublicKeyParameters).encoded)

                    val solBalanceResult = poolRepository.fetchSolBalance(publicKey)
                    solBalanceResult.fold(
                        onSuccess = { balance ->
                            homeUiState = homeUiState.copy(solBalance = balance)
                        },
                        onFailure = { error ->
                            Log.e("HomeScreenViewModel", "Error fetching sol balance", error)
                        }
                    )
                } catch (e: Exception) {
                    Log.e("HomeScreenViewModel", "Unexpected error", e)
                }
            }

        }
    }

//    private suspend fun loadOrGenerateKeypair() {
//        val password = "password"
//        var loadedKeypair = keypairRepository.loadEncryptedKeypair(password)
//        if (loadedKeypair == null) {
//            loadedKeypair = keypairRepository.generateNewKeypair()
//            keypairRepository.saveEncryptedKeypair(loadedKeypair!!, password)
//        }
//
//        val publicKey = loadedKeypair.public as Ed25519PublicKey
//        Log.d("HomeScreenViewModel", "Public key: $publicKey")
//
//        val privateKey = Ed25519PrivateKeyParameters(loadedKeypair.private.encoded, 0)
//        keypair = AsymmetricCipherKeyPair(privateKey.generatePublicKey(), privateKey)
//
////        val keyPairJson = """
////        [${privateKey.encoded.map { it.toUByte().toInt() }.joinToString(",")},${publicKey.encoded.map { it.toUByte().toInt() }.joinToString(",")}]
////        """.trimIndent()
////
////        Log.d("HomeScreenViewModel", "Keypair JSON: $keyPairJson")
//    }

    private fun sendReadyMessage() {
      viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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

    private fun handleStartMining(startMining: ServerMessage.StartMining) {
        Log.d("HomeScreenViewModel", "Received StartMining: hash=${Base64.encodeToString(startMining.challenge.toByteArray())}, " +
            "nonceRange=${startMining.nonceRange}, cutoff=${startMining.cutoff}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val challenge: List<UByte> = startMining.challenge.toList()

                var currentNonce = startMining.nonceRange.first
                val lastNonce = startMining.nonceRange.last
                val noncesPerThread = 10_000uL
                var secondsOfRuntime = startMining.cutoff + 5uL

                var bestDifficulty = 0u

                while(homeUiState.isMiningEnabled) {
                    val startTime = System.nanoTime()
                    Log.d("HomeScreenViewModel", "Seconds of run time: $secondsOfRuntime")
                    val maxBatchRuntime = 10uL // 10 seconds
                    val currentBatchMaxRuntime = minOf(secondsOfRuntime, maxBatchRuntime)
                    val jobs = List(homeUiState.selectedThreads) {
                        val nonceForThisJob = currentNonce
                        currentNonce += noncesPerThread
                        async(Dispatchers.Default) {
                            uniffi.orehqmobileffi.dxHash(challenge, currentBatchMaxRuntime, nonceForThisJob, lastNonce)
                        }
                    }
                    val results = jobs.awaitAll()
                    val endTime = System.nanoTime()

                    val totalNoncesChecked = results.sumOf { it.noncesChecked }
                    val elapsedTimeSeconds = if (endTime >= startTime) {
                        (endTime - startTime) / 1_000_000_000.0
                    } else {
                        0.0
                    }

                    if (secondsOfRuntime >= elapsedTimeSeconds.toUInt()) {
                        secondsOfRuntime -= elapsedTimeSeconds.toUInt()
                    }
                    val newHashrate = (totalNoncesChecked.toDouble() / elapsedTimeSeconds).toUInt()

                    val bestResult = results.maxByOrNull { it.difficulty }
                    if (bestResult != null) {
                        if (bestResult.difficulty > bestDifficulty) {
                            bestDifficulty = bestResult.difficulty
                            bestResult.let { solution ->
                                Log.d("HomeScreenViewModel", "Send submission with diff: ${solution.difficulty}")
                                sendSubmissionMessage(solution)
                            }

                            homeUiState = homeUiState.copy(
                                difficulty = bestResult.difficulty ?: 0u
                            )
                        }
                    }

                    Log.d("HomeScreenViewModel", "Hashpower: $newHashrate")
                    homeUiState = homeUiState.copy(
                        hashRate = newHashrate,
                    )

                    if (secondsOfRuntime <= 2u) {
                        break;
                    }
                }

                if (homeUiState.isMiningEnabled) {
                    sendReadyMessage()
                }
                // reset best diff
                homeUiState = homeUiState.copy(
                    difficulty = 0u,
                    lastDifficulty = homeUiState.difficulty
                )
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
//                    hashrate = "Error: ${e.message}"
//                    difficulty = "Error occurred"
                }
            }
        }
    }

    private fun handlePoolSubmissionResult(poolSubmissionResult: ServerMessage.PoolSubmissionResult) {
        Log.d("HomeScreenViewModel", "Received pool submission result:")
        Log.d("HomeScreenViewModel", "Difficulty: ${poolSubmissionResult.difficulty}")
        Log.d("HomeScreenViewModel", "Total Balance: ${"%.11f".format(poolSubmissionResult.totalBalance)}")
        Log.d("HomeScreenViewModel", "Total Rewards: ${"%.11f".format(poolSubmissionResult.totalRewards)}")
        Log.d("HomeScreenViewModel", "Top Stake: ${"%.11f".format(poolSubmissionResult.topStake)}")
        Log.d("HomeScreenViewModel", "Multiplier: ${"%.11f".format(poolSubmissionResult.multiplier)}")
        Log.d("HomeScreenViewModel", "Active Miners: ${poolSubmissionResult.activeMiners}")
        Log.d("HomeScreenViewModel", "Challenge: ${poolSubmissionResult.challenge.joinToString(", ")}")
        Log.d("HomeScreenViewModel", "Best Nonce: ${poolSubmissionResult.bestNonce}")
        Log.d("HomeScreenViewModel", "Miner Supplied Difficulty: ${poolSubmissionResult.minerSuppliedDifficulty}")
        Log.d("HomeScreenViewModel", "Miner Earned Rewards: ${"%.11f".format(poolSubmissionResult.minerEarnedRewards)}")
        Log.d("HomeScreenViewModel", "Miner Percentage: ${"%.11f".format(poolSubmissionResult.minerPercentage)}")

        homeUiState = homeUiState.copy(
            activeMiners = poolSubmissionResult.activeMiners.toInt(),
            poolBalance = poolSubmissionResult.totalBalance,
            topStake = poolSubmissionResult.topStake,
            poolMultiplier = poolSubmissionResult.multiplier,
        )
    }

    fun signUpClicked() {
        Log.d("HomeScreenViewModel", "Sign Up Clicked!")
        viewModelScope.launch(Dispatchers.IO) {
            if (keypair != null) {
                    val latestBlockhash = poolRepository.fetchLatestBlockhash()
                    latestBlockhash.fold(
                        onSuccess = { latestBlockhash ->
                            val publicKey = solanaRepository.base58Encode((keypair!!.public as Ed25519PublicKeyParameters).encoded)
                            val txn = solanaRepository.getSolTransferTransaction(latestBlockhash, publicKey, poolAuthorityPubkey!!, 1_000_000uL)
                            if (txn != null) {
                                var signedTxn = solanaRepository.signTransaction(txn.serialize(), listOf(keypair!!))
                                var signedTxnStr = solanaRepository.base64Encode(signedTxn.signedPayload)
                                val signedUp = poolRepository.signup(publicKey, signedTxnStr)
                                signedUp.fold(
                                    onSuccess = {
                                        Log.d("HomeScreenViewModel", "Successfully signed up!")

                                        withContext(Dispatchers.Main) {
                                            homeUiState = homeUiState.copy(
                                                isSignedUp = true
                                            )
                                        }
                                    },
                                    onFailure = { error ->
                                        Log.e("HomeScreenViewModel", "Error processing signup request", error)
                                    }
                                )
                            } else {
                                Log.e("HomeScreenViewModel", "Failed to getSolTransferTransaction for signup.")
                            }

                        },
                        onFailure = { error ->
                            Log.e("HomeScreenViewModel", "Error fetching latest blockhash", error)
                        }
                    )
            }
        }
    }

    fun toggleMining() {
        val toggled = !homeUiState.isMiningEnabled

        if (toggled) {
            sendReadyMessage()
        }

        homeUiState = homeUiState.copy(
            isMiningEnabled = toggled
        )
    }

    fun connectSecureWallet(activity_sender: ActivityResultSender) {
        viewModelScope.launch {
            when (val result = walletAdapter.connect(activity_sender)) {
                is TransactionResult.Success -> {
                    val pubkeyString = solanaRepository.base58Encode(result.authResult.accounts[0].publicKey)
                    walletRepository.insertWallet(Wallet(pubkeyString, result.authResult.authToken))
                    withContext(Dispatchers.Main) {
                        Log.d("HomeScreenViewModel", "SignInResult IS NOT NULL")
                        homeUiState = homeUiState.copy(
                            secureWalletPubkey = pubkeyString
                        )
                    }
                }
                is TransactionResult.NoWalletFound -> {
                    Log.e("HomeScreenViewModel", "No MWA compatible app wallet found on device.")
                }
                is TransactionResult.Failure -> {
                    Log.e("HomeScreenViewModel", "Error connecting to wallet: ${result.e.message}")
                }
            }
        }
    }

    fun depositSol(activity_sender: ActivityResultSender) {
        viewModelScope.launch {
            val latestBlockhash = poolRepository.fetchLatestBlockhash()
            latestBlockhash.fold(
                onSuccess = { latestBlockhash ->
                    val result = walletAdapter.transact(activity_sender) { authResult ->
                        val senderPubkey = homeUiState.secureWalletPubkey!!
                        val receiverPubkey = solanaRepository.base58Encode((keypair!!.public as Ed25519PublicKeyParameters).encoded)

                        val txn = solanaRepository.getSolTransferTransaction(latestBlockhash, senderPubkey, receiverPubkey, 1_005_000uL)
                        signAndSendTransactions(arrayOf(txn!!.serialize()))
                    }
                    when (result) {
                        is TransactionResult.Success -> {
                            val txSignatureBytes = result.successPayload?.signatures?.first()
                            txSignatureBytes?.let {
                                val sig = solanaRepository.base58Encode(it)
                                Log.d("HomeScreenViewModel", "Sig: $sig")
                                loadSolBalance()
                            }
                        }
                        is TransactionResult.NoWalletFound -> {
                            Log.e("HomeScreenViewModel", "No MWA compatible app wallet found on device.")
                        }
                        is TransactionResult.Failure -> {
                            Log.e("HomeScreenViewModel", "Error connecting to wallet: ${result.e.message}")
                        }
                    }
                },
                onFailure = { error ->
                    Log.e("HomeScreenViewModel", "Error fetching latest blockhash", error)
                }
            )
        }
    }

    fun withdrawSol(activity_sender: ActivityResultSender) {
        viewModelScope.launch(Dispatchers.IO) {
            val latestBlockhash = poolRepository.fetchLatestBlockhash()
            latestBlockhash.fold(
                onSuccess = { latestBlockhash ->
                    val result = walletAdapter.transact(activity_sender) { authResult ->
                        val receiverPubkey = homeUiState.secureWalletPubkey!!
                        val senderPubkey = solanaRepository.base58Encode((keypair!!.public as Ed25519PublicKeyParameters).encoded)

                        val txn = solanaRepository.getSolTransferTransactionWithFeePayer(latestBlockhash, senderPubkey, receiverPubkey, receiverPubkey, 1_005_000uL)
                        var partialSignedTxn = solanaRepository.signTransaction(txn!!.serialize(), listOf(keypair!!)).signedPayload


                        signAndSendTransactions(arrayOf(partialSignedTxn))
                    }
                    when (result) {
                        is TransactionResult.Success -> {
                            val txSignatureBytes = result.successPayload?.signatures?.first()
                            txSignatureBytes?.let {
                                val sig = solanaRepository.base58Encode(it)
                                Log.d("HomeScreenViewModel", "Sig: $sig")
                                loadSolBalance()
                            }
                        }
                        is TransactionResult.NoWalletFound -> {
                            Log.e("HomeScreenViewModel", "No MWA compatible app wallet found on device.")
                        }
                        is TransactionResult.Failure -> {
                            Log.e("HomeScreenViewModel", "Error connecting to wallet: ${result.e.message}")
                        }
                    }
                },
                onFailure = { error ->
                    Log.e("HomeScreenViewModel", "Error fetching latest blockhash", error)
                }
            )
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