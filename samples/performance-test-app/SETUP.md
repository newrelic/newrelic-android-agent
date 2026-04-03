# Performance Test App - Setup Guide

## Quick Start

### 1. Configure New Relic Application Token

Before running the app, you need to add your New Relic application token.

Edit `src/main/java/com/newrelic/agent/android/perftest/PerformanceTestApplication.kt`:

```kotlin
NewRelic.withApplicationToken("<YOUR-APP-TOKEN>")  // Replace with your actual token
```

You can get your application token from:
- New Relic Dashboard → Mobile → (Your App) → Settings → Application

### 2. Build the App

From the root of the Android agent repository:

```bash
# Clean and build
./gradlew clean :samples:performance-test-app:assembleDebug

# Or build and install directly to device/emulator
./gradlew :samples:performance-test-app:installDebug
```

### 3. Run the App

- Launch from Android Studio:
  - Open the project in Android Studio
  - Select `performance-test-app` from the run configuration dropdown
  - Click Run

- Or via command line:
  ```bash
  adb shell am start -n com.newrelic.agent.android.perftest/.MainActivity
  ```

## What Was Created

### Project Structure
```
samples/performance-test-app/
├── build.gradle                          # Build configuration
├── proguard-rules.pro                    # ProGuard rules
├── README.md                             # Full documentation
├── SETUP.md                              # This file
└── src/main/
    ├── AndroidManifest.xml               # App manifest
    ├── java/com/newrelic/agent/android/perftest/
    │   ├── PerformanceTestApplication.kt # App initialization
    │   ├── MainActivity.kt               # Main activity
    │   ├── navigation/
    │   │   └── AppNavigation.kt          # Navigation graph
    │   ├── screens/
    │   │   ├── HomeScreen.kt             # Home/landing
    │   │   ├── InfiniteScrollScreen.kt   # List testing
    │   │   ├── ImageGalleryScreen.kt     # Image loading
    │   │   ├── UIElementsScreen.kt       # UI components
    │   │   ├── NetworkTestScreen.kt      # Network calls
    │   │   └── NavigationTestScreen.kt   # Navigation flow
    │   └── ui/theme/
    │       └── Theme.kt                  # Material 3 theme
    └── res/
        ├── drawable/
        │   └── ic_launcher_foreground.xml
        ├── mipmap-anydpi-v26/
        │   └── ic_launcher.xml
        ├── values/
        │   ├── colors.xml
        │   ├── strings.xml
        │   └── themes.xml
        └── [other density folders for icons]
```

### Files Modified
- `settings.gradle` - Added `:samples:performance-test-app` module

## Testing Features

### 1. Infinite Scroll Test
- **Purpose**: Test list performance with large datasets
- **What it does**: LazyColumn with endless scrolling, loads 20 items at a time
- **New Relic monitors**: Frame rate, jank, memory usage

### 2. Image Gallery Test
- **Purpose**: Test image loading and scrolling performance
- **What it does**: Grid of 100+ images loaded from Picsum Photos API
- **New Relic monitors**: Network requests, image loading time, memory

### 3. UI Elements Test
- **Purpose**: Test various Material 3 components
- **What it does**: Demonstrates buttons, text fields, switches, cards, etc.
- **New Relic monitors**: User interactions, tap events

### 4. Network Test
- **Purpose**: Test HTTP request monitoring
- **What it does**: Makes GET requests to various test APIs
- **New Relic monitors**: Request timing, response codes, errors

### 5. Navigation Test
- **Purpose**: Test screen transition tracking
- **What it does**: Navigate through 10 sequential screens
- **New Relic monitors**: Screen views, navigation patterns

## Troubleshooting

### Build Issues

**Problem**: Gradle sync fails with version conflicts

**Solution**: Make sure you're using the Gradle wrapper from the root project:
```bash
./gradlew --version  # Should show Gradle 7.6
```

**Problem**: Kotlin version mismatch

**Solution**: The app uses the parent project's Kotlin version (1.7.0) which is compatible with Compose

### Runtime Issues

**Problem**: App crashes on launch with New Relic error

**Solution**: Check that you've added a valid application token in `PerformanceTestApplication.kt`

**Problem**: Images not loading in gallery

**Solution**: Ensure the device/emulator has internet connectivity

## Development Tips

### Using with iOS Comparison

Since you mentioned testing the iOS agent, here are some comparable scenarios:

| Android Feature | iOS Equivalent |
|----------------|----------------|
| InfiniteScrollScreen | UITableView with endless scrolling |
| ImageGalleryScreen | UICollectionView with images |
| NetworkTestScreen | URLSession network calls |
| NavigationTestScreen | UINavigationController push/pop |

### Customizing Tests

To add more test scenarios:

1. Create a new screen in `screens/` package
2. Add navigation route in `AppNavigation.kt`
3. Add navigation card in `HomeScreen.kt`

Example:
```kotlin
// In AppNavigation.kt
object MyTest : Screen("my_test")

// Add composable
composable(Screen.MyTest.route) {
    MyTestScreen(navController = navController)
}

// In HomeScreen.kt, add TestCard
TestCard(
    title = "My Test",
    description = "Description",
    icon = Icons.Default.SomeIcon,
    onClick = { navController.navigate(Screen.MyTest.route) }
)
```

## Next Steps

1. ✅ Configure your New Relic app token
2. ✅ Build and install the app
3. ✅ Run through each test scenario
4. ✅ Check New Relic dashboard for captured data
5. ✅ Compare with iOS agent results

## Need Help?

- Check the main [README.md](README.md) for detailed feature documentation
- Review the inline code comments
- Consult New Relic Android Agent documentation: https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/
