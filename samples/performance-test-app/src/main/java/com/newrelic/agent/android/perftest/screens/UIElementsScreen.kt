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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIElementsScreen(navController: NavController) {
    var textFieldValue by remember { mutableStateOf("") }
    var switchState by remember { mutableStateOf(false) }
    var checkboxState by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0.5f) }
    var radioSelection by remember { mutableStateOf(0) }
    var expandedFab by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UI Elements") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { expandedFab = !expandedFab },
                expanded = expandedFab,
                icon = { Icon(Icons.Default.Add, "Add") },
                text = { Text("Extended FAB") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Buttons Section
            SectionHeader("Buttons")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {}) {
                    Text("Button")
                }
                OutlinedButton(onClick = {}) {
                    Text("Outlined")
                }
                TextButton(onClick = {}) {
                    Text("Text")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = {}) {
                    Text("Tonal")
                }
                ElevatedButton(onClick = {}) {
                    Text("Elevated")
                }
            }

            // Icon Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Favorite, "Favorite")
                }
                FilledIconButton(onClick = {}) {
                    Icon(Icons.Default.Star, "Star")
                }
                FilledTonalIconButton(onClick = {}) {
                    Icon(Icons.Default.Settings, "Settings")
                }
                OutlinedIconButton(onClick = {}) {
                    Icon(Icons.Default.Share, "Share")
                }
            }

            Divider()

            // Text Inputs Section
            SectionHeader("Text Inputs")
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                label = { Text("Outlined TextField") },
                modifier = Modifier.fillMaxWidth()
            )

            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                label = { Text("Filled TextField") },
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            // Switches & Checkboxes Section
            SectionHeader("Switches & Toggles")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Switch")
                Switch(
                    checked = switchState,
                    onCheckedChange = { switchState = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Checkbox")
                Checkbox(
                    checked = checkboxState,
                    onCheckedChange = { checkboxState = it }
                )
            }

            Divider()

            // Sliders Section
            SectionHeader("Slider")
            Text("Value: ${(sliderValue * 100).toInt()}%")
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            // Radio Buttons Section
            SectionHeader("Radio Buttons")
            Column {
                (0..2).forEach { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = radioSelection == index,
                            onClick = { radioSelection = index }
                        )
                        Text("Option ${index + 1}")
                    }
                }
            }

            Divider()

            // Chips Section
            SectionHeader("Chips")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("Assist") }
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text("Filter") }
                )
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text("Input") }
                )
            }

            Divider()

            // Cards Section
            SectionHeader("Cards")
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Elevated Card",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "This is an example of a card with elevation.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Outlined Card",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "This is an example of an outlined card.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Add some space at the bottom for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
