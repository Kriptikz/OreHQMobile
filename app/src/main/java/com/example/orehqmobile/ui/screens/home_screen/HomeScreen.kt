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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.orehqmobile.ui.screens.OreHQMobileScaffold
import com.example.orehqmobile.ui.theme.OreHQMobileTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen() {
    OreHQMobileScaffold(title = "Home") {
        val availableThreads = Runtime.getRuntime().availableProcessors()
        var hashrate by remember { mutableStateOf("please run the benchmark to get the hashrate") }
        var difficulty by remember { mutableStateOf("please run the benchmark to get the difficulty") }
        var threadCount by remember { mutableStateOf(availableThreads) }
        val scope = rememberCoroutineScope()

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
                IconButton(onClick = { if (threadCount > 1) threadCount-- }) {
                    Text("▼")
                }
                Text(
                    text = "Threads: $threadCount",
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = { if (threadCount < 50) threadCount++ }) {
                    Text("▲")
                }
            }

            // Button to run the benchmark
            Button(
                onClick = {
                    // Launch a coroutine for heavy computation in the background
                    scope.launch(Dispatchers.Default) { // Use Dispatchers.Default for CPU-bound work
                        try {
                            val challenge: List<UByte> = List(32) { 0.toUByte() }
                            val startTime = System.nanoTime()
                            val jobs = List(threadCount) {
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

                            withContext(Dispatchers.Main) {
                                hashrate = String.format("%.2f h/s", newHashrate)
                                difficulty = "Best diff: ${bestResult?.difficulty ?: "N/A"}"
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                hashrate = "Error: ${e.message}"
                                difficulty = "Error occurred"
                            }
                        }
                    }
                },
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
        HomeScreen()
    }
}