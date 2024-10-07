package com.kriptikz.orehqmobile.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OreHQMobileScaffold(title: String, displayTopBar: Boolean, modifier: Modifier = Modifier, screenContent: @Composable () -> Unit) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.padding(top = statusBarPadding.calculateTopPadding())
            ) {
                if (displayTopBar) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(40.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(start = 16.dp)
                    ) {
                        Box {
//                            Icon(
//                                Icons.Default.Menu,
//                                contentDescription = null,
//                                tint = Color.White,
//                            )
                            Text(
                                text = title,
                                style = TextStyle(
                                    color = Color.White,
                                ),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                        }
                    }

                }
            }
        },
//        bottomBar = {
//            Row(
//                horizontalArrangement = Arrangement.SpaceEvenly,
//                verticalAlignment = Alignment.CenterVertically,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(40.dp)
//                    .background(Color.Black)
//            ) {
//                Icon(Icons.Default.Menu, contentDescription = null, tint = Color.Gray)
//                Icon(Icons.Default.Home, contentDescription = null, tint = Color.Gray)
//                Icon(Icons.Default.AccountBox, contentDescription = null, tint = Color.Gray)
//            }
//        },
        content = { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = MaterialTheme.colorScheme.background,
                content = screenContent
            )
        }
    )
}