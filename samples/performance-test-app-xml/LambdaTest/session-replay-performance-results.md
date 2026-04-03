# Session Replay Performance Test Results - Android (XML Layouts)

**Test Date:** April 2, 2026
**Platform:** Android - Real Device (LambdaTest)
**UI Framework:** **XML Layouts** (Traditional Android Views)
**Test Environment:** LambdaTest Real Device Cloud (Pixel 8, Android 14)
**Agent Version:** New Relic Android Agent 7.7.1
**Test Iterations:** 5 runs per configuration

> **Note:** This test uses traditional XML layouts with Android Views (MaterialButton, RecyclerView, etc.). For Jetpack Compose results, see the companion performance-test-app (Compose-based).

---

## 📊 Test Data (Averaged across 5 runs)

| Metric | No Agent | Agent Baseline | Agent + Session Replay |
|--------|----------|----------------|------------------------|
| **Average Memory** | 160.52 ± 1.36 MB | 164.57 ± 1.83 MB | 164.98 ± 4.01 MB |
| **Peak Memory** | 218.20 ± 1.62 MB | 223.27 ± 2.21 MB | 228.76 ± 8.70 MB |
| **Average CPU** | 19.45 ± 0.55% | 20.63 ± 0.83% | 25.10 ± 0.55% |
| **Peak CPU** | 31.75 ± 1.29% | 38.20 ± 1.09% | 43.87 ± 1.11% |
| **Test Runs** | 5 | 5 | 5 |

---

## 🎯 Session Replay Overhead

**Comparison:** Agent Baseline vs Agent + Session Replay

This measures the **incremental overhead** of enabling Session Replay on top of the New Relic agent.

### Memory Impact
| Metric | Agent Baseline | Agent + SR | Overhead | % Increase |
|--------|----------------|------------|----------|------------|
| Average Memory | 164.57 MB | 164.98 MB | **+0.41 MB** | **+0.25%** |
| Peak Memory | 223.27 MB | 228.76 MB | **+5.49 MB** | **+2.46%** |

### CPU Impact
| Metric | Agent Baseline | Agent + SR | Overhead | % Increase |
|--------|----------------|------------|----------|------------|
| Average CPU | 20.63% | 25.10% | **+4.47%** | **+21.67%** |
| Peak CPU | 38.20% | 43.87% | +5.67% | +14.84% |

---

## 📈 Summary

**Session Replay incremental overhead** (when added to an app already using the New Relic agent):
- **+0.41 MB memory** (+0.25% increase)
- **+4.47% CPU usage** (+21.67% increase)

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
| **Avg Memory** | 160.52 MB | 164.57 MB | 164.98 MB |
| **Peak Memory** | 218.20 MB | 223.27 MB | 228.76 MB |
| **Avg CPU** | 19.45% | 20.63% | 25.10% |
| **Peak CPU** | 31.75% | 38.20% | 43.87% |
| **Test Runs** | 5 | 5 | 5 |

### Incremental Overhead Breakdown

This table shows how each component adds overhead, building from the pure app (No Agent):

| Metric | Step 1: Agent Overhead<br>(vs No Agent) | Step 2: Session Replay Overhead<br>(vs Agent Baseline) | **Total Overhead**<br>**(vs No Agent)** |
|--------|------------------------------------------|--------------------------------------------------------|------------------------------------------|
| **Average Memory** | +4.05 MB<br>(+2.52%) | +0.41 MB<br>(+0.25%) | **+4.46 MB<br>(+2.78%)** |
| **Peak Memory** | +5.07 MB<br>(+2.32%) | +5.49 MB<br>(+2.46%) | **+10.56 MB<br>(+4.84%)** |
| **Average CPU** | +1.18%<br>(+6.07%) | +4.47%<br>(+21.67%) | **+5.65%<br>(+29.05%)** |
| **Peak CPU** | +6.45%<br>(+20.31%) | +5.67%<br>(+14.84%) | **+12.12%<br>(+38.17%)** |

**Reading this table:**
- **Step 1 (Agent):** Overhead when adding the New Relic agent to the pure app (No Agent)
- **Step 2 (Session Replay):** Additional overhead when enabling Session Replay on top of Agent Baseline
- **Total:** Combined overhead of agent + Session Replay vs pure app (No Agent)

### Key Insights

1. **Agent Overhead**: The New Relic agent itself adds ~4.05 MB memory with minimal CPU impact (vs No Agent)
2. **Session Replay Incremental**: Session Replay adds an additional ~0.41 MB memory and ~4.47% CPU on top of Agent Baseline
3. **Combined Impact**: Total overhead is ~4.46 MB (2.78%) memory and ~5.65% (29.05%) CPU vs pure app (No Agent)
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
- **UI Framework:** XML Layouts with Android Views (MaterialButton, RecyclerView, ScrollView)
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
- ✅ Average memory overhead (0.41 MB) is within limits (< 50 MB)
- ✅ Memory overhead percentage (0.25%) is within limits (< 25%)
- ✅ CPU overhead (21.67%) is within limits (< 30%)
- ✅ Consistent results across 5 test runs

Session Replay is **suitable for production use** with minimal performance impact.

---

*Generated from automated performance testing on LambdaTest Real Device Cloud (5 iterations per configuration)*
*Test App: XML Layouts (Traditional Android Views)*
