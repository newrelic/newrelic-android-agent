# New Relic Android Agent - Performance Test App

A comprehensive Jetpack Compose application designed for performance testing the New Relic Android Agent.

## Overview

This app provides various UI patterns and scenarios to test and validate New Relic's Android agent performance monitoring capabilities. Built entirely with Jetpack Compose, it represents modern Android development practices.

## Features

### 1. **Infinite Scroll** 📜
- LazyColumn with dynamic item loading
- Tests list rendering performance
- Simulates endless scrolling with 1000+ items
- Monitors memory usage and scroll performance

### 2. **Image Gallery** 🖼️
- Grid layout with 100+ images
- Uses Coil for image loading
- Tests image caching and loading performance
- Monitors network requests for images

### 3. **UI Elements** 🎨
- Comprehensive collection of Material 3 components:
  - Buttons (Filled, Outlined, Text, Tonal, Elevated)
  - Icon Buttons
  - Text Fields (Filled, Outlined)
  - Switches, Checkboxes, Radio Buttons
  - Sliders
  - Chips (Assist, Filter, Input)
  - Cards (Elevated, Outlined)
  - Floating Action Button
- Tests UI interaction tracking

### 4. **Network Test** 🌐
- Multiple HTTP request endpoints
- Success and error handling
- Burst testing (multiple concurrent requests)
- Tests network monitoring capabilities
- Endpoints used:
  - `httpbin.org` (HTTP testing)
  - `jsonplaceholder.typicode.com` (Mock API)
  - `api.github.com` (GitHub API)

### 5. **Navigation Test** 🧭
- Multi-screen navigation flow
- Tests screen transition tracking
- Progress indication across 10 screens
- State management validation

## Technology Stack

- **UI Framework**: Jetpack Compose (Material 3)
- **Navigation**: Compose Navigation
- **Image Loading**: Coil
- **Networking**: OkHttp3
- **Coroutines**: Kotlinx Coroutines
- **Monitoring**: New Relic Android Agent

## Setup

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26 (Android 8.0) or higher
- Kotlin 1.9.10+

### Configuration

1. **Add New Relic Application Token**

   Edit `PerformanceTestApplication.kt` and replace `<YOUR-APP-TOKEN>`:

   ```kotlin
   NewRelic.withApplicationToken("<YOUR-APP-TOKEN>")
   ```

2. **Build and Run**

   ```bash
   ./gradlew :samples:performance-test-app:assembleDebug
   ./gradlew :samples:performance-test-app:installDebug
   ```

## New Relic Features Tested

- ✅ Native Reporting
- ✅ Log Reporting
- ✅ Offline Storage
- ✅ Application Exit Reporting
- ✅ Background Reporting

## Testing Scenarios

### Performance Testing

1. **Scroll Performance**
   - Navigate to Infinite Scroll
   - Scroll rapidly through items
   - Observe FPS and jank metrics

2. **Image Loading**
   - Navigate to Image Gallery
   - Scroll through images
   - Monitor network requests and memory

3. **UI Interactions**
   - Navigate to UI Elements
   - Interact with various components
   - Test event tracking

4. **Network Performance**
   - Navigate to Network Test
   - Make single requests
   - Run burst tests
   - Observe request timing and errors

5. **Navigation Patterns**
   - Navigate to Navigation Test
   - Move through multiple screens
   - Test screen tracking and transitions

## Monitoring in New Relic

After running the tests, check your New Relic dashboard for:

- **Session Performance**: View overall app performance
- **Network Requests**: Analyze API call timing and errors
- **Crash Reports**: Monitor any crashes or ANRs
- **Custom Events**: Track user interactions
- **Screen Views**: See navigation patterns

## Architecture

```
performance-test-app/
├── src/main/java/com/newrelic/agent/android/perftest/
│   ├── PerformanceTestApplication.kt    # App initialization
│   ├── MainActivity.kt                   # Main activity
│   ├── navigation/
│   │   └── AppNavigation.kt             # Navigation graph
│   ├── screens/
│   │   ├── HomeScreen.kt                # Landing page
│   │   ├── InfiniteScrollScreen.kt      # List testing
│   │   ├── ImageGalleryScreen.kt        # Image testing
│   │   ├── UIElementsScreen.kt          # Component testing
│   │   ├── NetworkTestScreen.kt         # Network testing
│   │   └── NavigationTestScreen.kt      # Navigation testing
│   └── ui/theme/
│       └── Theme.kt                     # Material 3 theme
└── build.gradle                          # Dependencies
```

## Dependencies

Key dependencies:
- `com.newrelic.agent.android:android-agent:7.+`
- `androidx.compose.material3:material3`
- `androidx.navigation:navigation-compose`
- `io.coil-kt:coil-compose`
- `com.squareup.okhttp3:okhttp`

## License

Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
SPDX-License-Identifier: Apache-2.0
