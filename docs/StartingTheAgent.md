# Where to call `NewRelic.start()`

The agent is documented as started from the launcher `Activity`. This note records what the code does when
`NewRelic.start()` is called from the launcher `Activity` and from `Application.onCreate()`, and what differs
between the two.

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
   Started from `Application`, the agent exists before any `Activity` does. Tracing is gated by
   `FeatureFlag.InteractionTracing` and `Harvest.shouldCollectActivityTraces()`, which comes from the collector
   configuration (`TraceMachine.enabled` is true by default). After moving `start()`, confirm in a staging build
   that interaction traces still appear.
2. **Main-thread work in `start()`.** `AndroidAgentImpl`'s constructor creates `SavedState`, the offline
   storage and the file stores synchronously on the calling thread. From an `Activity` that is inside the
   first screen's `onCreate()`; from `Application` it is inside process start. Either way StrictMode reports
   disk reads.
3. **Every process runs `Application.onCreate()`.** An app that declares components with `android:process`
   gets a second `Application` instance in each of those processes, so `start()` would run in each, each with
   its own session, collector connection and harvest timer. The agent does not check which process it is in.
4. **Hybrid frameworks.** When `Agent.getMonoInstrumentationFlag()` is `YES` (MAUI / Xamarin builds), the
   agent registers its own `ActivityLifecycleBackgroundListener` through the application context during
   `init()`, so the context passed to `start()` must resolve to an `Application`.

## How to tell where and whether the agent started

- Run with `withLogLevel(AgentLog.DEBUG)` (or higher) and `withLoggingEnabled(true)`. `start()` logs
  `Agent started from <class>.<method>:<line>` at debug level.
- Look for `HarvestTimer: Starting with a period of ...` and `Harvester: connected` in logcat (tag `newrelic`).
- `NewRelic.isStarted()` is `true` after `init()` returns, including in a process where it should not run.
