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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed class NetworkState {
    object Idle : NetworkState()
    object Loading : NetworkState()
    data class Success(val message: String) : NetworkState()
    data class Error(val message: String) : NetworkState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkTestScreen(navController: NavController) {
    var networkState by remember { mutableStateOf<NetworkState>(NetworkState.Idle) }
    var requestCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val client = remember { OkHttpClient() }

    fun makeRequest(url: String) {
        scope.launch {
            networkState = NetworkState.Loading
            try {
                val response = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "NewRelic-Performance-Test")
                        .build()

                    client.newCall(request).execute()
                }

                networkState = if (response.isSuccessful) {
                    requestCount++
                    NetworkState.Success("Success! Status: ${response.code}")
                } else {
                    NetworkState.Error("Request failed with code: ${response.code}")
                }
                response.close()
            } catch (e: IOException) {
                networkState = NetworkState.Error("Error: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Test") },
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
            Text(
                text = "Network Performance Testing",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "Total requests: $requestCount",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Divider()

            // Request Buttons
            Text(
                text = "Test Endpoints",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = { makeRequest("https://httpbin.org/get") },
                modifier = Modifier.fillMaxWidth(),
                enabled = networkState !is NetworkState.Loading
            ) {
                Text("GET Request (httpbin.org)")
            }

            Button(
                onClick = { makeRequest("https://jsonplaceholder.typicode.com/posts/1") },
                modifier = Modifier.fillMaxWidth(),
                enabled = networkState !is NetworkState.Loading
            ) {
                Text("GET Request (JSONPlaceholder)")
            }

            Button(
                onClick = { makeRequest("https://api.github.com/zen") },
                modifier = Modifier.fillMaxWidth(),
                enabled = networkState !is NetworkState.Loading
            ) {
                Text("GET Request (GitHub API)")
            }

            Button(
                onClick = {
                    repeat(5) {
                        makeRequest("https://httpbin.org/get")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = networkState !is NetworkState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Burst Test (5 requests)")
            }

            Divider()

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = when (networkState) {
                    is NetworkState.Loading -> CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    is NetworkState.Success -> CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    is NetworkState.Error -> CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                    else -> CardDefaults.cardColors()
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = networkState) {
                        is NetworkState.Idle -> {
                            Text(
                                text = "Ready to make requests",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        is NetworkState.Loading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("Making request...")
                            }
                        }
                        is NetworkState.Success -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        is NetworkState.Error -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About Network Testing",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This screen tests New Relic's network monitoring capabilities. " +
                                "All requests are tracked and reported to New Relic, including " +
                                "response times, status codes, and error rates.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
