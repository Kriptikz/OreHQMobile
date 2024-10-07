package com.example.orehqmobile.ui.screens.home_screen

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.orehqmobile.data.entities.SubmissionResult
import com.example.orehqmobile.ui.screens.OreHQMobileScaffold
import com.example.orehqmobile.ui.theme.OreHQMobileTheme
import java.util.Calendar
import java.util.Locale
import kotlin.math.pow

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
    onClickDisconnectWallet: () -> Unit,
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
                if (homeUiState.isSignedUp && homeUiState.secureWalletPubkey != null) {
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
                        onClickDisconnectWallet = onClickDisconnectWallet,
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
                submissionResults = emptyList()
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
            onClickDisconnectWallet = {},
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

    val screenHeight = LocalConfiguration.current.screenHeightDp
    val listHeight = screenHeight / 3

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Wallet ORE Balance
            Text(
                text = "Wallet: $walletTokenBalance ORE",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(text = "Active Miners: $activeMiners", modifier = Modifier.padding(bottom = 8.dp))
            Text(
                text = "Pool Balance: ${String.format("%.11f", poolBalance)} ORE",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Top Stake: ${String.format("%.11f", topStake)} ORE",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Pool Multiplier: ${String.format("%.2f", poolMultiplier)}x",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )

            Text(text = "Hashpower: $hashpower", modifier = Modifier.padding(bottom = 8.dp))
            Text(
                text = "Last Difficulty: $lastDifficulty",
                modifier = Modifier.padding(bottom = 16.dp)
            )
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
                modifier = Modifier.padding(top = 16.dp)
            )

            val minimumBalanceReached = claimableBalance >= 0.005

            if (!minimumBalanceReached) {
                Text("Minimum claim amount is 0.005")
            }
            Button(
                onClick = onClickClaim,
                enabled = minimumBalanceReached,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Claim All")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(text = "Earned")
                Text(text = "Diff")
                Text(text = "Timestamp")
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(listHeight.dp)
                    .border(4.dp, Color.DarkGray)
            ) {
                items(homeUiState.submissionResults) { result ->
                    SubmissionResultItem(result)
                }
            }
        }

    }
}

@Composable
fun SubmissionResultItem(result: SubmissionResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        val earnings = (result.minerEarned.toDouble() / 10.0.pow(11.0))
        val calendar = Calendar.getInstance(Locale.getDefault())
        //get current date from ts
        calendar.timeInMillis = result.createdAt
        //return formatted date
        val date = android.text.format.DateFormat.format("E, dd MMM HH:mm:ss", calendar).toString()
        Text(text = "${"%.11f".format(earnings)}")
        Text(text = "${result.minerDifficulty}")
        Text(text = "$date")
    }
}

@Composable
fun SignUpScreen(
    homeUiState: HomeUiState,
    onClickSignUp: () -> Unit,
    onClickConnectWallet: () -> Unit,
    onClickDisconnectWallet: () -> Unit,
) {
    Text("Sol Balance: ${homeUiState.solBalance}")

    if (!homeUiState.isSignedUp) {
        if (homeUiState.solBalance >= 0.001005) {
            Text("Entry fee is 0.001005 SOL")
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
    }

    if (homeUiState.secureWalletPubkey != null) {
        Button(
            onClick = onClickDisconnectWallet,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect Wallet")
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
