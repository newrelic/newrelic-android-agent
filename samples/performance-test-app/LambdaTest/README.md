# Session Replay Performance Testing - Jetpack Compose

This directory contains automated performance tests for measuring New Relic Session Replay overhead on a Jetpack Compose Android application.

## Overview

The test suite measures memory and CPU usage across three configurations:
1. **No Agent** - Pure app without New Relic agent
2. **Baseline** - New Relic agent enabled, Session Replay disabled
3. **Session Replay** - Both agent and Session Replay enabled

## Prerequisites

- Node.js 20+ and npm
- Android Studio
- LambdaTest account with real device access
- New Relic staging environment access

## Setup Instructions

### 1. Configure Build Variants

The app uses Gradle product flavors to create three different APK variants with different New Relic configurations.

**File:** `app/build.gradle`

```gradle
productFlavors {
    noAgent {
        buildConfigField "String", "NEWRELIC_TOKEN", '""'
        buildConfigField "boolean", "AGENT_ENABLED", "false"
    }
    baseline {
        buildConfigField "String", "NEWRELIC_TOKEN", '"YOUR_BASELINE_TOKEN"'
        buildConfigField "boolean", "AGENT_ENABLED", "true"
    }
    sessionReplay {
        buildConfigField "String", "NEWRELIC_TOKEN", '"YOUR_SESSION_REPLAY_TOKEN"'
        buildConfigField "boolean", "AGENT_ENABLED", "true"
    }
}
```

**Getting New Relic Tokens:**
1. Log in to New Relic staging environment
2. Create two mobile applications:
   - One for baseline (Session Replay disabled in settings)
   - One for sessionReplay (Session Replay enabled in settings)
3. Copy the application tokens and update `build.gradle`

**Important:**
- The `baseline` variant should use a token from an app with Session Replay **disabled**
- The `sessionReplay` variant should use a token from an app with Session Replay **enabled**
- The `noAgent` variant has an empty token and AGENT_ENABLED=false

### 2. Build APKs

Build all three APK variants:

```bash
cd /path/to/performance-test-app
./gradlew assembleDebug
```

This creates three APKs:
- `app/build/outputs/apk/noAgent/debug/app-noAgent-debug.apk`
- `app/build/outputs/apk/baseline/debug/app-baseline-debug.apk`
- `app/build/outputs/apk/sessionReplay/debug/app-sessionReplay-debug.apk`

### 3. Upload APKs to LambdaTest

Upload each APK to LambdaTest Real Device Cloud:

```bash
# Upload noAgent APK
curl -u "USERNAME:ACCESS_KEY" \
  -X POST "https://manual-api.lambdatest.com/app/upload/realDevice" \
  -F "appFile=@app/build/outputs/apk/noAgent/debug/app-noAgent-debug.apk" \
  -F "name=performance-test-app-compose-no-agent.apk"

# Upload baseline APK
curl -u "USERNAME:ACCESS_KEY" \
  -X POST "https://manual-api.lambdatest.com/app/upload/realDevice" \
  -F "appFile=@app/build/outputs/apk/baseline/debug/app-baseline-debug.apk" \
  -F "name=performance-test-app-compose-baseline.apk"

# Upload sessionReplay APK
curl -u "USERNAME:ACCESS_KEY" \
  -X POST "https://manual-api.lambdatest.com/app/upload/realDevice" \
  -F "appFile=@app/build/outputs/apk/sessionReplay/debug/app-sessionReplay-debug.apk" \
  -F "name=performance-test-app-compose-session-replay.apk"
```

Each upload returns an `app_id` like `lt://APP1016021601775158401299680`.

### 4. Update Test Configuration Files

Update the three WebDriverIO config files with the APP IDs from step 3:

**`wdio-config-android-no-agent.js`:**
```javascript
'appium:app': process.env.LT_APP_ID || 'lt://APP_YOUR_NO_AGENT_ID',
```

**`wdio-config-android-baseline.js`:**
```javascript
'appium:app': process.env.LT_APP_ID || 'lt://APP_YOUR_BASELINE_ID',
```

**`wdio-config-android-session-replay.js`:**
```javascript
'appium:app': process.env.LT_APP_ID || 'lt://APP_YOUR_SESSION_REPLAY_ID',
```

### 5. Install Dependencies

Install WebDriverIO and test dependencies:

```bash
cd LambdaTest
npm install
```

This installs:
- `@wdio/cli` - WebDriverIO test runner
- `@wdio/jasmine-framework` - Test framework
- `webdriverio` - WebDriver client

**Note:** The `package.json` file is already configured with the correct dependencies. Local installation is required to avoid npx bugs.

### 6. Configure LambdaTest Credentials

Set your LambdaTest credentials as environment variables or update them in the config files:

```bash
export LT_USERNAME="your-username"
export LT_ACCESSKEY="your-access-key"
```

Or update directly in the config files:
```javascript
user: process.env.LT_USERNAME || 'your-username',
key: process.env.LT_ACCESSKEY || 'your-access-key',
```

## Running Tests

### Test Run (1 iteration per configuration)

Run a quick test to verify everything is working:

```bash
cd LambdaTest
./test-run.sh
```

This runs each configuration once (~10 minutes total).

### Full Test Suite (5 iterations per configuration)

Run the complete test suite for statistical significance:

```bash
cd LambdaTest
./run-all-tests.sh
```

This runs:
- 5 iterations of noAgent
- 5 iterations of baseline
- 5 iterations of sessionReplay

**Total:** 15 tests (~40-45 minutes)

### Run Individual Tests

You can also run individual configurations:

```bash
# No Agent test
npx wdio wdio-config-android-no-agent.js

# Baseline test
npx wdio wdio-config-android-baseline.js

# Session Replay test
npx wdio wdio-config-android-session-replay.js
```

## Analyzing Results

### Aggregate Results

After tests complete, aggregate the results:

```bash
node aggregate-results.js
```

This generates:
- Console output with statistics
- `results/aggregated_results.json` with detailed data

### Generate Markdown Report

Create a formatted markdown report:

```bash
node generate-results-markdown.js results/aggregated_results.json
```

This generates `session-replay-performance-results.md` with:
- Performance metrics tables
- Overhead calculations
- Statistical analysis
- Test methodology

## Test Details

### Test Scenario

Each test performs intensive UI operations:

1. **Navigation Stress Test** (5 iterations)
   - Navigate to Infinite Scroll screen and back

2. **Infinite Scroll Test**
   - 4 scrolls down, 2 scrolls up
   - Tests continuous scroll event capture

3. **Image Gallery Scroll Test**
   - 4 scrolls through image gallery
   - Tests visual content capture

4. **UI Elements Interactions**
   - Button taps, switches, sliders
   - Tests UI interaction capture

5. **Network Test**
   - 3 GET request button taps
   - Tests network instrumentation

6. **Navigation Flow Test**
   - 5 sequential screen navigations
   - Tests multi-screen flow capture

### Metrics Collected

- **Memory Usage** (MB) - via Debug.MemoryInfo
- **CPU Usage** (%) - via /proc/[pid]/stat
- **Test Duration** - total test execution time

Metrics are collected every 2 seconds throughout the test.

### Device Configuration

- **Device:** Pixel 8
- **OS:** Android 14
- **Platform:** LambdaTest Real Device Cloud
- **Test Duration:** ~2-3 minutes per test

## Project Structure

```
LambdaTest/
├── README.md                              # This file
├── package.json                           # npm dependencies
├── wdio-config-android-no-agent.js       # No Agent test config
├── wdio-config-android-baseline.js       # Baseline test config
├── wdio-config-android-session-replay.js # Session Replay test config
├── tests/
│   └── session-replay-performance.test.js # Main test script
├── run-all-tests.sh                       # Full test suite (5 runs each)
├── test-run.sh                            # Quick test (1 run each)
├── aggregate-results.js                   # Results aggregation script
├── generate-results-markdown.js           # Markdown report generator
└── results/                              # Test results (generated)
    ├── no_agent_run1.json
    ├── baseline_run1.json
    ├── session_replay_run1.json
    └── ...
```

## Troubleshooting

### "ENOTDIR: not a directory" error

If you see this error when running tests, make sure you ran `npm install` in the LambdaTest directory first. The local WebDriverIO installation is required.

### APK upload fails

- Check your LambdaTest credentials
- Verify the APK file path is correct
- Ensure the APK is less than 1GB

### Test fails to find UI elements

- Verify the APK uploaded correctly (check the APP ID)
- Ensure the app has proper `contentDescription` attributes on Composables
- Check LambdaTest device logs for errors

### Results not saved

- Check that the `results/` directory exists (created automatically)
- Verify the test completed successfully (look for "✓ Completed" messages)
- Check file permissions in the results directory

## Notes

- Each test run takes ~2-3 minutes on Pixel 8
- Results are saved as JSON files in the `results/` directory
- The test script includes automatic retries (up to 3 attempts) on failure
- A 10-second cooldown period is enforced between tests

## Related Documentation

- **XML Layouts Test:** See `../performance-test-app-xml/LambdaTest/` for traditional XML layout tests
- **New Relic Documentation:** https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/
- **LambdaTest Documentation:** https://www.lambdatest.com/support/docs/
- **WebDriverIO Documentation:** https://webdriver.io/
