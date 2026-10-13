# Where to call `NewRelic.start()`

Tracks NR-565010. The agent is documented publicly as started from the launcher `Activity`. A customer
asked why it cannot simply be started in `Application.onCreate()`; this note records what the code does in
each case, what is known to differ, and what is still open.

## Supported call sites

`NewRelic.start(Context)` is idempotent: a second call while the agent is running logs `NewRelic is already running.` and returns
(`NewRelic.java`, `started` flag). The two call sites people use:

| Call site | Status |
|---|---|
| Launcher `Activity.onCreate()` | Documented default, most tested |
| `Application.onCreate()` | Works mechanically; see the differences and open questions below |

`NewRelic.start()` requires a build that went through the Gradle plugin (`NewRelic.isInstrumented()` is
rewritten to return `true` at build time). Without instrumentation the call logs a recourse message and the
agent does not run, wherever it is called from.

## Starting from `Application` when the app has no `Application` class

Many apps have no `Application` subclass. To start the agent from one, the app has to add it:

1. Create a class extending `android.app.Application` and call `NewRelic...start(this)` from `onCreate()`
   after `super.onCreate()`.
2. Register it with `android:name` on the `<application>` element of `AndroidManifest.xml`
   (`android:name=".MyApplication"`, or a fully qualified name). Without the manifest entry Android never
   instantiates the class and the agent never starts, with no error.
3. Remove the `start()` call from the launcher `Activity`. A second call is ignored, but leaving both makes
   it unclear which one started the agent.

An app that already has an `Application` subclass only needs the `start()` call added to its `onCreate()`.

## What does not depend on the call site

- **Launch timing and lifecycle observation start earlier than `start()`.** `NewRelicAppContentProvider`
  (merged into every app manifest) runs `AppApplicationLifeCycle.onColdStartInitiated()` from
  `ContentProvider.onCreate()`, which is before `Application.onCreate()`. It registers the
  `ActivityLifecycleCallbacks` and records process-start and application-create times, using
  `context.getApplicationContext()` so wrapped contexts do not cause a cast failure.
- **Build-time instrumentation** (Activity/Fragment lifecycle tracing, network call interception, build ID)
  is identical; it does not know where `start()` is called.

## What differs

1. **Instrumented calls that run before `start()`.** The injected `TraceMachine.startTracing(...)` and
   `ApplicationStateMonitor` calls in an `Activity`'s lifecycle methods run in whatever state the agent is in
   at that moment. Started from the `Activity`, the first `onCreate()` has already begun when `start()` runs.
   Started from `Application`, the agent exists before any `Activity` does. The ticket records that starting
   in the application class has historically caused tracing issues; the code read for this note does not
   explain why (`TraceMachine.enabled` is true by default, so tracing is gated by `FeatureFlag.InteractionTracing`
   and `Harvest.shouldCollectActivityTraces()`, which comes from the collector configuration). **Open: the
   test results from NR-565010 must be added here.**
2. **Main-thread work in `start()`.** `AndroidAgentImpl`'s constructor creates `SavedState`, the offline
   storage and the file stores synchronously on the calling thread. From an `Activity` that is inside the
   first screen's `onCreate()`; from `Application` it is inside process start. Either way StrictMode reports
   disk reads (NR-596375, whose fix in PR #594 was closed unmerged).
3. **Every process runs `Application.onCreate()`.** An app that declares components with `android:process`
   gets a second `Application` instance in each of those processes, so `start()` would run in each, each with
   its own session, collector connection and harvest timer. The agent does not check which process it is in.
   A main-process guard is proposed in NR-603405 and is not in this repository yet.
4. **Hybrid frameworks.** When `Agent.getMonoInstrumentationFlag()` is `YES` (MAUI / Xamarin builds), the
   agent registers its own `ActivityLifecycleBackgroundListener` through the application context during
   `init()`, so the context passed to `start()` must resolve to an `Application`.

## How to tell where and whether the agent started

- Run with `withLogLevel(AgentLog.DEBUG)` (or higher) and `withLoggingEnabled(true)`. `start()` logs
  `Agent started from <class>.<method>:<line>` at debug level.
- Look for `HarvestTimer: Starting with a period of ...` and `Harvester: connected` in logcat (tag `newrelic`).
- `NewRelic.isStarted()` is `true` after `init()` returns, including in a process where it should not run.

## Open items before the public docs promote this

- Tracing results from the NR-565010 test runs (point 1 above).
- Whether to ship the NR-603405 main-process guard first, so the public page can drop its multi-process warning.
- Public page: `install-android-agent-gradle.mdx` currently says other classes are unsupported. The
  docs-website change for NR-565010 replaces that with a section that describes the Application-class option
  and tells readers to test in staging.
