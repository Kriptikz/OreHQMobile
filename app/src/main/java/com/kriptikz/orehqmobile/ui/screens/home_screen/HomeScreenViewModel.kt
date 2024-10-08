package com.kriptikz.orehqmobile.ui.screens.home_screen

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
import com.kriptikz.orehqmobile.OreHQMobileApplication
import com.kriptikz.orehqmobile.data.database.AppRoomDatabase
import com.kriptikz.orehqmobile.data.entities.SubmissionResult
import com.kriptikz.orehqmobile.data.entities.Wallet
import com.kriptikz.orehqmobile.data.repositories.IKeypairRepository
import com.kriptikz.orehqmobile.data.repositories.IPoolRepository
import com.kriptikz.orehqmobile.data.repositories.ISolanaRepository
import com.kriptikz.orehqmobile.data.models.toLittleEndianByteArray
import com.kriptikz.orehqmobile.data.repositories.SubmissionResultRepository
import com.kriptikz.orehqmobile.data.repositories.WalletRepository
import com.funkatronics.encoders.Base58
import com.kriptikz.orehqmobile.data.entities.AppAccount
import com.kriptikz.orehqmobile.data.repositories.AppAccountRepository
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.successPayload
import com.solana.transaction.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import kotlin.math.min
import kotlin.math.pow

data class HomeUiState(
    var availableThreads: Int,
    var hashRate: UInt,
    var difficulty: UInt,
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
    var minerPubkey: String?,
    var submissionResults: List<SubmissionResult>
)

@OptIn(ExperimentalStdlibApi::class)
class HomeScreenViewModel(
    application: OreHQMobileApplication,
    private val solanaRepository: ISolanaRepository,
    private val poolRepository: IPoolRepository,
    private val keypairRepository: IKeypairRepository,
    private val walletRepository: WalletRepository,
    private val submissionResultRepository: SubmissionResultRepository,
    private val appAccountRepository: AppAccountRepository,
) : ViewModel() {
    private var poolAuthorityPubkey: String? = null
    private var isFetchingUiState = false
    var homeUiState: HomeUiState by mutableStateOf(
        HomeUiState(
            availableThreads = 1,
            hashRate = 0u,
            difficulty = 0u,
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
            minerPubkey = null,
            submissionResults = emptyList()
        )
    )
        private set

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

        loadSecureWallet()

        val runtimeAvailableThreads = Runtime.getRuntime().availableProcessors()
        homeUiState = homeUiState.copy(availableThreads = runtimeAvailableThreads)

        loadPoolAuthorityPubkey()

        viewModelScope.launch(Dispatchers.IO) {
            val appAccount = appAccountRepository.getAppAccount()

            if (appAccount != null) {
                homeUiState = homeUiState.copy(
                    isSignedUp = appAccount.isSignedUp,
                    isLoadingUi = false
                )
            }

            // Fetch miner claimable rewards every 1 minute
            while (true) {
                if (homeUiState.minerPubkey.isNullOrBlank()) {
                    homeUiState = homeUiState.copy(
                        minerPubkey = keypairRepository.getPubkey()?.toString()
                    )
                }
                val pk = homeUiState.minerPubkey
                if ( pk != null && homeUiState.isSignedUp) {
                    val balanceRewardsResult = poolRepository.fetchMinerRewards(pk)
                    balanceRewardsResult.fold(
                        onSuccess = { balance ->
                            homeUiState = homeUiState.copy(claimableBalance = balance)
                        },
                        onFailure = { error ->
                            Log.e("HomeScreenViewModel", "Error fetching wallet rewards balance", error)
                        }
                    )
                }

                val submissionResults = submissionResultRepository.getAllSubmissionResults()
                homeUiState = homeUiState.copy(submissionResults = submissionResults)


                delay(60_000) // Delay for 1 minute
            }
        }
    }

    private fun loadSecureWallet() {
        viewModelScope.launch(Dispatchers.IO) {
            val secureWallet = walletRepository.getAllWallets()

            if (secureWallet.isNotEmpty()) {
                Log.d(TAG, "Found connected wallet db data.")
                walletAdapter.authToken = secureWallet[0].authToken
                homeUiState = homeUiState.copy(
                    secureWalletPubkey = secureWallet[0].publicKey
                )
            } else {
                Log.d(TAG, "No connected wallet data in db.")
                homeUiState = homeUiState.copy(
                    isLoadingUi = false
                )
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

    fun fetchUiState() {
        viewModelScope.launch(Dispatchers.IO) {
        if (homeUiState.minerPubkey == null) {
            homeUiState = homeUiState.copy(
                minerPubkey = keypairRepository.getPubkey()?.toString()
            )
        }
        if (homeUiState.secureWalletPubkey == null) {
            val secureWallet = walletRepository.getAllWallets()
            if (secureWallet.isNotEmpty()) {
                walletAdapter.authToken = secureWallet[0].authToken
                homeUiState = homeUiState.copy(
                    secureWalletPubkey = secureWallet[0].publicKey
                )
            } else {
                Log.d(TAG, "No connected wallet data in db.")
            }
        }
        val pubkey = homeUiState.minerPubkey
        if (pubkey != null) {
            if (!isFetchingUiState) {
                isFetchingUiState = true

                val securePubkey = homeUiState.secureWalletPubkey
                Log.d("HomeScreenViewModel", "Mining Pubkey: $pubkey")
                Log.d("HomeScreenViewModel", "Connected Pubkey: $securePubkey")
                    try {
                        // Fetch wallet token balance
                        if (securePubkey != null) {
                            val solBalanceResult = poolRepository.fetchSolBalance(securePubkey)
                            solBalanceResult.fold(
                                onSuccess = { balance ->
                                    Log.d("HomeScreenViewModel", "GOT SOL BALANCE: $balance")
                                    homeUiState = homeUiState.copy(solBalance = balance)
                                },
                                onFailure = { error ->
                                    Log.e("HomeScreenViewModel", "Error fetching sol balance", error)
                                }
                            )

                            val balanceResult = poolRepository.fetchMinerBalance(securePubkey)
                            balanceResult.fold(
                                onSuccess = { balance ->
                                    homeUiState = homeUiState.copy(walletTokenBalance = balance)
                                },
                                onFailure = { error ->
                                    Log.e("HomeScreenViewModel", "Error fetching wallet token balance", error)
                                }
                            )
                        }


                        // Fetch miner claimable rewards
                        val balanceRewardsResult = poolRepository.fetchMinerRewards(pubkey)
                        balanceRewardsResult.fold(
                            onSuccess = { balance ->
                                if (appAccountRepository.getAppAccount() == null) {
                                    appAccountRepository.insertAppAccount(AppAccount(pubkey, true))
                                }
                                homeUiState = homeUiState.copy(
                                    claimableBalance = balance,
                                    isSignedUp = true,
                                )
                            },
                            onFailure = { error ->
                                Log.e("HomeScreenViewModel", "Error fetching wallet rewards balance", error)
                            }
                        )

                        val activeMinersCountResult = poolRepository.fetchActiveMinersCount()
                        activeMinersCountResult.fold(
                            onSuccess = { activeMinersCount ->
                                homeUiState = homeUiState.copy(activeMiners = activeMinersCount)
                            },
                            onFailure = { error ->
                                Log.e("HomeScreenViewModel", "Error fetching active miners count", error)
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
        val publicKey = homeUiState.secureWalletPubkey
        if (publicKey != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Fetch wallet token balance
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

    fun signUpClicked(activitySender: ActivityResultSender) {
        Log.d("HomeScreenViewModel", "Sign Up Clicked!")
        homeUiState = homeUiState.copy(isProcessingSignup = true)
        viewModelScope.launch(Dispatchers.IO) {
            val publicKey = homeUiState.minerPubkey
            val secureWalletPubkey = homeUiState.secureWalletPubkey
            if (publicKey != null && secureWalletPubkey != null) {
                val signedUp = poolRepository.signup(publicKey)
                signedUp.fold(
                    onSuccess = {
                        Log.d("HomeScreenViewModel", "Successfully signed up!")
                        appAccountRepository.insertAppAccount(AppAccount(publicKey, true))
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
                homeUiState = homeUiState.copy(isProcessingSignup = false)
            }
        }
    }

    fun onClaimClicked() {
        viewModelScope.launch(Dispatchers.IO) {
            val minerPubkey = homeUiState.minerPubkey
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

                    var kp = keypairRepository.loadAcKeypair()
                    val sig = Base58.encodeToString(solanaRepository.signMessage(msgBytes, listOf(kp!!)).signature)

                    val claimResult = poolRepository.claim(timestamp, sig, minerPubkey!!, receiverPubkey, claimAmountGrains)
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
                    homeUiState = homeUiState.copy(
                        secureWalletPubkey = pubkeyString
                    )
                    loadSolBalance()
                }
                is TransactionResult.NoWalletFound -> {
                    Log.e(TAG, "No MWA compatible app wallet found on device.")
                }
                is TransactionResult.Failure -> {
                    Log.e(TAG, "Error connecting to wallet: ${result.e.message}")
                }
            }
        }
    }

    public fun setPoolbalance(newPoolBalance: Double) {
        homeUiState = homeUiState.copy(
            poolBalance = newPoolBalance
        )
    }

    public fun setTopStake(newTopStake: Double) {
        homeUiState = homeUiState.copy(
            topStake = newTopStake
        )
    }

    public fun setPoolMultiplier(newPoolMultiplier: Double) {
        homeUiState = homeUiState.copy(
            poolMultiplier = newPoolMultiplier
        )
    }

    public fun setActiveMiners(newActiveMiners: Int) {
        homeUiState = homeUiState.copy(
            activeMiners = newActiveMiners
        )
    }

    fun disconnectSecureWallet(activity_sender: ActivityResultSender) {
        viewModelScope.launch {
            when (val result = walletAdapter.disconnect(activity_sender)) {
                is TransactionResult.Success -> {
                    walletRepository.deleteWallet(homeUiState.secureWalletPubkey!!)
                    homeUiState = homeUiState.copy(
                        secureWalletPubkey = null,
                        solBalance = 0.0
                    )
                }
                is TransactionResult.NoWalletFound -> {
                    Log.e(TAG, "No MWA compatible app wallet found on device.")
                }
                is TransactionResult.Failure -> {
                    Log.e(TAG, "Error connecting to wallet: ${result.e.message}")
                }
            }
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
                val walletRepository = application.container.walletRepository
                val submissionResultRepository = application.container.submissionResultRepository
                val appAccountRepository = application.container.appAccountRepository
                HomeScreenViewModel(
                    application = application,
                    solanaRepository = solanaRepository,
                    poolRepository = poolRepository,
                    keypairRepository = keypairRepository,
                    walletRepository = walletRepository,
                    submissionResultRepository = submissionResultRepository,
                    appAccountRepository = appAccountRepository,
                )
            }
        }
    }

}