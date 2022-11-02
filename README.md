# New Relic Android Agent

> The New Relic Android agent provides performance monitoring instrumentation for Android applications.


# [Run Book](RUNBOOK.md)


## Overview

The Android agent is comprised of several modules that are used together to instrument client applications.

### Agent (`:agent`)
Artifact that wraps `agent-core` into an Android-compatible library project. This is a mixed-Java JAR.

### Agent Core (`:agent-core`)
Artifact whose classes are renamed and repackaged to a fat JAR. This is a pure-Java JAR (no Android) and contains the business rules for the agent.

### Class Rewriter (`:class-rewriter`)
Artifact that allows JVMs to perform auto-instrumentation during builds. This is a pure-Java JAR (no Android) and includes what becomes the Java agent.

### Gradle plugin (`:plugins:gradle`)
Artifact that, when installed into AndroidStudio" performs auto-instrumentation during certain build phases.

### Maven plugin
The Android Maven plugin is no longer supported.

# Setting up your IDE

### Android Studio

* [Download](https://developer.android.com/sdk/index.html) the Android Studio IDE.
* If you haven't done so already, clone the [android_agent repo](https://github.com/newrelic/android_agent) from Github.
* From Android Studio's main menu, select File -> Import Project, hit next and provide full path to your cloned android_agent repo.
* Install SDK Platforms. SDK Manager left pane: Appearances & Behavior -> System Settings -> Android SDK. Right Pane select SDK Platforms. Select Android 11.0 (API 30) down to Android 8.0 (API 26) and "Apply". (optional)

### Environment Variables
Determine Android SDK paths. Android Studio -> Tools -> SDK Manager, SDK Manager left pane: Appearances & Behavior -> System Settings -> Android SDK.  
Edit `~/.bashrc` or `~/.zshrc` file by adding 
```
export ANDROID_SDK_HOME=/Users/{username}/Library/Android/sdk
export ANDROID_SDK_ROOT=/Users/{username}/Library/Android/sdk
```

Either `source .bashrc` or open a new terminal window and then comfirm new env vars with `set | grep -i android`

### JDK
* [Download](https://adoptopenjdk.net/?variant=openjdk8&jvmVariant=hotspot) and install the Java 8 development Kit. Select Version: OpenJDK8 and JVM: HotSpot.
* Accept all SDK licenses: `yes | ~/Library/Android/sdk/tools/bin/sdkmanager --licenses`
* [Download](https://www.jenv.be) jEnv to manage your local Java environment. (optional)
* Confirm version of Java: `java -version` should return AdoptOpenJDK build 1.8.*

### IntelliJ IDEA
Officially `Not Supported`, but OS X doesn't pass through env vars so you'll have to run this on a command line and restart IDEA: <code>launchctl setenv ANDROID_HOME $ANDROID_HOME</code>

# Git Submodules
Pull in the submodules with `git submodule update --init --recursive`

# Building the Android Agent

## Gradle

The agent can be built from either the command line or Android Studio. From the command line, enter `./gradlew clean build`. From AndroidStudio, each module should be built using the `Release` configuration.

The agent version is contained in `gradle.properties`, but can be overridden by adding `-Pnewrelic.agent.version={version}` to the Gradlew command line.


# Tests
### Unit Tests

|Module|Reports found here|
|---|---|
|`agent`|file://agent/build/reports/tests/release/index.html|
|`agent-core`|file://agent-core/build/reports/tests/index.html|

### Integration Tests `[Incubating]`
> TBA

### Regression Tests `[Incubating]`
> TBA


# Static Analysis Reports
|Module|Reports found here|
|---|---|
|`Lint`|file://agent/build/outputs/lint-results-release.html|
|`FindBugs`|file://agent-core/build/reports/findbugs/main.xml|
|`PMD`|file://agent-core/build/reports/pmd/main.html|
||file://agent-core/build/reports/pmd/test.html|
||file://class-rewriter/build/reports/pmd/main.html|


# Coverage Reports

|Module|Reports found here|
|---|---|
|`agent`|file://agent/build/reports/jacoco/jacocoTestReport/html/index.html|
|`agent-core`|file://agent-code/build/reports/jacoco/jacocoTestReport/html/index.html|


# Debugging the agent

The simplest way to debug the agent is at runtime through a test app.

* Create a test app that has been configured to use the agent.
* Run a debugging session of the test app, but before you start execution, browse the `External Libraries` dependencies from the AS `Project` pane (all the way at the bottom).
* Drill down to `External Libraries/android-agent-{version}/android-agent-{version}.jar/com/newrelic/agent/android/NewRelic`
* Open the file, and set a break point just inside the start() method. Now debug the app and execution will break inside the agent.

Debugging the class rewriter or plugins is more difficult but you can use a ```Remote``` debugging session and command line Gradle builds.
* In AndroidStudio, create a `Remote` _Run/Debug configuration_.
* From your command line, `export` the environment variable `GRADLE_OPTS` as:</br></br>
```export GRADLE_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5050"```</br></br>
* Initiate a command line build of your test app, i.e., `./gradlew clean assembleDebug`
* You should then see `Listening for transport dt_socket at address: 5005`
* Set some breakpoints in the agent or class rewriter, and start the remote debugging session
* You are now live-debugging the Gradle build, specifically those tasks that interact with Agent code
* To remove GRADLE_OPTS from the environment, simply ```unset GRADLE_OPTS```

## Debugging with Android Studio
Starting in version 3.0, Android Studio has made it easier to debug client builds using remote debugging. You no longer have
to declare a `GRADLE_OPTS` environment variable; rather, simply start a command line build with `-Dorg.gradle.debug=true --no-daemon` declared in either command line or 'gradle.properties` file.

```When set to true, Gradle will run the build with remote debugging enabled, listening on port 5005. Note that this is the equivalent of adding -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 to the JVM command line and will suspend the virtual machine until a debugger is attached. ```

You will no longer see `Listening for transport dt_socket at address: 5005`, but instead now see `> Starting Daemon` when the build waits for a remote debugger to attach. Simply run your _Remote_ configuration (created above) and the debugger will stop at any breakpoints set. 

## Debugging the Agent Gradle Plugin
The easiest way to debug the Agent Gradle Plugin is to run the [PluginFunctionalSpec test](plugins/gradle/src/test/groovy/com/newrelic/agent/android/PluginFunctionalSpec.groovy). 
When the GradleRunner is created with `withDebug(true)`, the Gradle process available for debugging, and breakpoints can be set anywhere in the plugin code. 
When the test is run, the breakpoints will be triggered and the state of the plugin 

## Gradle Properties
_Properties_ are configurable values used throughout the agent. At present, these include:

|Property|Use decscription|
|---|---|
|`newrelic.agent.version`|The Semantic version of the agent, i.e., `5.13.0`|
|`newrelic.agent.build`|The build number of the agent. Usually provided by CI, will appear as `SNAPSHOT` when built locally|

## Gradle Tasks

The agent's *Gradle tasks* represent pipeline targets, in other words, *what parts of the agent should be built*.</br>Specify the target task after any properties settings, i.e., ```./gradlew -Pnewrelic.agent.version=6.0.0 {task} {task} ...```

### `assemble`
Build the agent project

### `test`
Build the agent project and run all tests

### `build`
Build and test the agent project

### `publish`
Build and install artifacts to the in-project Maven local repository (${rootProject.buildDir}/.m2/repository)

### `coverageReport`
Generates HTML reports for Jacoco coverage data. Currently produces reports for the Agent and Agent-Core module. Depends on `publish` task

### `staticAnalysis`
Generates HTML reports for static analysis tools run against agent code.

### `copyLegacyArtifacts`
Generates legacy artifacts to root project's `./dist` folder. All current Jenkins jobs assume this artifact location.

### `uploadArchives`
Generates distribution artifacts and deploys them to Sonatype and BinTray

# Publishing
## Deploy to Sonatype

# Agent Configuration `[Incubating]`
The Gradle plugin can be configured to selectively disable certain build variants. The notation is changing, but at the moment looks like this:
```
  newrelic {
        // globally enable/disable class instrumentation
        enabled true (default: true)

        // enable/disable test class insstrumentation (default: true)
        instrumentTests false

        // Disable instrumentation of select build variants (default: none)
        excludeVariant 'FullDebug', 'LiteRelease'
    }
```

# Licensing

Important: Any third-party software used in the Android agent must be reviewed for
licensing issues by the Mobile APM project manager before use. This is true even if the
software is distributed under the same license as other software already approved for use.

