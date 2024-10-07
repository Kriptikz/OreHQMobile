package com.kriptikz.orehqmobile.ui.screens.new_created_wallet_screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.kriptikz.orehqmobile.OreHQMobileApplication
import com.kriptikz.orehqmobile.data.models.Ed25519PublicKey
import com.kriptikz.orehqmobile.data.repositories.IKeypairRepository
import com.kriptikz.orehqmobile.data.repositories.IPoolRepository
import com.kriptikz.orehqmobile.data.repositories.ISolanaRepository
import com.kriptikz.orehqmobile.ui.screens.home_screen.HomeScreenViewModel
import com.kriptikz.orehqmobile.ui.screens.home_screen.HomeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.security.KeyPair

data class CreatedWalletScreenState(
    var phrase: String,
    var pubkey: String,
)

class CreatedWalletScreenViewModel(
    application: OreHQMobileApplication,
    private val keypairRepository: IKeypairRepository,
) : ViewModel() {
    private var keypair: KeyPair? = null
    var createdWalletScreenState: CreatedWalletScreenState by mutableStateOf(
        CreatedWalletScreenState(
            phrase = "",
            pubkey = ""
        )
    )
        private set

    fun generateNewWallet() {
        val generatedKeypair = keypairRepository.generateNewKeypairWithPhrase()
        val publicKey = generatedKeypair.keypair.public as Ed25519PublicKey

        keypair = generatedKeypair.keypair

        createdWalletScreenState = createdWalletScreenState.copy(
            phrase = generatedKeypair.phrase,
            pubkey = publicKey.toString(),
        )
    }

    fun saveWallet() {
        keypairRepository.saveKeypair(keypair!!)
    }



    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OreHQMobileApplication)
                val keypairRepository = application.container.keypairRepository
                CreatedWalletScreenViewModel(
                    application = application,
                    keypairRepository = keypairRepository,
                )
            }
        }
    }
}
