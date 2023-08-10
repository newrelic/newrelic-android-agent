[![Community Plus header](https://github.com/newrelic/opensource-website/raw/main/src/images/categories/Community_Plus.png)](https://opensource.newrelic.com/oss-category/#community-plus)
# New Relic Android Agent 
> The New Relic Android agent provides performance monitoring instrumentation for Android applications. 
> With [New Relic's Android agent](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/get-started/introduction-new-relic-mobile-android), you can track everything from performance issues to tiny errors within your code. Our agent monitors your Android app and provides visibility into its behavior during runtime. 

## Artifacts
Agent releases follow [semantic versioning conventions](https://semver.org/). See [agent release notes](https://docs.newrelic.com/docs/release-notes/mobile-release-notes/android-release-notes/) for full details on releases and downloads. Agent artifacts can also be found on [Maven](https://search.maven.org/search?q=com.newrelic.agent.android).

The Android agent is comprised of several modules that are used together to instrument client applications:
  
### Agent ([:agent](https://github.com/newrelic/newrelic-android-agent/tree/main/agent))

The agent module wraps `agent-core` and `instrumentation` modules into an Android-compatible library project. 

The output is `android-agent-<version>.jar`

### Agent Core ([:agent-core](https://github.com/newrelic/newrelic-android-agent/tree/main/agent-core))

The `agent-core` module contains common runtime artifacts collected and embedded into a single JAR:

*   The SDK interface (API)
*   The agent model 
*   Instrumentation and metrics
*   Data collection and reporting
*   Crash detection
*   Events and analytics management 

The output is `agent-core-<version>.jar`

### Instrumentation ([:instrumentation](https://github.com/newrelic/newrelic-android-agent/tree/main/instrumentation))

The instrumentation module performs instrumentation during builds on client code using bytecode rewriting. 

The output is `instrumentation-<version>.jar`

### Gradle plugin ([:plugins:gradle](https://github.com/newrelic/newrelic-android-agent/tree/main/plugins))

The Gradle plugin auto-instruments client code during builds when applied to Android build configurations. 

The output is `agent-gradle-plugin-<version>.jar`


## Git Submodules

Prior to building the agent, update the repo's submodules from the command line
> git submodule update --init --recursive

### NDK agent ([:ndk:agent-ndk](https://github.com/newrelic/newrelic-ndk-agent/tree/main))

The NDK agent submodule captures native crashes resulting from raised signals and uncaught runtime exceptions from C and C++ code. After building, Android native agent artifacts are located in `agent-ndk/build/outputs/aar`

The output is `agent-ndk-<variant>.aar`


# Installation

## Dependencies

Tool dependencies must to be installed and configured for your environment prior to building. The Android agent requires the following tools to build:

| Dependency            | Version        |
|-----------------------|----------------|
| Java                  | JDK 11         |
| Android Gradle Plugin | 7.1            |
| Gradle                | 7.2            |
| minSDK                | 24             |
| compileSDK            | 28             |
| targetSDK             | 28             |
| buildtools            | 28.0.2         |

## Submodules
|Dependency|        Version         |
|----------|:----------------------:|
|NDK| 21.4.7075529 or higher |
|Cmake|    3.18.1 or higher    |


# Setting up your environment

### AndroidStudio setup
* [Download](https://developer.android.com/sdk/index.html) the Android Studio IDE.
* Install SDK Platforms. SDK Manager left pane: Appearances & Behavior -> System Settings -> Android SDK. Right Pane selects the SDK Platforms.
Android Studio must be configured with the Android Native Development Kit (NDK) installed. Update `cmake` in `Tools` -> `SDK Manager` -> `SDK Tools`
* From Android Studio's main menu, select `File` -> `Import Project` -> `Next`. Provide the full path to your cloned agent repo.

The JDK 11 implementation provided by Android Studio is the default JDK used when building from the IDE.

# Getting Started
See [Configure with Gradle and Android Studio](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/install-android-apps-gradle-android-studio) as well as
the Android agent [compatibility and requirements](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/get-started/new-relic-android-compatibility-requirements/) documentation for an overview of what is supported by the Android agent.

## Building

The agent can be built from either the command line Gradle or Android Studio. 

The agent version is contained in `gradle.properties`, but can be overridden by adding `-Pnewrelic.agent.version={version}` to the Gradlew command line.

### Build using Gradle
To build the `android-agent` JAR, run the following command from the project root directory:

```
./gradlew clean assemble
```

To build and run all checks:

```
./gradlew clean build
```

### Build using Android Studio

From Android Studio, each module should be built using the `Release` configuration.

## Testing

The agent uses JUnit, Mockito and Robolectric to mock and test agent functionality.

### `Unit Tests`
```
./gradlew test
```

| Module           | Reports                                                     |
|------------------|-------------------------------------------------------------|
| `agent`          | agent/build/reports/tests/testReleaseUnitTest/index.html         |
| `agent-core`     | agent-core/build/reports/tests/test/index.html            |
| `instrumentation`| instrumentation/build/reports/tests/test/index.html  |

### `Regression Tests`
```
./gradlew :plugins:gradle:check -P regressions
```

| Module           |Reports|
|------------------|---|
| `plugins:gradle` |plugins/gradle/build/reports/tests/test/index.html|


### `Static Analysis`
```
./gradlew lint
```
| |Reports|
|-------|---|
| `Lint` |file://agent/build/reports/lint-results-release.html|


# Debugging the agent

The simplest way to debug the agent is at runtime through its [test app](https://github.com/newrelic/newrelic-android-agent/tree/main/samples/agent-test-app).

* Create a test app that has been configured to use the agent.
* Run a debugging session of the test app, but before you start execution, browse the `External Libraries` dependencies from the AS `Project` pane (all the way at the bottom).
* Drill down to `External Libraries/android-agent-{version}/android-agent-{version}.jar/com/newrelic/agent/android/NewRelic`
* Open the file, and set a break point just inside the start() method. Now debug the app and execution will break inside the agent.

Debugging the class rewriter or plugins is more difficult but you can use a ```Remote``` debugging session and command line Gradle builds.
* In Android Studio, create a `Remote` _Run/Debug configuration_.
* From your command line, export the `GRADLE_OPTS` environment variable:
  > export GRADLE_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5050"
* Initiate a command line build of your test app:
  > ./gradlew clean assembleDebug 
  >
  You should then see `Listening for transport dt_socket at address: 5005`

* Set some breakpoints in the agent or class rewriter, and start the remote debugging session
* You are now live-debugging the Gradle build, specifically those tasks that interact with Agent code
* To remove GRADLE_OPTS from the environment,  
    > unset GRADLE_OPTS

## Debugging with Android Studio
Starting in version 3.0, Android Studio has made it easier to debug client builds using remote debugging. You no longer have
to declare a `GRADLE_OPTS` environment variable. Simply start a command line build with 
> `-Dorg.gradle.debug=true --no-daemon` 

declared on the build command line, or as properties in the `gradle.properties` file.

When set to true, Gradle will run the build with remote debugging enabled, listening on port 5005. Note that this is the equivalent of adding GRADLE_OPTS above.

This will suspend the virtual machine until a debugger is attached. You will no longer see `Listening for transport dt_socket at address: 5005`, but instead now see `> Starting Daemon` when the build waits for a remote debugger to attach. Simply run your _Remote_ configuration (created above) and the debugger will stop at any breakpoints set.

## Debugging the Agent Gradle Plugin
The easiest way to debug the Agent Gradle Plugin is to run a [plugin functional test](plugins/gradle/src/functional/groovy/com/newrelic/agent/android/PluginSmokeSpec.groovy).
When the GradleRunner is created with `withDebug(true)`, the Gradle process is available for debugging and breakpoints can be set anywhere in the plugin code.
When the test is run, the breakpoints will be triggered and the state of the plugin available for inspection.

## Gradle Properties
_Properties_ are configurable values used throughout the agent. At present, these include:

| Property                 | Use decscription                                                                                                                                     |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `newrelic.agent.version` | The Semantic version of the agent, i.e., `7.0.0`                                                                                                     |
| `newrelic.agent.build`   | The build number of the agent. Usually provided by CI, will appear as `SNAPSHOT` when built locally                                                  |

## Gradle Tasks

The agent's *Gradle tasks* represent pipeline targets, in other words, *what parts of the agent should be built*.</br>Specify the target task after any properties settings, i.e., ```./gradlew -Pnewrelic.agent.version=6.10.0 {task} {task} ...```

### `assemble`
Build the agent project

### `check`
Build the agent project and run all tests

### `build`
Build and test the agent project

### `publish`
Build and install artifacts to the in-project Maven local repository (${rootProject.buildDir}/.m2/repository)

# Sonatype Staging
Agent pre-release snapshots will be posted to `https://oss.sonatype.org/content/repositories/comnewrelic-{snapshotId}`

# Usage
The [Agent SDK](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/android-sdk-api/android-sdk-api-guide/) provides information on thw various agent SDK methods available to clients.


# Support

New Relic hosts and moderates an online forum where customers can interact with New Relic employees as well as other customers 
to get help and share best practices. Like all official New Relic open source projects, there's a related Community topic in 
the New Relic Explorers Hub. You can find this project's topic/threads [on the New Relic Explorer's Hub](https://discuss.newrelic.com/tags/android).

### Support Channels 

* [New Relic Documentation](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/get-started/introduction-new-relic-mobile-android): Comprehensive guidance for using our platform
* [New Relic Community](https://discuss.newrelic.com/tags/android): The best place to engage in troubleshooting questions
* [New Relic Developer](https://developer.newrelic.com/): Resources for building a custom observability applications
* [New Relic University](https://learn.newrelic.com/): A range of online training for New Relic users of every level
* [New Relic Technical Support](https://support.newrelic.com/) 24/7/365 ticketed support. Read more about our [Technical Support Offerings](https://docs.newrelic.com/docs/licenses/license-information/general-usage-licenses/support-plan).

## Github Issues
[Github Issues](https://github.com/newrelic/newrelic-android-agent/issues) are not open to the public at the moment. Please contact our support team or one of the support channels to submit your issues, feature requests, etc.


## Privacy
At New Relic, we take your privacy and the security of your information seriously and are committed to protecting your information. We must emphasize the importance of not sharing personal data in public forums, and ask all users to scrub logs and diagnostic information for sensitive information, whether personal, proprietary, or otherwise.

We define “Personal Data” as any information relating to an identified or identifiable individual, including, for example, your name, phone number, post code or zip code, Device ID, IP address and email address.

Please review [New Relic’s General Data Privacy Notice](https://newrelic.com/termsandconditions/privacy) for more information.

## Roadmap
See our [roadmap](roadmap.md), to learn more about our product vision, understand our plans, and provide us valuable feedback.

## Contribute

We encourage your contributions to improve the New Relic Android Agent! Keep in mind that when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project.

If you would like to contribute to this project, review [these guidelines](./CONTRIBUTING.md).

To all contributors, we thank you!  Without your contribution, this project would not be what it is today.
If you have any questions, or to execute our corporate CLA (which is required if your contribution is on behalf of a company), drop us an email at opensource@newrelic.com.

**A note about vulnerabilities**

As noted in our [security policy](https://docs.newrelic.com/docs/licenses/license-information/referenced-policies/security-policy/), New Relic is committed to the privacy and security of our customers and their data. We believe that providing coordinated disclosure by security researchers and engaging with the security community are important means to achieve our security goals.

If you believe you have found a security vulnerability in this project or any of New Relic's products or websites, we welcome and greatly appreciate you reporting it to New Relic through [HackerOne](https://hackerone.com/newrelic).


## License
`newrelic-android-agent` is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.
> The New Relic Android agent also uses source code from third-party libraries. Full details on which libraries are used and the terms under which they are licensed can be found  in the [third-party notices](./THIRD_PARTY_NOTICES.md).
