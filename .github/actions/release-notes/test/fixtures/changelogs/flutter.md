### 1.3.2

## Enhancements

- Updated the native Android agent to version 7.8.3.

### 1.3.1

## Enhancements

- Updated the native iOS agent to version 7.7.7.

### 1.3.0

## Bug Fixes

- Fixed an issue where a tracked HTTP header sent with more than one value would fail the request being instrumented. Multi-valued headers are now recorded as a single comma-separated value, and tracked header entries that cannot be read are skipped instead of throwing.
- Fixed the `Flutter Agent Version` attribute reporting a stale version, as the release process no longer updated the Dart agent version constant.

## Enhancements

- Updated the native Android agent to version 7.8.2.
- Updated the native iOS agent to version 7.7.6.

### 1.2.11
