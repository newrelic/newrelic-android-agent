/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.perftest.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationTestScreen(navController: NavController) {
    var currentScreen by remember { mutableStateOf(1) }
    val maxScreens = 10

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation Test - Screen $currentScreen") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentScreen == 1) {
                            navController.popBackStack()
                        } else {
                            currentScreen--
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Screen $currentScreen of $maxScreens",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .size(200.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentScreen.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress
            LinearProgressIndicator(
                progress = currentScreen.toFloat() / maxScreens,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "${((currentScreen.toFloat() / maxScreens) * 100).toInt()}% Complete",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { currentScreen-- },
                    enabled = currentScreen > 1,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ArrowBack, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Previous")
                }

                Button(
                    onClick = { currentScreen++ },
                    enabled = currentScreen < maxScreens,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.ArrowForward, null)
                }
            }

            Button(
                onClick = { currentScreen = 1 },
                enabled = currentScreen != 1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to Screen 1")
            }

            Divider()

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Navigation Testing",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Navigate through multiple screens to test navigation performance " +
                                "and state management. This tests how New Relic tracks screen transitions " +
                                "and user navigation patterns.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Dynamic content based on screen number
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Screen Details",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Current Screen: $currentScreen")
                    Text("Previous Screen: ${if (currentScreen > 1) currentScreen - 1 else "None"}")
                    Text("Next Screen: ${if (currentScreen < maxScreens) currentScreen + 1 else "None"}")
                    Text("Total Screens Available: $maxScreens")
                }
            }
        }
    }
}
