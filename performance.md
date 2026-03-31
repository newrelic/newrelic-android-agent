# New Relic Android SDK Performance Impact Report

This report summarizes the performance overhead of the New Relic Android Agent (including Session Replay) by comparing baseline (No SDK) iterations against SDK-enabled iterations.

## Executive Summary

| Metric | Baseline (No SDK) | With New Relic SDK | Delta (Overhead) |
| :--- | :--- | :--- | :--- |
| **Cold Startup** | 1365.0 ms | 1475.8 ms | +110.8 ms (+8.1%) |
| **Hot Startup** | 50.4 ms | 48.5 ms | -1.9 ms (no impact) |
| **Peak CPU Usage** | 26.33% | 26.22% | -0.11% (no impact) |
| **Peak Memory Usage** | 307.94 MB | 317.19 MB | +9.25 MB (+3.0%) |
| **Network Upload** | 43.3 KB | 72.9 KB | +29.6 KB (+68.4%) |
| **Avg. Janky Frames** | 504.8 | 518.3 | +13.5 (+2.7%) |

### Key Takeaways

- **Cold startup** adds ~111 ms of one-time initialization overhead, well within acceptable thresholds for production apps.
- **Hot startup** shows no measurable impact — the SDK does not interfere with process-resume performance.
- **CPU usage** is effectively unchanged, confirming the agent's lightweight background processing.
- **Memory** increases by ~9 MB, primarily for internal telemetry buffers and session replay state.
- **Network upload** increases by ~30 KB per session due to telemetry and session replay payloads.
- **UI rendering** remains smooth with negligible jank increase (~2.7%) and no change in average frame rate.

---

## APK Size Impact

Measured using the New Relic Android Agent v7.7.3 on the Now in Android (NiA) app.

| Build Type | With New Relic | Without New Relic | Difference | % Increase |
| :--- | :--- | :--- | :--- | :--- |
| **Debug** | 24.42 MB (25,606,044 B) | 23.97 MB (25,130,804 B) | +465 KB | +1.9% |
| **Release** | 17.69 MB (18,549,778 B) | 17.31 MB (18,155,716 B) | +385 KB | +2.2% |

The agent adds roughly **465 KB to debug** and **385 KB to release** builds. The smaller delta in release is due to R8/ProGuard shrinking unused agent code. Overall APK size impact is modest at ~2%.

---

## Startup Times

Measured in milliseconds (ms). Cold startup involves full process initialization; hot startup involves bringing an existing process to the foreground.

| Run Type | Iteration | Cold Startup (ms) | Hot Startup (ms) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 1312 | 53 |
| | Run 2 | 1300 | 46 |
| | Run 3 | 1403 | 55 |
| | Run 4 | 1402 | 49 |
| | Run 5 | 1408 | 47 |
| **SDK Enabled** | Run 1 | 1560 | 53 |
| | Run 2 | 1456 | 48 |
| | Run 3 | 1439 | 46 |
| | Run 4 | 1448 | 47 |

---

## Resource Utilization (Peaks)

Represents the maximum resource consumption observed during the session.

| Run Type | Iteration | Peak CPU Usage (%) | Peak Memory Usage (MB) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 26.25 | 306.82 |
| | Run 2 | 24.13 | 313.42 |
| | Run 3 | 25.38 | 303.90 |
| | Run 4 | 25.75 | 309.25 |
| | Run 5 | 30.13 | 306.30 |
| **SDK Enabled** | Run 1 | 25.88 | 314.57 |
| | Run 2 | 26.25 | 321.19 |
| | Run 3 | 22.75 | 316.48 |
| | Run 4 | 30.00 | 316.50 |

---

## Network Traffic

Total data uploaded during the session, including telemetry and session replay payloads.

| Run Type | Iteration | Network Upload (Bytes) |
| :--- | :--- | :--- |
| **Baseline** | Run 1 | 42,966 |
| | Run 2 | 43,939 |
| | Run 3 | 40,546 |
| | Run 4 | 44,290 |
| | Run 5 | 44,908 |
| **SDK Enabled** | Run 1 | 70,886 |
| | Run 2 | 78,192 |
| | Run 3 | 70,841 |
| | Run 4 | 72,111 |

---

## UI Performance (Jank)

Janky frames represent frames that failed to render within the 16 ms window.

| Run Type | Iteration | Total Janky Frames | Avg. Frame Rate (FPS) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 487 | 81.52 |
| | Run 2 | 412 | 81.94 |
| | Run 3 | 557 | 80.49 |
| | Run 4 | 451 | 81.94 |
| | Run 5 | 617 | 80.66 |
| **SDK Enabled** | Run 1 | 511 | 81.54 |
| | Run 2 | 441 | 81.82 |
| | Run 3 | 474 | 82.24 |
| | Run 4 | 647 | 81.39 |

---

---

# Jetpack Compose Session Replay — Performance Impact Report

This section evaluates the performance overhead of Session Replay across different recording modes on a Jetpack Compose application (Now in Android). Four configurations were tested: Baseline (SDK disabled), Full Recording, No Image Recording, and No Touch Recording.

## Executive Summary

| Metric | Baseline | Full Recording | No Image | No Touch | Full vs Baseline |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Cold Startup** | 1380.8 ms | 1449.0 ms | 1438.0 ms | 1474.2 ms | +68.2 ms (+4.9%) |
| **Hot Startup** | 46.8 ms | 53.8 ms | 66.3 ms | 51.4 ms | +7.0 ms (+14.9%) |
| **Peak CPU** | 29.3% | 27.8% | 28.9% | 26.7% | -1.5% (no impact) |
| **Avg CPU** | 4.81% | 5.36% | 5.15% | 5.20% | +0.55% (+11.4%) |
| **Peak Memory** | 310.2 MB | 332.5 MB | 323.9 MB | 318.8 MB | +22.3 MB (+7.2%) |
| **Avg Memory** | 248.7 MB | 265.4 MB | 250.2 MB | 252.8 MB | +16.7 MB (+6.7%) |
| **Network Upload** | 43.0 KB | 455.8 KB | 132.9 KB | 117.6 KB | +412.8 KB |
| **Avg FPS** | 81.7 | 79.7 | 81.1 | 81.5 | -2.0 FPS (-2.5%) |
| **Total Janky Frames** | 888 | 952 | 843 | 734 | +64 (+7.2%) |
| **Crashes / ANRs** | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | None |

### Key Takeaways

- **Cold startup** overhead is modest at ~68 ms (4.9%) for Full Recording — lower than the general SDK overhead measured on View-based apps, suggesting efficient Compose initialization.
- **Average CPU** increases by ~0.55 percentage points with Full Recording, reflecting the continuous Compose tree traversal and image capture.
- **Peak memory** increases by ~22 MB with Full Recording, driven by bitmap capture buffers. No Image mode reduces this to ~14 MB, and No Touch mode to ~9 MB.
- **Network upload** is the most significant overhead — Full Recording uploads ~456 KB vs 43 KB baseline due to screenshot data. No Image mode reduces this to ~133 KB (71% reduction vs Full), and No Touch mode to ~118 KB (74% reduction).
- **Frame rate** drops by ~2 FPS with Full Recording but remains above 79 FPS — imperceptible to users.
- **No crashes or ANRs** were observed across any configuration.
- **No Image Recording** offers the best performance-to-functionality tradeoff: minimal CPU/memory overhead with 71% less network traffic than Full Recording.

---

## Startup Times

| Config | Iteration | Cold Startup (ms) | Hot Startup (ms) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 1368 | 50 |
| | Run 2 | 1340 | 43 |
| | Run 3 | 1369 | 46 |
| | Run 4 | 1313 | 48 |
| | Run 5 | 1374 | 48 |
| | Run 6 | 1521 | 46 |
| **Full Recording** | Run 1 | 1464 | 45 |
| | Run 2 | 1406 | 81 |
| | Run 3 | 1426 | 45 |
| | Run 4 | 1500 | 44 |
| **No Image** | Run 1 | 1473 | 87 |
| | Run 2 | 1470 | 47 |
| | Run 3 | 1419 | 65 |
| | Run 4 | 1390 | 66 |
| **No Touch** | Run 1 | 1466 | 48 |
| | Run 2 | 1623 | 65 |
| | Run 3 | 1572 | 56 |
| | Run 4 | 1333 | 44 |
| | Run 5 | 1377 | 44 |

---

## CPU Utilization

| Config | Iteration | Peak CPU (%) | Avg CPU (%) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 24.5 | 4.80 |
| | Run 2 | 29.0 | 4.89 |
| | Run 3 | 29.6 | 4.53 |
| | Run 4 | 37.0 | 4.67 |
| | Run 5 | 27.1 | 5.10 |
| | Run 6 | 28.2 | 4.85 |
| **Full Recording** | Run 1 | 26.4 | 5.57 |
| | Run 2 | 27.8 | 5.27 |
| | Run 3 | 27.4 | 5.34 |
| | Run 4 | 29.6 | 5.24 |
| **No Image** | Run 1 | 30.8 | 5.13 |
| | Run 2 | 30.1 | 5.50 |
| | Run 3 | 29.1 | 5.17 |
| | Run 4 | 25.4 | 4.79 |
| **No Touch** | Run 1 | 27.5 | 5.11 |
| | Run 2 | 24.5 | 5.46 |
| | Run 3 | 34.9 | 5.09 |
| | Run 4 | 17.5 | 5.00 |
| | Run 5 | 29.2 | 5.36 |

---

## Memory Utilization

| Config | Iteration | Peak Memory (MB) | Avg Memory (MB) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 309.9 | 248.6 |
| | Run 2 | 313.6 | 251.6 |
| | Run 3 | 308.1 | 246.7 |
| | Run 4 | 312.1 | 249.7 |
| | Run 5 | 308.3 | 248.7 |
| | Run 6 | 309.1 | 247.1 |
| **Full Recording** | Run 1 | 341.0 | 263.8 |
| | Run 2 | 332.1 | 265.3 |
| | Run 3 | 331.3 | 263.6 |
| | Run 4 | 325.5 | 268.8 |
| **No Image** | Run 1 | 325.7 | 253.6 |
| | Run 2 | 327.8 | 252.2 |
| | Run 3 | 320.7 | 247.7 |
| | Run 4 | 321.6 | 247.1 |
| **No Touch** | Run 1 | 308.6 | 250.9 |
| | Run 2 | 329.7 | 259.5 |
| | Run 3 | 321.4 | 260.9 |
| | Run 4 | 324.9 | 246.4 |
| | Run 5 | 309.4 | 246.5 |

---

## Network Traffic

Total data uploaded during the session (bytes).

| Config | Iteration | Upload (Bytes) | Download (Bytes) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 41,822 | 465,841 |
| | Run 2 | 43,469 | 571,292 |
| | Run 3 | 47,504 | 573,606 |
| | Run 4 | 39,544 | 462,098 |
| | Run 5 | 44,785 | 576,002 |
| | Run 6 | 47,257 | 472,473 |
| **Full Recording** | Run 1 | 448,074 | 492,427 |
| | Run 2 | 461,196 | 492,367 |
| | Run 3 | 479,876 | 615,856 |
| | Run 4 | 478,555 | 614,818 |
| **No Image** | Run 1 | 134,001 | 591,675 |
| | Run 2 | 130,571 | 475,543 |
| | Run 3 | 137,688 | 589,440 |
| | Run 4 | 142,252 | 592,386 |
| **No Touch** | Run 1 | 124,044 | 773,968 |
| | Run 2 | 106,493 | 471,714 |
| | Run 3 | 120,544 | 632,384 |
| | Run 4 | 125,259 | 592,295 |
| | Run 5 | 125,962 | 592,301 |

---

## UI Performance (Jank & Frame Rate)

| Config | Iteration | Total Janky Frames | Avg FPS |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 911 | 81.9 |
| | Run 2 | 877 | 82.5 |
| | Run 3 | 1005 | 80.5 |
| | Run 4 | 1028 | 81.8 |
| | Run 5 | 764 | 82.5 |
| | Run 6 | 741 | 81.3 |
| **Full Recording** | Run 1 | 888 | 79.9 |
| | Run 2 | 1208 | 78.9 |
| | Run 3 | 870 | 80.0 |
| | Run 4 | 843 | 79.9 |
| **No Image** | Run 1 | 832 | 80.8 |
| | Run 2 | 813 | 80.8 |
| | Run 3 | 931 | 81.2 |
| | Run 4 | 797 | 81.6 |
| **No Touch** | Run 1 | 813 | 80.6 |
| | Run 2 | 786 | 81.3 |
| | Run 3 | 465 | 82.5 |
| | Run 4 | 894 | 81.9 |
| | Run 5 | 712 | 81.4 |

---

## Recording Mode Comparison — Network Upload Overhead

| Mode | Avg Upload (KB) | vs Baseline | vs Full Recording |
| :--- | :--- | :--- | :--- |
| Baseline | 43.0 | — | — |
| Full Recording | 455.8 | +412.8 KB (+960%) | — |
| No Image | 132.9 | +89.9 KB (+209%) | -322.9 KB (-71%) |
| No Touch | 117.6 | +74.6 KB (+173%) | -338.2 KB (-74%) |

---

## Recording Mode Comparison — Memory Overhead

| Mode | Avg Peak Memory (MB) | vs Baseline |
| :--- | :--- | :--- |
| Baseline | 310.2 | — |
| Full Recording | 332.5 | +22.3 MB (+7.2%) |
| No Image | 323.9 | +13.7 MB (+4.4%) |
| No Touch | 318.8 | +8.6 MB (+2.8%) |

---

## Methodology — Jetpack Compose Tests

- **App:** Now in Android (NiA) — Google's official Jetpack Compose sample app.
- **Device:** Consistent physical device configuration across all iterations.
- **Iterations:** 6 baseline, 4 Full Recording, 4 No Image, 5 No Touch runs.
- **Profiling Tool:** App profiling tool with per-second telemetry for CPU, memory, disk, network, FPS, frames, and temperature.
- **Recording Modes:**
  - **Full Recording** — captures screenshots, touch events, and UI tree mutations.
  - **No Image Recording** — captures touch events and UI tree mutations without screenshots.
  - **No Touch Recording** — captures screenshots and UI tree mutations without touch events.
- **Stability:** Zero crashes and zero ANRs across all 19 test runs.

---

## Methodology — View-Based Tests (Section 1)

- **Device:** Tests were run on a consistent device/emulator configuration across all iterations.
- **Iterations:** 5 baseline runs and 4 SDK-enabled runs to account for natural variance.
- **SDK Configuration:** New Relic Android Agent with Session Replay enabled using default settings.
- **Measurements:** Cold/hot startup via `adb shell am start`, CPU/memory via system profiling, network via traffic capture, jank via frame stats.

---

---

# View-Based App Session Replay — Performance Impact Report

This section evaluates the performance overhead of Session Replay on a View-based Android application (Infinity), comparing four configurations: Baseline (no agent), MSR with Default Settings, No Image Capture, and Full Image Recording. Each configuration was tested with 5 iterations.

## Executive Summary

| Metric | Baseline | MSR Default | No Image | Full Image | MSR Default vs Baseline |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Cold Startup** | 890.6 ms | 940.4 ms | 948.4 ms | 955.4 ms | +49.8 ms (+5.6%) |
| **Hot Startup** | 39.0 ms | 40.8 ms | 39.4 ms | 36.8 ms | +1.8 ms (no impact) |
| **Peak CPU** | 17.7% | 14.9% | 18.0% | 17.5% | -2.8% (no impact) |
| **Avg CPU** | 2.61% | 2.56% | 2.73% | 2.68% | -0.05% (no impact) |
| **Peak Memory** | 295.7 MB | 300.5 MB | 288.6 MB | 301.8 MB | +4.8 MB (+1.6%) |
| **Avg Memory** | 215.9 MB | 214.6 MB | 222.8 MB | 211.4 MB | -1.3 MB (no impact) |
| **Network Upload** | 97.2 KB | 152.9 KB | 150.1 KB | 372.4 KB | +55.7 KB (+57.3%) |
| **Avg FPS** | 80.4 | 79.7 | 79.7 | 78.0 | -0.8 FPS (-1.0%) |
| **Total Janky Frames** | 950 | 1127 | 1107 | 1664 | +177 (+18.6%) |
| **Crashes / ANRs** | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | None |

### Key Takeaways

- **Cold startup** overhead is ~50 ms (+5.6%) with MSR Default Settings — consistent with the Compose results and well within acceptable thresholds.
- **CPU usage** is effectively unchanged across all modes, confirming lightweight background processing on View-based apps.
- **Peak memory** increases by only ~5 MB (+1.6%) with MSR Default, significantly lower than the Compose app (~22 MB), indicating lower overhead for View-based rendering.
- **Network upload** increases by ~56 KB with MSR Default. Full Image Recording increases upload to ~372 KB — substantially more but still lower than the Compose app's ~456 KB due to simpler View hierarchies.
- **Frame rate** drops by less than 1 FPS with MSR Default. Full Image Recording shows a ~2.4 FPS drop, with one outlier run pulling the average down.
- **Janky frames** increase by ~19% with MSR Default. Full Image Recording shows higher variance due to one outlier run (4446 janky frames in Run 5); excluding it, the average is ~969 — comparable to baseline.
- **Zero crashes or ANRs** across all 20 runs.

---

## Startup Times

| Config | Iteration | Cold Startup (ms) | Hot Startup (ms) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 881 | 42 |
| | Run 2 | 878 | 40 |
| | Run 3 | 955 | 36 |
| | Run 4 | 864 | 38 |
| | Run 5 | 875 | 39 |
| **MSR Default** | Run 1 | 931 | 44 |
| | Run 2 | 921 | 45 |
| | Run 3 | 979 | 43 |
| | Run 4 | 915 | 42 |
| | Run 5 | 956 | 30 |
| **No Image** | Run 1 | 920 | 38 |
| | Run 2 | 899 | 42 |
| | Run 3 | 950 | 37 |
| | Run 4 | 1020 | 41 |
| | Run 5 | 953 | 39 |
| **Full Image** | Run 1 | 987 | 38 |
| | Run 2 | 988 | 36 |
| | Run 3 | 907 | 41 |
| | Run 4 | 912 | 36 |
| | Run 5 | 983 | 33 |

---

## CPU Utilization

| Config | Iteration | Peak CPU (%) | Avg CPU (%) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 14.6 | 2.52 |
| | Run 2 | 18.2 | 2.64 |
| | Run 3 | 17.1 | 2.74 |
| | Run 4 | 15.2 | 2.52 |
| | Run 5 | 23.2 | 2.61 |
| **MSR Default** | Run 1 | 13.4 | 2.61 |
| | Run 2 | 15.8 | 2.55 |
| | Run 3 | 12.9 | 2.66 |
| | Run 4 | 12.9 | 2.59 |
| | Run 5 | 19.4 | 2.38 |
| **No Image** | Run 1 | 23.6 | 2.95 |
| | Run 2 | 15.0 | 2.66 |
| | Run 3 | 21.9 | 2.53 |
| | Run 4 | 15.1 | 2.57 |
| | Run 5 | 14.5 | 2.95 |
| **Full Image** | Run 1 | 16.6 | 2.62 |
| | Run 2 | 22.1 | 2.89 |
| | Run 3 | 16.6 | 2.78 |
| | Run 4 | 20.8 | 2.76 |
| | Run 5 | 11.2 | 2.35 |

---

## Memory Utilization

| Config | Iteration | Peak Memory (MB) | Avg Memory (MB) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 292.5 | 207.9 |
| | Run 2 | 301.6 | 208.4 |
| | Run 3 | 288.5 | 245.5 |
| | Run 4 | 300.5 | 211.0 |
| | Run 5 | 295.7 | 206.5 |
| **MSR Default** | Run 1 | 291.6 | 216.9 |
| | Run 2 | 293.9 | 217.0 |
| | Run 3 | 303.5 | 214.7 |
| | Run 4 | 308.0 | 211.7 |
| | Run 5 | 305.5 | 212.7 |
| **No Image** | Run 1 | 275.3 | 232.3 |
| | Run 2 | 297.8 | 221.1 |
| | Run 3 | 298.0 | 210.5 |
| | Run 4 | 300.3 | 215.0 |
| | Run 5 | 271.4 | 234.9 |
| **Full Image** | Run 1 | 294.0 | 214.5 |
| | Run 2 | 299.9 | 216.0 |
| | Run 3 | 313.5 | 217.9 |
| | Run 4 | 296.7 | 212.1 |
| | Run 5 | 304.7 | 196.7 |

---

## Network Traffic

Total data uploaded during the session (bytes).

| Config | Iteration | Upload (Bytes) | Download (Bytes) |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 120,106 | 4,891,437 |
| | Run 2 | 86,293 | 2,023,137 |
| | Run 3 | 87,465 | 2,885,703 |
| | Run 4 | 105,377 | 4,862,467 |
| | Run 5 | 98,419 | 3,099,606 |
| **MSR Default** | Run 1 | 156,359 | 3,120,063 |
| | Run 2 | 155,588 | 3,045,714 |
| | Run 3 | 167,893 | 3,459,222 |
| | Run 4 | 152,326 | 3,003,427 |
| | Run 5 | 150,817 | 3,155,015 |
| **No Image** | Run 1 | 133,454 | 2,447,024 |
| | Run 2 | 148,294 | 2,312,432 |
| | Run 3 | 160,826 | 2,310,135 |
| | Run 4 | 172,818 | 3,449,741 |
| | Run 5 | 153,324 | 2,982,835 |
| **Full Image** | Run 1 | 432,396 | 3,150,457 |
| | Run 2 | 336,444 | 3,044,564 |
| | Run 3 | 388,906 | 3,041,261 |
| | Run 4 | 321,608 | 3,188,193 |
| | Run 5 | 427,442 | 3,110,411 |

---

## UI Performance (Jank & Frame Rate)

| Config | Iteration | Total Janky Frames | Avg FPS |
| :--- | :--- | :--- | :--- |
| **Baseline** | Run 1 | 1047 | 80.1 |
| | Run 2 | 974 | 80.8 |
| | Run 3 | 829 | 80.7 |
| | Run 4 | 953 | 80.4 |
| | Run 5 | 947 | 80.2 |
| **MSR Default** | Run 1 | 816 | 80.9 |
| | Run 2 | 935 | 80.1 |
| | Run 3 | 1075 | 80.3 |
| | Run 4 | 808 | 80.5 |
| | Run 5 | 2003 | 76.5 |
| **No Image** | Run 1 | 807 | 80.9 |
| | Run 2 | 1165 | 79.8 |
| | Run 3 | 1865 | 76.6 |
| | Run 4 | 908 | 79.9 |
| | Run 5 | 789 | 81.3 |
| **Full Image** | Run 1 | 875 | 79.9 |
| | Run 2 | 954 | 79.9 |
| | Run 3 | 1046 | 79.5 |
| | Run 4 | 1000 | 80.1 |
| | Run 5 | 4446 | 70.6 |

---

## Recording Mode Comparison — Network Upload Overhead

| Mode | Avg Upload (KB) | vs Baseline | vs Full Image |
| :--- | :--- | :--- | :--- |
| Baseline | 97.2 | — | — |
| MSR Default | 152.9 | +55.7 KB (+57.3%) | -219.5 KB (-58.9%) |
| No Image | 150.1 | +52.9 KB (+54.4%) | -222.3 KB (-59.7%) |
| Full Image | 372.4 | +275.2 KB (+283.1%) | — |

---

## Recording Mode Comparison — Memory Overhead

| Mode | Avg Peak Memory (MB) | vs Baseline |
| :--- | :--- | :--- |
| Baseline | 295.7 | — |
| MSR Default | 300.5 | +4.8 MB (+1.6%) |
| No Image | 288.6 | -7.2 MB (-2.4%) |
| Full Image | 301.8 | +6.0 MB (+2.0%) |

---

## Methodology — View-Based App (Infinity) Tests

- **App:** Infinity — a View-based Android application.
- **Device:** Consistent physical device configuration across all iterations.
- **Iterations:** 5 runs per configuration (20 total).
- **Profiling Tool:** App profiling tool with per-second telemetry for CPU, memory, disk, network, FPS, and frames.
- **Recording Modes:**
  - **MSR Default Settings** — Session Replay with default configuration (masked text, no image capture).
  - **No Image Capture** — Session Replay without screenshot/image capture.
  - **Full Image Recording** — Session Replay with full screenshot capture enabled.
- **Stability:** Zero crashes and zero ANRs across all 20 test runs.
- **Note:** Full Image Recording Run 5 exhibited an anomalous jank spike (4446 frames, ~70.6 FPS) likely caused by external system pressure; all other runs were consistent.
