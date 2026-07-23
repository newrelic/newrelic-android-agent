# New Relic Android Agent

Mobile performance monitoring agent for Android. **Two-phase architecture:** build-time bytecode instrumentation + runtime monitoring and data collection.

## Modules

| Module | Path | Type | Output |
|---|---|---|---|
| **agent-core** | `/agent-core` | Java 17 library | `agent-core-<v>.jar` (fat via Shadow) |
| **instrumentation** | `/instrumentation` | Java 17 library | `instrumentation-<v>.jar` (ASM relocated) |
| **agent** | `/agent` | Android library | `android-agent-<v>.aar` |
| **plugins/gradle** | `/plugins/gradle` | Gradle plugin (Groovy + Kotlin) | `agent-gradle-plugin-<v>.jar` |

Dependency chain: `agent` → `agent-core` + `instrumentation`. The Gradle plugin orchestrates build-time rewriting.

### agent-core — runtime agent
Runtime implementation, public SDK (`NewRelic.java`), data collection, harvest system, analytics, session management.

Key subdirectories:
```
agent-core/src/main/java/com/newrelic/agent/android/
├── crash/                    # Crash detection → .claude/rules/crash-flow.md
├── tracing/                  # Activity/interaction tracing → .claude/rules/tracing.md
├── harvest/                  # Data harvesting and transmission
├── measurement/              # Performance metrics
├── instrumentation/          # HTTP/network runtime hooks
├── analytics/                # Events, breadcrumbs
├── sessionReplay/            # Session replay
└── distributedtracing/       # Trace context propagation
```

Key classes: `Agent` (singleton facade), `AndroidAgentImpl` (main impl), `AgentConfiguration`, `Harvest`, `TraceMachine`.

### agent — Android wrapper
Combines agent-core + instrumentation into an AAR. Handles Android Context integration, lifecycle callbacks, NDK bridge.

```
agent/src/main/java/com/newrelic/agent/android/
├── instrumentation/          # Runtime hooks (OkHttp, etc.)
├── ndk/                       # Native crash bridge
├── rum/                       # Real User Monitoring (Activity lifecycle)
├── stores/                    # SharedPreferences persistence
├── sessionReplay/             # Session replay rendering
└── webView/                   # WebView instrumentation
```

Fat JAR lands at `build/intermediates/aar_main_jar/release/classes.jar`. Shadow plugin uses multi-phase JAR build for Gson compatibility (unshaded + shaded → merged).

### instrumentation — bytecode rewriter
See **`.claude/rules/instrumentation.md`**.

### plugins/gradle — build-time plugin
See **`.claude/rules/plugin-architecture.md`**.

## Path-Gated Rule Files

Load the appropriate rule file before editing these areas:

| Path | Rule |
|---|---|
| `plugins/gradle/**`, `buildconfig.gradle` | `.claude/rules/plugin-architecture.md` |
| `instrumentation/**` | `.claude/rules/instrumentation.md` |
| `agent-core/src/main/java/com/newrelic/agent/android/crash/**`, `agent/**/ndk/**` | `.claude/rules/crash-flow.md` |
| `agent-core/**/tracing/**` | `.claude/rules/tracing.md` |

## Build-Time vs Runtime Split

**Build time:** Gradle plugin runs `NewRelicConfigTask` (generates `NEW_RELIC_BUILD_ID`), `ClassTransformer` (rewrites `isInstrumented()`, `getBuildId()`, injects Activity/network hooks), and `NewRelicMapUploadTask` (uploads ProGuard/NDK maps).

**Runtime:** `NewRelic.start(context)` → `AndroidAgentImpl.init()` → harvest timer (60s) collects metrics, traces, events, crashes and POSTs to backend.

### Build ID caching (NR-556102)

`NewRelicConfigTask` generates a random build ID per build. It used to embed that value directly in the compiled `NewRelicConfig.java`, which busted `compileReleaseJavaWithJavac`/`compileReleaseKotlin`/`minifyReleaseWithR8` caching on every CI build (a changed source file forces a cache miss for anything that touches it). Fixed by moving the real value into a generated Android string resource (`values/com_newrelic_android_agent_config.xml`, entry `com.newrelic.android.buildId`) instead — `NewRelicConfig.java`'s `BUILD_ID` field is now a stable placeholder, so compile/minify inputs stop changing build-to-build. Wired per AGP adapter via `sources.res.addGeneratedSourceDirectory(...)` (4.x uses `registerGeneratedResFolders`). At runtime, `AndroidAgentImpl` resolves the value via `Resources.getIdentifier(...)` during `init()` and pushes it to `Agent.setBuildId(...)`; `Agent.getBuildId()` falls back to the old `NewRelicConfig` reflection path if the resource is absent (covers an old-plugin + new-agent-core version mismatch).

## Data Flow Summary

```
1. BUILD TIME — plugin rewrites classes, uploads maps
2. RUNTIME INIT — NewRelic.start(), harvest timer, crash handler
3. RUNTIME ONGOING — activity traces, network interception, breadcrumbs; 60s harvest
4. RUNTIME CRASH — UncaughtExceptionHandler → CrashStore → upload on next launch
```

## Key Architectural Patterns

- **Null Object** — `NullAgentImpl` when instrumentation missing/disabled
- **Facade** — `NewRelic` (public API), `Agent` (internal switchable impl)
- **Visitor** — ASM `ClassVisitor` chain for bytecode transformation
- **State Machine** — `TraceMachine` (trace state + thread-local)
- **Thread-Safe Singleton** — `Agent.impl` (lock for impl switching), `StatsEngine`, `AnalyticsControllerImpl`
- **Factory** — `VariantAdapter.register()`, `ClassVisitorFactory`
- **Observable** — `HarvestLifecycleAware`, `ApplicationStateListener`, `TraceLifecycleAware`
- **Strategy** — per-AGP variant adapters

## Common Modification Points

**Add new framework instrumentation:** see `.claude/rules/instrumentation.md`.

**Add new metric:**
1. Define in `MetricNames`
2. Increment via `StatsEngine.get().inc(metricName)`
3. Surface in next harvest via `MeasurementProducer`

**Add new public API method:**
1. Add static method to `NewRelic`
2. Implement in `AndroidAgentImpl`
3. Add null impl in `NullAgentImpl`
4. Document in samples/tests

## Runtime Configuration

```java
NewRelic.withApplicationToken(token)
    .withLoggingEnabled(true)
    .withLogLevel(AgentLog.INFO)
    .withApplicationVersion("1.0.0")
    .withApplicationFramework(ApplicationFramework.NATIVE, "version")
    .withCrashReportingEnabled(true)
    .start(context);
```

Build-side DSL (`newrelic { ... }`) is in `.claude/rules/plugin-architecture.md`.

## Feature Flags (`FeatureFlag.java`)
`DistributedTracing`, `InteractionTracing`, `CrashReporting`, `HandledExceptions`, `HttpResponseBodyCapture`, `NetworkRequest`, `SessionReplay`.

## Key Dependencies

- **agent-core:** ASM (shadowed `com.newrelic.org.objectweb.asm`), Gson (shadowed, special handling), FlatBuffers
- **instrumentation:** ASM 9.x
- **Build toolchain:** AGP 8.1.4 (compileSdk 34), Gradle 8.1.1, Kotlin 1.7.0, Java 17 minimum

Gradle 8 migration gotchas are in `.claude/rules/plugin-architecture.md`.

## Code Conventions

- **New files must be written in Kotlin.** Existing Java files may be edited in place, but any newly created source or test file should be Kotlin (`.kt`). Don't rewrite existing Java just to convert it.

## Testing Strategy

- **Unit:** Robolectric for Activity simulation
- **Integration:** Gradle TestKit for plugin behavior
- **Functional:** End-to-end with sample app
- **Instrumentation tests:** verify bytecode transformations

## Investigation Checklist

1. Module chain: `agent → agent-core + instrumentation`
2. Build-time vs runtime? Was instrumentation applied? Check bytecode or `NewRelic.isInstrumented()` at runtime
3. Trace data flow: collection → `MeasurementProducer` → `Harvest` → transmission
4. AGP compatibility → variant adapters (`.claude/rules/plugin-architecture.md`)
5. Shadow JAR relocations move classes to `com.newrelic.*` — affects reflection
6. For bytecode method injection, look for `GeneratorAdapter` usage

## Frequently Modified Files
- `/agent-core/src/main/java/com/newrelic/agent/android/AndroidAgentImpl.java`
- `/instrumentation/src/main/java/com/newrelic/agent/compile/visitor/`
- `/plugins/gradle/src/main/groovy/com/newrelic/agent/android/`
- `/agent-core/src/main/java/com/newrelic/agent/android/crash/`
- `/agent-core/src/main/java/com/newrelic/agent/android/tracing/TraceMachine.java`
# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

### Code Intelligence

Prefer LSP over Grep/Glob/Read for code navigation:
- `goToDefinition` / `goToImplementation` to jump to source
- `findReferences` to see all usages across the codebase
- `workspaceSymbol` to find where something is defined
- `documentSymbol` to list all symbols in a file
- `hover` for type info without reading the file
- `incomingCalls` / `outgoingCalls` for call hierarchy

Before renaming or changing a function signature, use
`findReferences` to find all call sites first.

Use Grep/Glob only for text/pattern searches (comments,
strings, config values) where LSP doesn't help.


After writing or editing code, check LSP diagnostics before
moving on. Fix any type errors or missing imports immediately.

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
