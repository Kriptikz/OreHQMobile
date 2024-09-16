package com.example.orehqmobile.ui.screens.home_screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
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
    onIncreaseSelectedThreads: () -> Unit,
    onDecreaseSelectedThreads: () -> Unit,
    onRunBenchmark: () -> Unit,
) {
    OreHQMobileScaffold(title = "Home") {
        val hashrate = homeUiState.hashRate
        val difficulty = homeUiState.difficulty
        val availableThreads = homeUiState.availableThreads
        val selectedThreads = homeUiState.selectedThreads

        Column(
            modifier = Modifier
                .fillMaxSize() // Fill the whole screen size
                .padding(16.dp),
            verticalArrangement = Arrangement.Center, // Center vertically
            horizontalAlignment = Alignment.CenterHorizontally // Center horizontally
        ) {
            // Text showing available threads
            Text(text = "Available Threads: $availableThreads", modifier = Modifier.padding(bottom = 8.dp))

            // Text showing the current hashrate
            Text(text = "Hashrate: $hashrate", modifier = Modifier.padding(bottom = 16.dp))
            Text(text = "Difficulty: $difficulty", modifier = Modifier.padding(bottom = 16.dp))

            // Thread count selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                IconButton(onClick = onDecreaseSelectedThreads) {
                    Text("▼")
                }
                Text(
                    text = "Threads: $selectedThreads",
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = onIncreaseSelectedThreads) {
                    Text("▲")
                }
            }

            // Button to run the benchmark
            Button(
                onClick = onRunBenchmark,
                modifier = Modifier.fillMaxWidth() // Make button fill width for better appearance on mobile
            ) {
                Text("Run Benchmark")
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
                hashRate = 0.0,
                difficulty = 0u,
                selectedThreads =  1,
            ),
            onDecreaseSelectedThreads = {},
            onIncreaseSelectedThreads = {},
            onRunBenchmark = {},
        )
    }
}