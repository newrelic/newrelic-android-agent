# Session Replay Performance Test Results - Android (Jetpack Compose)

**Test Date:** April 2, 2026
**Platform:** Android - Real Device (LambdaTest)
**UI Framework:** **Jetpack Compose** (Modern Declarative UI)
**Test Environment:** LambdaTest Real Device Cloud (Pixel 8, Android 14)
**Agent Version:** New Relic Android Agent 7.7.1
**Test Iterations:** 5 runs per configuration

> **Note:** This test uses Jetpack Compose with Material 3 components. For traditional XML layouts results, see the companion performance-test-app-xml.

---

## 📊 Test Data (Averaged across 5 runs)

| Metric | No Agent | Agent Baseline | Agent + Session Replay |
|--------|----------|----------------|------------------------|
| **Average Memory** | 207.12 ± 3.83 MB | 208.37 ± 1.25 MB | 210.72 ± 6.08 MB |
| **Peak Memory** | 272.44 ± 27.19 MB | 261.82 ± 6.01 MB | 274.71 ± 27.15 MB |
| **Average CPU** | 25.50 ± 1.08% | 26.98 ± 0.31% | 33.65 ± 0.95% |
| **Peak CPU** | 112.59 ± 24.69% | 127.82 ± 21.80% | 130.59 ± 12.14% |
| **Test Runs** | 5 | 5 | 5 |

---

## 🎯 Session Replay Overhead

**Comparison:** Agent Baseline vs Agent + Session Replay

This measures the **incremental overhead** of enabling Session Replay on top of the New Relic agent.

### Memory Impact
| Metric | Agent Baseline | Agent + SR | Overhead | % Increase |
|--------|----------------|------------|----------|------------|
| Average Memory | 208.37 MB | 210.72 MB | **+2.35 MB** | **+1.13%** |
| Peak Memory | 261.82 MB | 274.71 MB | **+12.89 MB** | **+4.92%** |

### CPU Impact
| Metric | Agent Baseline | Agent + SR | Overhead | % Increase |
|--------|----------------|------------|----------|------------|
| Average CPU | 26.98% | 33.65% | **+6.67%** | **+24.72%** |
| Peak CPU | 127.82% | 130.59% | +2.77% | +2.17% |

---

## 📈 Summary

**Session Replay incremental overhead** (when added to an app already using the New Relic agent):
- **+2.35 MB memory** (+1.13% increase)
- **+6.67% CPU usage** (+24.72% increase)

The overhead is minimal and within acceptable limits for production use.

---

## 🔬 Three-Way Comparison (No Agent vs Agent vs Agent + Session Replay)

To understand the incremental overhead of each component, we tested three configurations:
1. **No Agent** - Pure app without New Relic agent
2. **Agent Baseline** - New Relic agent enabled, Session Replay disabled
3. **Agent + Session Replay** - Both agent and Session Replay enabled

### Performance Metrics Across All Configurations

| Metric | No Agent | Agent Baseline | Agent + SR |
|--------|----------|----------------|------------|
| **Avg Memory** | 207.12 MB | 208.37 MB | 210.72 MB |
| **Peak Memory** | 272.44 MB | 261.82 MB | 274.71 MB |
| **Avg CPU** | 25.50% | 26.98% | 33.65% |
| **Peak CPU** | 112.59% | 127.82% | 130.59% |
| **Test Runs** | 5 | 5 | 5 |

### Incremental Overhead Breakdown

This table shows how each component adds overhead, building from the pure app (No Agent):

| Metric | Step 1: Agent Overhead<br>(vs No Agent) | Step 2: Session Replay Overhead<br>(vs Agent Baseline) | **Total Overhead**<br>**(vs No Agent)** |
|--------|------------------------------------------|--------------------------------------------------------|------------------------------------------|
| **Average Memory** | +1.25 MB<br>(+0.60%) | +2.35 MB<br>(+1.13%) | **+3.60 MB<br>(+1.74%)** |
| **Peak Memory** | -10.62 MB<br>(-3.90%) | +12.89 MB<br>(+4.92%) | **+2.27 MB<br>(+0.83%)** |
| **Average CPU** | +1.48%<br>(+5.80%) | +6.67%<br>(+24.72%) | **+8.15%<br>(+31.96%)** |
| **Peak CPU** | +15.23%<br>(+13.53%) | +2.77%<br>(+2.17%) | **+18.00%<br>(+15.99%)** |

**Reading this table:**
- **Step 1 (Agent):** Overhead when adding the New Relic agent to the pure app (No Agent)
- **Step 2 (Session Replay):** Additional overhead when enabling Session Replay on top of Agent Baseline
- **Total:** Combined overhead of agent + Session Replay vs pure app (No Agent)

### Key Insights

1. **Agent Overhead**: The New Relic agent itself adds ~1.25 MB memory with minimal CPU impact (vs No Agent)
2. **Session Replay Incremental**: Session Replay adds an additional ~2.35 MB memory and ~6.67% CPU on top of Agent Baseline
3. **Combined Impact**: Total overhead is ~3.60 MB (1.74%) memory and ~8.15% (31.96%) CPU vs pure app (No Agent)
4. **Production Ready**: Both agent and Session Replay overheads are within acceptable limits for production use
5. **Consistent Results**: Standard deviations show consistent performance across 5 test runs

---

## 🧪 Test Methodology

### Test Scenario
The test performed the following intensive operations:
1. **Navigation Stress Test** (5 iterations)
   - Navigate to Infinite Scroll screen
   - Navigate back to home
   - Tests navigation capture and memory management

2. **Infinite Scroll Test**
   - 4 scrolls down
   - 2 scrolls up
   - Tests continuous scroll event capture

3. **Image Gallery Scroll Test**
   - 4 scrolls through image gallery
   - Tests visual content capture with images

4. **UI Elements Interactions**
   - Button taps, switches, sliders
   - Scroll through UI elements
   - Tests UI interaction capture

5. **Network Test**
   - 3 GET request button taps
   - Tests network instrumentation

6. **Navigation Flow Test**
   - 5 sequential screen navigations
   - Tests multi-screen flow capture

7. **Metrics Collection**
   - Resource metrics collected every 2 seconds
   - Memory usage (MB) via Debug.MemoryInfo
   - CPU usage (%) via /proc/[pid]/stat

### Configuration
- **Device:** Pixel 8, Android 14 (LambdaTest Real Device)
- **UI Framework:** Jetpack Compose with Material 3 (Button, LazyColumn, Card, etc.)
- **Test Framework:** WebDriverIO + Appium
- **Agent:** New Relic Android Agent 7.7.1 with Gradle Plugin
- **Test Duration:** ~2-3 minutes per test
- **Test Iterations:** 5 runs per configuration for statistical significance
- **No Agent:** New Relic library present but not initialized (AGENT_ENABLED=false)
- **Agent Baseline:** Agent enabled, Session Replay disabled (BASELINE=1)
- **Agent + Session Replay:** Agent enabled, Session Replay enabled via server configuration

---

## ✅ Verdict

**SESSION REPLAY OVERHEAD IS ACCEPTABLE**

All metrics are within acceptable thresholds:
- ✅ Average memory overhead (2.35 MB) is within limits (< 50 MB)
- ✅ Memory overhead percentage (1.13%) is within limits (< 25%)
- ✅ CPU overhead (24.72%) is within limits (< 30%)
- ✅ Consistent results across 5 test runs

Session Replay is **suitable for production use** with minimal performance impact.

---

*Generated from automated performance testing on LambdaTest Real Device Cloud (5 iterations per configuration)*
*Test App: Jetpack Compose (Modern Declarative UI)*
