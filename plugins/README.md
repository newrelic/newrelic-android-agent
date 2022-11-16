New Relic Android Gradle Plugin
===============================

## High-level Overview

When applied to an Android project, the New Relic [Android Gradle Plugin](https://github.com/newrelic/android-gradle-plugin) plugin automatically instruments classes 
found on the app's compile classpath. 

In addition, the plugin manages uploads of obfuscation and native symbol maps for use when rendering mobile crash and handled
exception stack traces, including:
* ProGuard or DexGuard [obfuscation map files](https://developer.android.com/studio/build/shrink-code#enable)
* [NDK symbol files](https://docs.bugsnag.com/api/ndk-symbol-mapping-upload/). See [our docs](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/android-agent-native-crash-reporting/) for more information.


In cases where obfuscation maps or native symbol files  must be manually uploaded, follow the [instructions here](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/android-agent-crash-reporting#manual-proguard).


## Getting started

Integrate the Android Gradle Plugin into an existing app:

* [Complete these instructions](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/install-android-apps-gradle-android-studio#configuration) to use Gradle and Android Studio
* Customize your plugin integration by review ing the [configuration options](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/configure-new-relic-gradle-plugin).

## Support

* [Getting started with New Relic mobile monitoring](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/install-android-apps-gradle-android-studio)
* [Plugin configuration](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/configure-new-relic-gradle-plugin)  
* [Working with Proguard](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/configure-proguard-or-dexguard-android-apps)

## Contributing

We encourage your contributions to improve the New Relic Android Agent! Keep in mind that when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project.

If you would like to contribute to this project, review [these guidelines](../CONTRIBUTING.md).


## License
`android-gradle-plugin` is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.
> The New Relic Android agent also uses source code from third-party libraries. Full details on which libraries are used and the terms under which they are licensed can be found  in the [third-party notices](../THIRD_PARTY_NOTICES.md).
