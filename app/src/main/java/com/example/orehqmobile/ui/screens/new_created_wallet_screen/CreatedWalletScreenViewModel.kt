package com.example.orehqmobile.ui.screens.new_created_wallet_screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.orehqmobile.OreHQMobileApplication
import com.example.orehqmobile.data.models.Ed25519PublicKey
import com.example.orehqmobile.data.repositories.IKeypairRepository
import com.example.orehqmobile.data.repositories.IPoolRepository
import com.example.orehqmobile.data.repositories.ISolanaRepository
import com.example.orehqmobile.ui.screens.home_screen.HomeUiState
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

data class CreatedWalletScreenState(
    var phrase: String,
    var pubkey: String,
    var keypair: AsymmetricCipherKeyPair?
)

class CreatedWalletScreenViewModel(
    application: OreHQMobileApplication,
    private val keypairRepository: IKeypairRepository,
) : ViewModel() {
    var createdWalletScreenState: CreatedWalletScreenState by mutableStateOf(
        CreatedWalletScreenState(
            phrase = "",
            pubkey = "",
            keypair = null
        )
    )
        private set

    fun generateNewWallet() {
        val generatedKeypair = keypairRepository.generateNewKeypairWithPhrase()
        val publicKey = generatedKeypair.keypair.public as Ed25519PublicKey


        val privateKey = Ed25519PrivateKeyParameters(generatedKeypair.keypair.private.encoded, 0)
        val keypair = AsymmetricCipherKeyPair(privateKey.generatePublicKey(), privateKey)


        createdWalletScreenState = createdWalletScreenState.copy(
            phrase = generatedKeypair.phrase,
            pubkey = publicKey.toString(),
            keypair = keypair,
        )
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
