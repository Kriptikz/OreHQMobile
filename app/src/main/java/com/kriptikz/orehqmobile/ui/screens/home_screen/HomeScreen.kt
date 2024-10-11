package com.kriptikz.orehqmobile.ui.screens.home_screen

import android.widget.ScrollView
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.kriptikz.orehqmobile.data.daos.SubmissionResultDao
import com.kriptikz.orehqmobile.data.entities.SubmissionResult
import com.kriptikz.orehqmobile.ui.screens.OreHQMobileScaffold
import com.kriptikz.orehqmobile.ui.theme.OreHQMobileTheme
import java.util.Calendar
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    homeUiState: HomeUiState,
    serviceRunning: Boolean,
    setPowerSlider: (Float) -> Unit,
    onToggleMining: () -> Unit,
    onClickSignup: () -> Unit,
    onClickConnectWallet: () -> Unit,
    onClickDisconnectWallet: () -> Unit,
    onClickClaim: () -> Unit,
    onUpdateSelectedThreads: (Int) -> Unit,
) {
    OreHQMobileScaffold(title = "Home", displayTopBar = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (homeUiState.isLoadingUi) {
                CircularProgressIndicator()
            } else {
                if (homeUiState.isSignedUp) {// && homeUiState.secureWalletPubkey != null) {
                    MiningScreen(
                        homeUiState = homeUiState,
                        serviceRunning = serviceRunning,
                        setPowerSlider = setPowerSlider,
                        onToggleMining = onToggleMining,
                        onClickClaim = onClickClaim,
                        onUpdateSelectedThreads = onUpdateSelectedThreads,
                        onClickConnectWallet = onClickConnectWallet
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
    OreHQMobileTheme(darkTheme = true) {
        HomeScreen(
            HomeUiState(
                availableThreads = 1,
                hashRate = 0,
                difficulty = 0,
                selectedThreads =  1,
                claimableBalance = 0.0,
                solBalance = 0.0,
                walletTokenBalance = 0.0,
                activeMiners = 0,
                poolBalance = 0.0,
                poolMultiplier = 0.0,
                isSignedUp = true,
                isProcessingSignup = false,
                isLoadingUi = false,
                secureWalletPubkey = "",
                minerPubkey = "",
                submissionResults = emptyList(),
                powerSlider = 0f,
                isMiningSwitchOn = false,
                lastSubmissionAgo = 0,
            ),
            serviceRunning = false,
            setPowerSlider = {},
            onToggleMining = {},
            onClickSignup = {},
            onClickConnectWallet = {},
            onClickDisconnectWallet = {},
            onClickClaim = {},
            onUpdateSelectedThreads = {},
        )
    }
}

@Composable
fun MiningScreen(
    homeUiState: HomeUiState,
    serviceRunning: Boolean,
    setPowerSlider: (Float) -> Unit,
    onToggleMining: () -> Unit,
    onClickClaim: () -> Unit,
    onUpdateSelectedThreads: (Int) -> Unit,
    onClickConnectWallet: () -> Unit,
) {
    val availableThreads = homeUiState.availableThreads
    val oreBalance = homeUiState.walletTokenBalance
    val claimableBalance = homeUiState.claimableBalance
    val activeMiners = homeUiState.activeMiners
    val poolBalance = homeUiState.poolBalance
    val poolMultiplier = homeUiState.poolMultiplier

    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    var isPublicKeyCopied by remember { mutableStateOf(false) }
    var isClaimPublicKeyCopied by remember { mutableStateOf(false) }

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
            val pubkeyString = homeUiState.minerPubkey?.let {
                it
            } ?: "Error"
            PublicKeySection(
                text = "Miner Pubkey: ",
                pubkey = pubkeyString,
                onCopyPubkey = {
                    clipboardManager.setText(AnnotatedString(pubkeyString))
                    isPublicKeyCopied = true
                },
                isPublicKeyCopied = isPublicKeyCopied
            )

            Text(text = "Active Miners: $activeMiners", modifier = Modifier.padding(bottom = 8.dp))
            Text(
                text = "Pool Balance: ${String.format("%.11f", poolBalance)} ORE",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Pool Multiplier: ${String.format("%.2f", poolMultiplier)}x",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )

            Text(text = "Hashpower: ${homeUiState.hashRate}", modifier = Modifier.padding(bottom = 8.dp))
            Text(text = "Difficulty: ${homeUiState.difficulty}")

            val lastSubmissionTimestamp = if (homeUiState.submissionResults.isNotEmpty()) {
                homeUiState.submissionResults.first().createdAt
            } else {
                0
            }

            Text(text = "Last Submission: ${homeUiState.lastSubmissionAgo}s ago")

            // Thread count selector
            Column(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    Modifier.height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mining Power:",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (homeUiState.powerSlider >= 3) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning, high power usage!",
                                tint = Color.Yellow
                            )
                            Box(
                                Modifier
                                    .height(60.dp)
                                    .padding(start = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Warning: High power usage can degrade device!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (homeUiState.powerSlider == 3f) Color.Yellow else Color.Red,
                                )
                            }
                        }
                    }
                }

                Slider(
                    value = homeUiState.powerSlider,
                    onValueChange = { newValue ->
                        setPowerSlider(newValue)
                        val newThreadCount = newValue.roundToInt()
                        onUpdateSelectedThreads(newThreadCount)
                    },
                    steps = 3,
                    valueRange = 0f..4f,
                    colors = SliderDefaults.colors(
                        thumbColor = lerp(Color.Green, Color.Red, homeUiState.powerSlider / 4f),
                        activeTrackColor = lerp(Color.Green, Color.Red, homeUiState.powerSlider / 4f)
                    )
                )

                val steps = listOf("Min", "Low", "Med", "High", "Max")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    steps.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            color = when (index) {
                                0 -> Color.Green
                                2 -> Color.Yellow
                                4 -> Color.Red
                                else -> Color.Unspecified
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Text(
                    text = "Threads: ${homeUiState.selectedThreads} / $availableThreads",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
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
                Text("Minimum claim amount is 0.005", style = MaterialTheme.typography.labelSmall)
            }


            if (homeUiState.secureWalletPubkey == null) {
                Button(
                    onClick = onClickConnectWallet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }
            } else {
                if (oreBalance == null) {
                    Text("-0.004 first claim deduction.", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                    if (minimumBalanceReached) {
                        Text("Actual Claim Amount: ${claimableBalance - 0.004}", style = MaterialTheme.typography.labelLarge, color = Color.Green)
                    }
                }
                val claimPubkey = homeUiState.secureWalletPubkey ?: "Not Connected"
                PublicKeySection(
                    text = "Claim Pubkey: ",
                    pubkey = claimPubkey,
                    onCopyPubkey = {
                        clipboardManager.setText(AnnotatedString(claimPubkey))
                        isClaimPublicKeyCopied = true
                    },
                    isPublicKeyCopied = isClaimPublicKeyCopied
                )
                Button(
                    onClick = onClickClaim,
                    enabled = minimumBalanceReached,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Claim All")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(text = "Earned")
                Text(text = "Diff")
                Text(text = "Time")
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
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val earnings = (result.minerEarned.toDouble() / 10.0.pow(11.0))
        val calendar = Calendar.getInstance(Locale.getDefault())
        //get current date from ts
        calendar.timeInMillis = result.createdAt
        //return formatted date
        val date = android.text.format.DateFormat.format("HH:mm:ss", calendar).toString()
        val formattedEarnings = "%.11f".format(earnings)
        val (integerPart, fractionalPart) = formattedEarnings.split(".")
        val leadingZeros = fractionalPart.takeWhile { it == '0' }
        val remainingFraction = fractionalPart.dropWhile { it == '0' }
        val mod = Modifier.fillMaxWidth(0.33f)

    Box(modifier = mod, contentAlignment = Alignment.CenterEnd) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 8.sp)) {
                        append("$integerPart.$leadingZeros")
                    }
                    append(remainingFraction)
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Box(modifier = mod) {
            Text(text = "${result.minerDifficulty}", style = MaterialTheme.typography.bodyLarge)
        }

        Box(modifier = mod) {
            Text(text = "$date", style = MaterialTheme.typography.bodySmall)
        }
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

    if (!homeUiState.isSignedUp) {// && homeUiState.secureWalletPubkey != null) {
        Button(
            onClick = onClickSignUp,
            enabled = !homeUiState.isProcessingSignup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register Miner")
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


@Composable
fun PublicKeySection(text: String, pubkey: String, onCopyPubkey: () -> Unit, isPublicKeyCopied: Boolean) {
    Row {
        Text(
            text = "$text",
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .wrapContentWidth()
                .clickable(onClick = onCopyPubkey)
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pubkeyFirst5 = pubkey.take(5)
                val pubkeyLast5 = pubkey.takeLast(5)
                Text(
                    text = "$pubkeyFirst5...$pubkeyLast5",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isPublicKeyCopied) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Copied",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}