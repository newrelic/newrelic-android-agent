## 1.7.2

## Improvements
- Native Android agent updated to version 7.8.3
- Native iOS agent updated to version 7.7.7


## 1.7.1

## Improvements
- Native iOS agent updated to version 7.7.7


## 1.7.0

## Bug fixes
- Fixed iOS apps crashing on launch with `Library not loaded: @rpath/NewRelic.framework/NewRelic`. The Swift Package Manager declaration added in 1.6.0 has been removed and iOS dependencies now resolve via CocoaPods only. Builds on 1.6.0 through 1.6.4 were affected unless Swift Package Manager was manually disabled in the EDM4U iOS Resolver settings.
  EDM4U's SPM resolver links package products against the `UnityFramework` target without embedding
  them into the built `.app`, so the dynamic `NewRelic.xcframework` was never present at runtime.
  Because EDM4U enables SPM by default, this was the default iOS path on those versions. See
  [#82](https://github.com/newrelic/newrelic-unity-agent/issues/82) and the upstream report
  [googlesamples/unity-jar-resolver#779](https://github.com/googlesamples/unity-jar-resolver/issues/779).
- Fixed the New Relic iOS agent being missing entirely from builds on Unity 2019.1 through 2021.2, where EDM4U suppressed the `NewRelicAgent` pod but skipped Swift Package Manager injection because it requires Unity 2021.3 or newer.

## Improvements
- Native Android agent updated to version 7.8.2
- Native Android NDK agent updated to version 1.1.5
- Native iOS agent updated to version 7.7.6

## Upgrade notes
- iOS dependencies now resolve through CocoaPods only. No action is needed if you previously used Swift Package Manager, as EDM4U falls back to the `NewRelicAgent` pod automatically and the **Swift Package Manager Enabled** setting can be left checked.
- If a generated Xcode project still carries a `newrelic-ios-agent-spm` package reference from an earlier build, delete the generated project and rebuild from Unity to clear it.


## 1.6.4
