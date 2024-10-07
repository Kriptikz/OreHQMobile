package com.kriptikz.orehqmobile.ui.screens.new_wallet_start_screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kriptikz.orehqmobile.ui.screens.OreHQMobileScaffold
import com.kriptikz.orehqmobile.ui.screens.home_screen.HomeScreen
import com.kriptikz.orehqmobile.ui.screens.home_screen.HomeUiState
import com.kriptikz.orehqmobile.ui.theme.OreHQMobileTheme

@Composable
fun NewWalletScreen(
    onButtonGenerate: () -> Unit,
) {
    OreHQMobileScaffold(title = "Generate Wallet", displayTopBar = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Looks like there is no Mining Hot Wallet for this app yet.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Get a pen & paper and click generate to get started!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = onButtonGenerate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NewWalletScreenPreview() {
    OreHQMobileTheme {
        NewWalletScreen(
            onButtonGenerate = {}
        )
    }
}
