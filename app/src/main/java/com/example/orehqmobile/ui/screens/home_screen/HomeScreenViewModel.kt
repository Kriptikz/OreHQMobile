package com.example.orehqmobile.ui.screens.home_screen

import android.net.Uri
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
import com.example.orehqmobile.data.database.AppRoomDatabase
import com.example.orehqmobile.data.entities.Wallet
import com.example.orehqmobile.data.repositories.IKeypairRepository
import com.example.orehqmobile.data.repositories.IPoolRepository
import com.example.orehqmobile.data.repositories.ISolanaRepository
import com.example.orehqmobile.data.models.toLittleEndianByteArray
import com.example.orehqmobile.data.repositories.WalletRepository
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.successPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import kotlin.math.pow

data class HomeUiState(
    var availableThreads: Int,
    var hashRate: UInt,
    var difficulty: UInt,
    var lastDifficulty: UInt,
    var selectedThreads: Int,
    var solBalance: Double,
    var claimableBalance: Double,
    var walletTokenBalance: Double,
    var activeMiners: Int,
    var poolBalance: Double,
    var poolMultiplier: Double,
    var topStake: Double,
    var isSignedUp: Boolean,
    var isProcessingSignup: Boolean,
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
            claimableBalance = 0.0,
            solBalance = 0.0,
            walletTokenBalance = 0.0,
            activeMiners = 0,
            poolBalance = 0.0,
            poolMultiplier = 0.0,
            topStake = 0.0,
            isSignedUp = false,
            isProcessingSignup = false,
            isLoadingUi = true,
            secureWalletPubkey = null,
        )
    )
        private set

    private val walletRepository: WalletRepository

    private val solanaUri = Uri.parse("https://orehqmobile.com")
    private val iconUri = Uri.parse("favicon.ico") // resolves to https://yourdapp.com/favicon.ico
    private val identityName = "Ore HQ Mobile"

    private val walletAdapter = MobileWalletAdapter(connectionIdentity =
        ConnectionIdentity(
            identityUri = solanaUri,
            iconUri = iconUri,
            identityName = identityName,
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

    fun signUpClicked() {
        Log.d("HomeScreenViewModel", "Sign Up Clicked!")
        homeUiState = homeUiState.copy(isProcessingSignup = true)
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
                withContext(Dispatchers.Main) {
                    homeUiState = homeUiState.copy(isProcessingSignup = false)
                }
            }
        }
    }

    fun onClaimClicked() {
        viewModelScope.launch(Dispatchers.IO) {
            val minerPubkey = Base58.encodeToString((keypair!!.public as Ed25519PublicKeyParameters).encoded)
            val receiverPubkey = homeUiState.secureWalletPubkey!!

            val result = poolRepository.fetchTimestamp()
            result.fold(
                onSuccess = { timestamp ->
                    Log.d(TAG, "Fetched timestamp: $timestamp")
                    val claimAmountGrains = (homeUiState.claimableBalance * 10.0.pow(11.0)).toULong()
                    val tsBytes = timestamp.toLittleEndianByteArray()
                    val receiverPubkeyBytes = Base58.decode(receiverPubkey)
                    val claimAmountBytes = claimAmountGrains.toLittleEndianByteArray()

                    var msgBytes = tsBytes + receiverPubkeyBytes + claimAmountBytes

                    val sig = Base58.encodeToString(solanaRepository.signMessage(msgBytes, listOf(keypair!!)).signature)

                    val claimResult = poolRepository.claim(timestamp, sig, minerPubkey, receiverPubkey, claimAmountGrains)
                    claimResult.fold(
                        onSuccess = {
                            Log.d(TAG, "Successfully queued claim request.")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Error claiming", error)
                        }
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Error fetching timestamp", error)
                }
            )
        }

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

                        Log.d(TAG, "GOT AUTH RESULT")
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

    fun getSignFunction(): (ByteArray) -> ByteArray {
        return { data ->
            keypair?.let { kp ->
                solanaRepository.signMessage(data, listOf(kp)).signature
            } ?: ByteArray(0)
        }
    }

    fun getPubkeyFunction(): () -> String {
        return {
            keypair?.let { kp ->
                val publicKey = (kp.public as Ed25519PublicKeyParameters).encoded
                Base58.encodeToString(publicKey)
            } ?: ""
        }
    }

    companion object {
        private const val TAG = "HomeScreenViewModel"
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