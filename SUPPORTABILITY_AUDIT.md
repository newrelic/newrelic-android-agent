# Supportability Metrics Audit

## Architecture Overview

Metric name constants are centralized in `agent-core/.../metric/MetricNames.java`. Two primary top-level namespaces:

- `Supportability/AgentHealth/` — agent internals (crash, HEx, SessionReplay, AEI, logs, events, payload)
- `Supportability/Mobile/Android/` — API usage, platform features (WebView, WebSocket, NDK, config)
- `Supportability/Events/` — event buffer system
- `Supportability/TraceContext/` — distributed tracing

Metrics are reported via `StatsEngine.SUPPORTABILITY.*(...)` (preferred for supportability metrics) or `StatsEngine.get().*(...)`  (general engine).

---

## Issues Found

### 1. Constants Defined Outside `MetricNames` — `TraceContext.java:37-38`

**Severity: Low**

```java
// Should be in MetricNames.java
public static final String SUPPORTABILITY_TRACE_CONTEXT_CREATED = "Supportability/TraceContext/Create/Success";
public static final String SUPPORTABILITY_TRACE_CONTEXT_EXCEPTION = "Supportability/TraceContext/Create/Exception/%s";
```

These are the only supportability metric constants not in `MetricNames.java`. The `%s` format token is also inconsistent — the rest of the codebase uses suffix concatenation or `MessageFormat` `{0}` style.

**Fix:** Move constants to `MetricNames.java`, update `TraceContext.java` to reference them.

---

### 2. Unused Constant — `MetricNames.java:159`

**Severity: Medium**

`SUPPORTABILITY_SESSION_REPLAY_SAMPLED = "Supportability/AgentHealth/SessionReplay/Sampled/"` is defined but never referenced in any production code.

The analogous log metric `SUPPORTABILITY_LOG_SAMPLED` is actively used:
- `LogReporter.java:130`: records sampling decision on init
- `AndroidAgentImpl.java:1160`: records sampling decision on session start

The SessionReplay equivalent recording call was never added, likely an oversight when the feature was implemented.

**Fix:** Add recording call in `SessionReplayReporter` or `AndroidAgentImpl` where the SR sampling decision is made (mirrors log pattern). Or remove the constant if sampling telemetry is not desired for SessionReplay.

---

### 3. Commented-Out Metric — `ApplicationExitMonitor.java:227`

**Severity: Medium**

```java
// StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_AEI_DROPPED, recordsDropped.get());
```

`SUPPORTABILITY_AEI_DROPPED` is defined in `MetricNames.java:107` but its only usage is commented out. The companion metrics (`SUPPORTABILITY_AEI_VISITED`, `SUPPORTABILITY_AEI_SKIPPED`) are active.

**Fix:** Uncomment if intentional tracking is desired, or remove the constant if the metric is deprecated.

---

### 4. Inconsistent `StatsEngine` Instance in `SessionReplaySender.java`

**Severity: Medium**

The same sender mixes `StatsEngine.get()` and `StatsEngine.SUPPORTABILITY` for supportability metrics:

| Line | Instance | Metric |
|------|----------|--------|
| 130 | `StatsEngine.get()` | `SUPPORTABILITY_SESSION_REPLAY_UPLOAD_TIME` |
| 132 | `StatsEngine.SUPPORTABILITY` | `SUPPORTABILITY_SESSION_REPLAY_COMPRESSED` |
| 133 | `StatsEngine.SUPPORTABILITY` | `SUPPORTABILITY_SESSION_REPLAY_UNCOMPRESSED` |
| 139 | `StatsEngine.get()` | `SUPPORTABILITY_SESSION_REPLAY_UPLOAD_TIMEOUT` |
| 144 | `StatsEngine.get()` | `SUPPORTABILITY_SESSION_REPLAY_URL_SIZE_LIMIT_EXCEEDED` |
| 149 | `StatsEngine.get()` | `SUPPORTABILITY_SESSION_REPLAY_UPLOAD_THROTTLED` |
| 153 | `StatsEngine.SUPPORTABILITY` | `SUPPORTABILITY_SESSION_REPLAY_UPLOAD_REJECTED` |
| 159 | `StatsEngine.get()` | `SUPPORTABILITY_SESSION_REPLAY_FAILED_UPLOAD` |
| 172 | `StatsEngine.get()` | `SUPPORTABILITY_SESSION_REPLAY_FAILED_UPLOAD` |

Other analogous senders (`AEITraceSender`, `LogForwarder`, `NativeReporting`) consistently use `StatsEngine.SUPPORTABILITY` for all supportability metrics.

**Fix:** Standardize all calls in `SessionReplaySender.java` to `StatsEngine.SUPPORTABILITY`.

---

### 5. `SUPPORTABILITY_COLLECTOR` String Concatenation — `Harvester.java`, `HarvestConnection.java`

**Severity: Low**

Multiple metric names are assembled by concatenating the base `SUPPORTABILITY_COLLECTOR` constant with raw string literals. None of the sub-paths have named constants:

| Usage site | Concatenated suffix |
|---|---|
| `HarvestConnection.java:100` | `"Connection/Errors"` |
| `HarvestConnection.java:205` | `"Connect"` |
| `HarvestConnection.java:284` | `"ResponseErrorCodes/" + errorCode` |
| `Harvester.java:158, 256` | `"Harvest"` |
| `Harvester.java:173` | `"Harvest/Connect/Error/" + responseCode` |
| `Harvester.java:246` | `"Harvest/Error/UNKNOWN"` |
| `Harvester.java:254` | `"Harvest/Background"` |
| `Harvester.java:268` | `"Harvest/Error/Background/" + responseCode` |
| `Harvester.java:270` | `"Harvest/Error/" + responseCode` |
| `Harvester.java:336` | `"Harvest/OfflineStorage/" + responseCode` |

**Fix:** Add named constants to `MetricNames.java` for static suffixes and base prefixes for the dynamic (response-code-suffixed) variants.

---

### 6. `AgentHealth.java:47` — Inline Format String

**Severity: Low**

```java
statsEngine.inc(MessageFormat.format("Supportability/AgentHealth/{0}/{1}/{2}/{3}",
    (key == null) ? DEFAULT_KEY : key,
    exception.getSourceClass(),
    exception.getSourceMethod(),
    exception.getExceptionClass()));
```

The format string is hardcoded directly, bypassing `MetricNames.SUPPORTABILITY_AGENT`. No constant exists for this four-segment dynamic pattern.

**Fix:** Extract to a constant in `MetricNames.java`, e.g.:
```java
public static final String SUPPORTABILITY_AGENT_EXCEPTION = SUPPORTABILITY_AGENT + "{0}/{1}/{2}/{3}";
```

---

### 7. Possible Duplicate WebView Metric Recording

**Severity: Medium**

Both files record the same metrics:

- `WebViewInstrumentation.java:16,21` records `SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL`
- `WebViewInstrumentationCallbacks.java:100,104` records `SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL`

Similarly for `WEBVIEW_POST_URL`. Whether this causes double-counting depends on which instrumentation path executes for a given call. This warrants investigation to confirm they're mutually exclusive.

---

### 8. Missing Trailing Slash on `CONFIG_SESSIONREPLAY` Parent — `MetricNames.java:179`

**Severity: Low (style only)**

```java
// Current — no trailing slash on parent, leading slash on children:
public static final String SUPPORTABILITY_MOBILE_ANDROID_CONFIG_SESSIONREPLAY = SUPPORTABILITY_MOBILE_ANDROID + "Config/SessionReplay";
public static final String SUPPORTABILITY_MOBILE_ANDROID_CONFIG_SESSIONREPLAY_ENABLED = SUPPORTABILITY_MOBILE_ANDROID_CONFIG_SESSIONREPLAY + "/Enabled";

// Convention used elsewhere — trailing slash on parent, no leading slash on children:
public static final String SUPPORTABILITY_CRASH = SUPPORTABILITY_AGENT + "Crash/";
public static final String SUPPORTABILITY_CRASH_INVALID_BUILDID = SUPPORTABILITY_CRASH + "InvalidBuildId";
```

The metric names themselves are correct (same output either way). This is purely a code style inconsistency.

**Fix:** Add trailing slash to parent, remove leading slash from children constants (no metric name changes).

---

### 9. Inconsistent Casing in AEI Sub-Metric Names — `MetricNames.java:100-104`

**Severity: Low (do not change — would alter metric names)**

```java
public static final String SUPPORTABILITY_AEI_UNSUPPORTED_OS = SUPPORTABILITY_AEI + "unsupportedOS/";
public static final String SUPPORTABILITY_AEI_EXIT_STATUS    = SUPPORTABILITY_AEI + "status/";
public static final String SUPPORTABILITY_AEI_EXIT_BY_REASON = SUPPORTABILITY_AEI + "reason/";
```

These use lowercase while most other suffix strings use PascalCase (e.g., `"UploadTime"`, `"FailedUpload"`). **Do not fix** — changing the casing would change the actual metric names sent to New Relic, breaking existing dashboards/alerts.

---

## Summary

| # | Issue | Severity | Fixed in branch |
|---|-------|----------|-----------------|
| 1 | TraceContext constants outside MetricNames | Low | Yes |
| 2 | `SUPPORTABILITY_SESSION_REPLAY_SAMPLED` unused | Medium | Needs product decision |
| 3 | `SUPPORTABILITY_AEI_DROPPED` commented out | Medium | Needs product decision |
| 4 | Mixed StatsEngine instances in SessionReplaySender | Medium | Yes |
| 5 | Collector sub-metrics as raw string concatenation | Low | Yes |
| 6 | AgentHealth inline format string | Low | Yes |
| 7 | Possible duplicate WebView metric recording | Medium | Needs investigation |
| 8 | Missing trailing slash on CONFIG_SESSIONREPLAY parent | Low | Yes |
| 9 | AEI sub-metric name casing inconsistency | Low | No (breaking change) |
