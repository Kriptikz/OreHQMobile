package com.example.orehqmobile.ui.screens.home_screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.orehqmobile.ui.screens.OreHQMobileScaffold
import com.example.orehqmobile.ui.theme.OreHQMobileTheme

@Composable
fun HomeScreen(
    homeUiState: HomeUiState,
    serviceRunning: Boolean,
    threadCount: Int,
    hashpower: UInt,
    difficulty: UInt,
    lastDifficulty: UInt,
    onIncreaseSelectedThreads: () -> Unit,
    onDecreaseSelectedThreads: () -> Unit,
    onToggleMining: () -> Unit,
    onClickSignup: () -> Unit,
    onClickConnectWallet: () -> Unit,
    onClickDepositSol: () -> Unit,
    onClickWithdrawSol: () -> Unit,
    onClickClaim: () -> Unit,
) {
    OreHQMobileScaffold(title = "Home", displayTopBar = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (homeUiState.isLoadingUi) {
                CircularProgressIndicator()
            } else {
                if (homeUiState.isSignedUp) {
                    MiningScreen(
                        homeUiState = homeUiState,
                        serviceRunning = serviceRunning,
                        threadCount = threadCount,
                        hashpower = hashpower,
                        difficulty = difficulty,
                        lastDifficulty = lastDifficulty,
                        onIncreaseSelectedThreads,
                        onDecreaseSelectedThreads,
                        onToggleMining,
                        onClickClaim,
                    )
                } else {
                    SignUpScreen(
                        homeUiState = homeUiState,
                        onClickSignUp = onClickSignup,
                        onClickConnectWallet = onClickConnectWallet,
                        onClickDepositSol = onClickDepositSol,
                        onClickWithdrawSol = onClickWithdrawSol,
                    )
                }

            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    OreHQMobileTheme {
        HomeScreen(
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
                topStake = 0.0,
                poolMultiplier = 0.0,
                isSignedUp = false,
                isProcessingSignup = false,
                isLoadingUi = false,
                secureWalletPubkey = null,
            ),
            serviceRunning = false,
            threadCount = 1,
            hashpower = 0u,
            difficulty = 0u,
            lastDifficulty = 0u,
            onDecreaseSelectedThreads = {},
            onIncreaseSelectedThreads = {},
            onToggleMining = {},
            onClickSignup = {},
            onClickConnectWallet = {},
            onClickDepositSol = {},
            onClickWithdrawSol = {},
            onClickClaim = {},
        )
    }
}

@Composable
fun MiningScreen(
    homeUiState: HomeUiState,
    serviceRunning: Boolean,
    threadCount: Int,
    hashpower: UInt,
    difficulty: UInt,
    lastDifficulty: UInt,
    onIncreaseSelectedThreads: () -> Unit,
    onDecreaseSelectedThreads: () -> Unit,
    onToggleMining: () -> Unit,
    onClickClaim: () -> Unit,
) {
    val difficulty = difficulty
    val lastDifficulty = lastDifficulty
    val availableThreads = homeUiState.availableThreads
    val claimableBalance = homeUiState.claimableBalance
    val walletTokenBalance = homeUiState.walletTokenBalance
    val activeMiners = homeUiState.activeMiners
    val poolBalance = homeUiState.poolBalance
    val topStake = homeUiState.topStake
    val poolMultiplier = homeUiState.poolMultiplier

    // Wallet ORE Balance
    Text(
        text = "Wallet: $walletTokenBalance ORE",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 32.dp)
    )

    Text(text = "Active Miners: $activeMiners", modifier = Modifier.padding(bottom = 8.dp))
    Text(
        text = "Pool Balance: ${String.format("%.11f", poolBalance)} ORE",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 32.dp)
    )
    Text(
        text = "Top Stake: ${String.format("%.11f", topStake)} ORE",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 32.dp)
    )
    Text(
        text = "Pool Multiplier: ${String.format("%.2f", poolMultiplier)}x",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 32.dp, bottom = 32.dp)
    )

    Text(text = "Hashpower: $hashpower", modifier = Modifier.padding(bottom = 8.dp))
    Text(text = "Last Difficulty: $lastDifficulty", modifier = Modifier.padding(bottom = 16.dp))
    Text(text = "Difficulty: $difficulty", modifier = Modifier.padding(bottom = 16.dp))

    // Thread count selector
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Text(text = "Threads:", modifier = Modifier.padding(end = 8.dp))
        IconButton(onClick = onDecreaseSelectedThreads) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease threads")
        }
        Text(
            text = threadCount.toString(),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(onClick = onIncreaseSelectedThreads) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase threads")
        }
        Text(text = "/ $availableThreads", modifier = Modifier.padding(start = 8.dp))
    }

    // Mining toggle button
    Button(
        onClick = onToggleMining,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (serviceRunning) "Stop Mining" else "Start Mining")
    }

    Text(
        text = "Claimable: ${String.format("%.11f", claimableBalance)} ORE",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 32.dp)
    )

    Button(
        onClick = onClickClaim,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Claim All")
    }

}

@Composable
fun SignUpScreen(
    homeUiState: HomeUiState,
    onClickSignUp: () -> Unit,
    onClickConnectWallet: () -> Unit,
    onClickDepositSol: () -> Unit,
    onClickWithdrawSol: () -> Unit,
) {
    Text("Sol Balance: ${homeUiState.solBalance}")

    if (homeUiState.solBalance >= 0.001005) {
        Button(
            onClick = onClickSignUp,
            enabled = !homeUiState.isProcessingSignup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Up")
        }
    } else {
        Text("Please deposit sol for signup")
    }

    if (homeUiState.secureWalletPubkey != null) {
        Button(
            onClick = onClickDepositSol,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Deposit Sol")
        }
        Button(
            onClick = onClickWithdrawSol,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Withdraw Sol")
        }
    } else {
        Button(
            onClick = onClickConnectWallet,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect Wallet")
        }
    }
}
