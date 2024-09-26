package com.example.orehqmobile.ui.screens.save_with_passcode_screen

import com.example.orehqmobile.ui.screens.new_created_wallet_screen.CreatedWalletScreenState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.orehqmobile.ui.screens.OreHQMobileScaffold
import com.example.orehqmobile.ui.screens.home_screen.HomeScreen
import com.example.orehqmobile.ui.screens.home_screen.HomeUiState
import com.example.orehqmobile.ui.theme.OreHQMobileTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign

@Composable
fun SaveWithPasscodeScreen(
    saveWithPasscodeScreenState: SaveWithPasscodeScreenState,
    onNumberPress: (index: Int, value: Int) -> Unit,
    setCurrentIndex: (index: Int) -> Unit,
    deletePasscodeValue: (index: Int) -> Unit
) {
    OreHQMobileScaffold(title = "New Wallet", displayTopBar = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Enter Passcode",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    saveWithPasscodeScreenState.passcode.forEachIndexed { index, value ->
                        PasscodeButton(
                            text = if (value == -1) "" else "•",
                            onClick = { setCurrentIndex(index) },
                            isSelected = index == saveWithPasscodeScreenState.selectedIndex
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberPadRow(numbers = listOf(1, 2, 3), onNumberPress = onNumberPress, saveWithPasscodeScreenState)
                NumberPadRow(numbers = listOf(4, 5, 6), onNumberPress = onNumberPress, saveWithPasscodeScreenState)
                NumberPadRow(numbers = listOf(7, 8, 9), onNumberPress = onNumberPress, saveWithPasscodeScreenState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { deletePasscodeValue(saveWithPasscodeScreenState.selectedIndex) },
                        modifier = Modifier
                            .size(80.dp)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    NumberButton(number = 0, onNumberPress = onNumberPress, saveWithPasscodeScreenState)
                    Spacer(modifier = Modifier.width(8.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { /* Handle Done action */ },
                        modifier = Modifier
                            .size(80.dp)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Done",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
fun PasscodeButton(text: String, onClick: () -> Unit, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun NumberPadRow(numbers: List<Int>, onNumberPress: (index: Int, value: Int) -> Unit, saveWithPasscodeScreenState: SaveWithPasscodeScreenState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center // Center the row
    ) {
        numbers.forEach { number ->
            Spacer(modifier = Modifier.width(8.dp))
            NumberButton(number = number, onNumberPress = onNumberPress, saveWithPasscodeScreenState)
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun NumberButton(number: Int, onNumberPress: (index: Int, value: Int) -> Unit, saveWithPasscodeScreenState: SaveWithPasscodeScreenState) {
    Button(
        onClick = { onNumberPress(saveWithPasscodeScreenState.selectedIndex, number) },
        modifier = Modifier
            .size(80.dp)
            .padding(4.dp), // Increased button size and reduced padding
        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.secondary)
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.titleLarge, // Using Material 3 typography
            fontSize = 24.sp // You can adjust this value as needed
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SaveWithPasscodeScreenPreview() {
    OreHQMobileTheme {
        SaveWithPasscodeScreen(
            SaveWithPasscodeScreenState(
                selectedIndex = 0,
                passcode = intArrayOf(0, 0, 0, 0, 0, 0)
            ),
            onNumberPress = { _, _ -> {}},
            setCurrentIndex = { _ -> {}},
            deletePasscodeValue = { _ -> {}},
        )
    }
}
