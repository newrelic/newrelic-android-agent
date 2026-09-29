# Removing Session Replay from your release build

If your app will never use Session Replay, you can have R8 remove the feature's
code from your release APK entirely, instead of shipping it and leaving it turned
off. Testing against a sample application measured a reduction of approximately
80 KB. The exact figure depends on your app, AGP version, and R8 settings, so
measure your own build.

This is configured entirely in your app. Turning Session Replay off in the New
Relic UI does **not** reduce APK size — backend configuration is read at runtime,
long after R8 has finished compiling.

> **All three steps below are required, and the rule set must be used in full.**
>
> 1. Ignore the agent's bundled ProGuard rules
> 2. Add the New Relic rules to your own ProGuard configuration
> 3. Add the `-assumevalues` rule
>
> Each step depends on the others. Applying only some of them will either remove
> nothing at all, or cause a runtime error when the agent parses its
> configuration. There is no partial configuration that works.

---

## Requirements

| Requirement | Notes |
|---|---|
| `minifyEnabled true` | R8 must run on the variant |
| Android Gradle Plugin 8.1 or newer | Required for the `keepRules` block in Step 1 |
| R8 full mode | Enabled by default on AGP 8+. Do not set `android.enableR8.fullMode=false` |
| No blanket keep rule for New Relic classes | Any `-keep class com.newrelic.** { *; }` in your app, in another library, or in a DexGuard configuration will prevent this from working. See [Troubleshooting](#troubleshooting) |

---

## Step 1 — Ignore the agent's bundled ProGuard rules

The agent AAR ships its own consumer ProGuard rules, which keep the full agent
including Session Replay. ProGuard and R8 keep rules are **additive** — a rule
supplied by a library cannot be overridden or narrowed from your app. To take
control of them, tell AGP not to apply them.

Add the `optimization` block to your minified build type in `app/build.gradle`:

```groovy
android {
    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                          'proguard-rules.pro'

            optimization {
                keepRules {
                    // Option A: Ignore rules from specific libraries using 'group:artifact'
                    ignoreExternalDependencies 'com.newrelic.agent.android:android-agent'

                    // Option B: Ignore ALL external library proguard rules entirely
                    // ignoreAllExternalDependencies true
                }
            }
        }
    }
}
```

Notes:
- Coordinates are `group:artifact` with **no version**.
- Prefer Option A. Option B discards the bundled rules of *every* dependency in
  your build, and you would need to replace all of them yourself.
- Apply this to each minified build type you ship.
- On AGP 8.10 and newer, `ignoreFrom(...)` and
  `ignoreFromAllExternalDependencies(...)` are accepted as aliases.
  `ignoreExternalDependencies` works on all supported versions.

Kotlin DSL (`app/build.gradle.kts`):

```kotlin
release {
    isMinifyEnabled = true
    optimization {
        keepRules {
            ignoreExternalDependencies("com.newrelic.agent.android:android-agent")
            // ignoreAllExternalDependencies(true)
        }
    }
}
```

---

## Step 2 — Add the New Relic rules to `proguard-rules.pro`

Because Step 1 discarded the agent's bundled rules, your app must now supply
them. Copy this block **in full** into `app/proguard-rules.pro`.

```proguard
# =============================================================================
# New Relic Android agent — ProGuard/R8 rules
# Configured to remove Session Replay from the build.
# =============================================================================

# Keep the agent, with two exclusions:
#   - the sessionReplay package, so it can be removed
#   - two store classes that live outside that package but implement its
#     interfaces; keeping them would hold the package in place
-keep class !com.newrelic.agent.android.sessionReplay.**,!com.newrelic.agent.android.stores.FileSessionReplayStore,!com.newrelic.agent.android.stores.FileOfflineSessionReplayStore, com.newrelic.agent.android.** { *; }
-dontwarn com.newrelic.agent.android.**

# Session Replay CONFIGURATION is still delivered and parsed even when the
# feature's code is removed, and it is populated reflectively. These rules keep
# what reflection requires: no-argument constructors and original field names.
#
# These must use -keepclassmembers. A "-keep class ... { *; }" rule here would
# mark the configuration methods as entry points, which stops R8 from applying
# the -assumevalues rule from Step 3 and silently disables the whole
# optimization.
#
# The "$**" rules are required in addition to the plain ones: a rule naming a
# class does not cover its nested classes.
-keepclassmembers class com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration {
    <fields>;
    <init>();
}
-keepclassmembers class com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration$** {
    <fields>;
    <init>();
}
-keepclassmembers class com.newrelic.agent.android.sessionReplay.MobileSessionReplayConfiguration {
    <fields>;
    <init>();
}
-keepclassmembers class com.newrelic.agent.android.sessionReplay.MobileSessionReplayConfiguration$** {
    <fields>;
    <init>();
}
-keepclassmembers class com.newrelic.agent.android.sessionReplay.SessionReplayLocalConfiguration {
    <fields>;
    <init>();
}

# Enum values are matched by constant name during configuration parsing.
-keepclassmembers enum com.newrelic.agent.android.sessionReplay.TextMaskingStrategy {
    <fields>;
}

-keep class com.newrelic.agent.android.sessionReplay.SessionReplayStore { *; }

# Required by the New Relic Gradle plugin
-keepattributes Exceptions, Signature, InnerClasses, LineNumberTable, SourceFile, EnclosingMethod
-keepattributes *Annotation*
-keep class com.newrelic.com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.newrelic.com.google.gson.reflect.TypeToken

# Network libraries instrumented by the agent
-keep class org.apache.http.** { *; }
-keep interface org.apache.http.** { *; }
-keep class com.squareup.okhttp.** { *; }
-keep interface com.squareup.okhttp.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
```

On its own, this block does not remove anything — it replaces the agent's bundled
rules and makes Session Replay *eligible* for removal. Step 3 is what actually
triggers it.

### React Native apps

If your app uses React Native, also add:

```proguard
-keep class com.facebook.react.uimanager.drawable.CSSBackgroundDrawable {
    int mColor;
}
-keep class com.facebook.react.views.view.ReactViewBackgroundDrawable {
    int mColor;
}
-keep class com.facebook.react.uimanager.drawable.BackgroundDrawable {
    int backgroundColor;
}
-keep class com.facebook.react.uimanager.drawable.CompositeBackgroundDrawable {
    android.graphics.drawable.Drawable background;
}
```

---

## Step 3 — Add the `-assumevalues` rule

This is the rule that performs the removal. Add it to `app/proguard-rules.pro`,
alongside the block from Step 2:

```proguard
# Declares at build time that Session Replay is off, which is what allows R8 to
# remove it. Without this rule the agent's internal checks read runtime
# configuration, which R8 cannot evaluate at build time, so it must assume the
# feature may be used and keeps all Session Replay code.
#
# Both methods must be listed — different call sites use each one, and omitting
# either leaves those call sites in the build.
-assumevalues class com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration {
    boolean isEnabled() return false;
    boolean isSessionReplayEnabled() return false;
}
```

Why this cannot be inferred automatically: Session Replay is normally controlled
by configuration delivered from New Relic while your app is running. R8 runs at
build time and has no way to know what that configuration will contain, so it
keeps the code. This rule is you telling R8, at build time, that the answer will
always be "off" for this build.

> **Important:** this rule must not be combined with a
> `-keep class ... SessionReplayConfiguration { *; }` rule. A `-keep` rule marks
> those methods as entry points, and R8 does not apply `-assumevalues` to methods
> that are kept — the optimization would be silently cancelled. The Step 2 rules
> use `-keepclassmembers` specifically to avoid this.

### Keeping Session Replay instead

To build a variant that *does* include Session Replay, remove this Step 3 rule
only. Steps 1 and 2 can stay exactly as they are.

---

## Step 4 — Verify

Temporarily add this rule and rebuild your minified variant:

```proguard
-whyareyoukeeping class com.newrelic.agent.android.sessionReplay.SessionReplayReporter
```

- **No output for that class** — it was removed. The configuration is working.
- **A retention path is printed** — read it. It names the rule or call chain that
  is holding Session Replay in your build, which is the problem to fix.

To measure the saving, compare the total size of `classes*.dex` inside the APK
with and without the `-assumevalues` block. `-printusage <file>` will also list
the removed `com.newrelic.agent.android.sessionReplay` classes.

Remove the `-whyareyoukeeping` rule when you are done.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Nothing is removed | The `-assumevalues` rule is missing. The agent's internal checks read runtime configuration, which R8 cannot evaluate on its own. | Add the Step 3 rule — it is what performs the removal |
| Nothing is removed | A blanket `-keep class com.newrelic.** { *; }` exists in your app, in another library, or in a DexGuard configuration. It keeps the code *and* marks the configuration methods as entry points, which cancels `-assumevalues`. Keep rules are additive and cannot be overridden. | Remove that rule. Older New Relic setup guidance recommended it; it is incompatible with this optimization |
| Nothing is removed | Step 1 was skipped, so the agent's bundled rules still apply and keep the whole package | Add the `optimization { keepRules { ... } }` block |
| `RuntimeException: Unable to invoke no-args constructor for ...SessionReplayConfiguration$a` while the agent parses configuration | The Session Replay configuration classes lost the constructor reflection needs. A rule naming a class does not cover its nested classes. | Include the `$**` `-keepclassmembers` rules from Step 2 |
| Text masking settings appear to be ignored | `TextMaskingStrategy` enum constants were renamed | Include the enum rule from Step 2 |
| Works in debug, fails only in release | R8 does not run on debug builds | Always test the minified variant |

If some Session Replay code remains in the APK, the build is still correct — R8
never leaves a broken program. A partial result means fewer bytes saved, not a
stability risk, so it is safe to investigate at your own pace.

---

## What changes at runtime

- No replays are captured or uploaded. Enabling Session Replay in the New Relic
  UI has **no effect** on a build compiled this way; it must be rebuilt without
  the `-assumevalues` block.
- Session Replay configuration is still received in the harvest response and
  parsed, then ignored. This is why the configuration classes must remain
  reflection-readable.
- Replay data already cached on the device from an earlier build is not uploaded
  and not deleted. It ages out through the cache's normal size limit.
- All other agent functionality is unaffected: crash reporting, handled
  exceptions, network monitoring, interaction traces, events, logging, and
  distributed tracing continue to work normally.
