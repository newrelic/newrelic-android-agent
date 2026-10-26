# Changelog

# 1.3.2

## Improvements

- Updated the Native Android agent to version 7.8.3.

# 1.3.1

## Improvements

- Updated the Native Android agent to version 7.8.2.
- Updated the Native iOS agent to version 7.7.7.


# 1.3.0

## Improvements

- Symbol packages (`.snupkg`) are now published alongside the NuGet packages for `NewRelic.MAUI.Plugin`, `NewRelic.MAUI.iOS.Binding`, and `NewRelic.MAUI.Android.Binding`. Each contains the portable PDBs for every target framework the package ships, so stack frames that resolve into plugin or binding code can be symbolicated.
- Native Android agent updated to version 7.8.0.
- Native iOS agent updated to version 7.7.5.

## Bug Fixes

- Fixed a latent double-report path for Android unhandled exceptions. `Android.Runtime.AndroidEnvironment.UnhandledExceptionRaiser` and `AppDomain.CurrentDomain.UnhandledException` both fire for the same unhandled exception, so the plugin's shared event invoked `RecordException` twice. In practice this was usually masked by a race at process teardown (the fatal exception typically kills the process before the second call can persist, so only one `MobileHandledException` was usually recorded) — but that's not guaranteed, so the plugin now dedupes explicitly instead of relying on timing.

# 1.2.4

