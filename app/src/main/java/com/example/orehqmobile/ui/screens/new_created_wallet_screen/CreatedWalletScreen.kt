package com.example.orehqmobile.ui.screens.new_created_wallet_screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.orehqmobile.ui.screens.OreHQMobileScaffold
import com.example.orehqmobile.ui.theme.OreHQMobileTheme
import kotlinx.coroutines.delay

@Composable
fun CreatedWalletScreen(
    createdWalletScreenState: CreatedWalletScreenState,
    onClickContinue: () -> Unit
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    var isPublicKeyCopied by remember { mutableStateOf(false) }

    OreHQMobileScaffold(title = "New Wallet", displayTopBar = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SecretPhraseSection(createdWalletScreenState.phrase)
            PublicKeySection(
                pubkey = createdWalletScreenState.pubkey,
                onCopyPubkey = {
                    clipboardManager.setText(AnnotatedString(createdWalletScreenState.pubkey))
                    isPublicKeyCopied = true
                },
                isPublicKeyCopied = isPublicKeyCopied
            )
            Button(
                onClick = onClickContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
fun SecretPhraseSection(phrase: String) {
    Column {
        Text(
            text = "Your Secret Phrase",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(phrase.split(" ")) { word ->
                PhraseWord(word)
            }
        }
    }
}

@Composable
fun PhraseWord(word: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.aspectRatio(2f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun PublicKeySection(pubkey: String, onCopyPubkey: () -> Unit, isPublicKeyCopied: Boolean) {
    Column {
        Text(
            text = "Public Key",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCopyPubkey)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pubkey,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
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

@Preview(showBackground = true)
@Composable
fun CreatedWalletScreenPreview() {
    OreHQMobileTheme {
        CreatedWalletScreen(
            createdWalletScreenState = CreatedWalletScreenState(
                phrase = "",
                pubkey = "",
                keypair = null
            ),
            onClickContinue = {}
        )
    }
}
