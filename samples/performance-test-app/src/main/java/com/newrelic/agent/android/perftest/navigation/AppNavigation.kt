/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.perftest.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.newrelic.agent.android.perftest.screens.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object InfiniteScroll : Screen("infinite_scroll")
    object ImageGallery : Screen("image_gallery")
    object UIElements : Screen("ui_elements")
    object NetworkTest : Screen("network_test")
    object NavigationTest : Screen("navigation_test")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.InfiniteScroll.route) {
            InfiniteScrollScreen(navController = navController)
        }
        composable(Screen.ImageGallery.route) {
            ImageGalleryScreen(navController = navController)
        }
        composable(Screen.UIElements.route) {
            UIElementsScreen(navController = navController)
        }
        composable(Screen.NetworkTest.route) {
            NetworkTestScreen(navController = navController)
        }
        composable(Screen.NavigationTest.route) {
            NavigationTestScreen(navController = navController)
        }
    }
}
