#!/usr/bin/env bash
# changelog-bullets.sh against real excerpts of every agent's CHANGELOG (fixtures/changelogs/).
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
n="$tmp/next.mdx"
C="$FIXTURES/changelogs"
cb() { cp "$SKELETON" "$n"; "$SCRIPTS/changelog-bullets.sh" "$1" "$C/$2.md" "$n"; }

# Cordova: "# 7.1.12" at the same level as "# Changelog", no section heading.
assert_eq "cordova: H1 version, no section heading" "added enhancement: Native Android agent updated to version 7.8.3." "$(cb 7.1.12 cordova)"
assert_eq "cordova: the next H1 version is not included" "added enhancement: Native iOS agent updated to version 7.7.7." "$(cb 7.1.11 cordova)"

# Capacitor: versions and sections both "##"; old headings start with a space.
assert_eq "capacitor: ## version with ## section" "added enhancement: Updated the Native Android agent to version 7.8.3." "$(cb 1.6.13 capacitor)"
out=$(cb 1.5.11 capacitor)
assert_eq "capacitor: ' ## 1.5.11' (leading space) is a version heading" 3 "$(printf '%s\n' "$out" | grep -c '^added')"
assert_not_contains "capacitor: ' ## 1.5.10' ends 1.5.11" "$out" "7.6.8"

# Flutter: "### 1.3.2" versions with "## Enhancements" / "## Bug Fixes" sections above them in level.
assert_eq "flutter: ### version, ## section" "added enhancement: Updated the native Android agent to version 7.8.3." "$(cb 1.3.2 flutter)"
out=$(cb 1.3.0 flutter)
assert_eq "flutter: two bug fixes" 2 "$(printf '%s\n' "$out" | grep -c '^added bug: Fixed')"
assert_eq "flutter: two enhancements after the bug fixes" 2 "$(printf '%s\n' "$out" | grep -c '^added enhancement: Updated the native')"
assert_not_contains "flutter: ### 1.2.11 ends 1.3.0" "$(cat "$n")" "1.2.11"

# Unity: "## 1.7.0" and "## Bug fixes" at the same level; an entry wrapped over several lines.
out=$(cb 1.7.0 unity)
assert_eq "unity: two bug fixes" 2 "$(printf '%s\n' "$out" | grep -c '^added bug:')"
assert_contains "unity: continuation lines join their entry" "$out" "manually disabled in the EDM4U iOS Resolver settings. EDM4U's SPM resolver links package products against the \`UnityFramework\` target without embedding them into the built"
assert_contains "unity: the last continuation line is kept" "$out" "[googlesamples/unity-jar-resolver#779](https://github.com/googlesamples/unity-jar-resolver/issues/779)."
assert_eq "unity: Improvements gives three enhancements, Upgrade notes two more" 5 "$(printf '%s\n' "$out" | grep -c '^added enhancement:')"
assert_eq "unity: each entry is one bullet line" 7 "$(grep -c '^\* ' "$n")"
assert_eq "unity: 1.7.2 has two entries" 2 "$(cb 1.7.2 unity | grep -c '^added enhancement: Native')"

# .NET MAUI: "# 1.3.0" (H1) with "## Improvements" and "## Bug Fixes".
out=$(cb 1.3.0 net-maui)
assert_eq "maui: three enhancements" 3 "$(printf '%s\n' "$out" | grep -c '^added enhancement:')"
assert_eq "maui: one bug fix" 1 "$(printf '%s\n' "$out" | grep -c '^added bug: Fixed a latent double-report')"
assert_contains "maui: UTF-8 text passes through" "$(cat "$n")" "teardown (the fatal exception typically kills the process"

# React Native: "## 1.9.2" with "## Improvements".
assert_eq "react native: ## version with ## section" "added enhancement: Updated the Native Android agent to version 7.8.3." "$(cb 1.9.2 react-native)"

# Keep a Changelog: "## [1.3.2] - 2026-10-05" with ### Added / Fixed / Security; [Unreleased] is not a version.
out=$(cb 1.3.2 keep-a-changelog)
assert_contains "keep-a-changelog: Added maps to enhancement" "$out" "added enhancement: Offline storage for queued events."
assert_contains "keep-a-changelog: Fixed maps to bug" "$out" "added bug: A crash when the app is backgrounded during a harvest."
assert_contains "keep-a-changelog: Security maps to security" "$out" "added security: Upgraded a vulnerable transitive dependency."
assert_not_contains "keep-a-changelog: Unreleased is not taken" "$out" "Not released yet"
assert_not_contains "keep-a-changelog: [1.3.1] ends [1.3.2]" "$out" "Older entry"

# A v-prefixed heading, and 1.9.2 versus 1.9.20 at every level.
printf '%s\n' '# v1.9.20' '- Twenty.' '### v1.9.2 (2026-10-05)' '- Two.' '# 1.9.1' '- One.' > "$tmp/c.md"
cp "$SKELETON" "$n"; out=$("$SCRIPTS/changelog-bullets.sh" 1.9.2 "$tmp/c.md" "$n")
assert_eq "a v-prefixed, dated H3 heading matches and 1.9.20 does not" "added enhancement: Two." "$out"
cp "$SKELETON" "$n"; out=$("$SCRIPTS/changelog-bullets.sh" 1.9.20 "$tmp/c.md" "$n")
assert_eq "1.9.20 ends at the next version of any level" "added enhancement: Twenty." "$out"
printf '%s\n' '## 1.9.2-beta.1' '- Beta.' '## 1.9.2' '- Final.' > "$tmp/c.md"
cp "$SKELETON" "$n"; assert_eq "a pre-release heading is not the release" "added enhancement: Final." "$("$SCRIPTS/changelog-bullets.sh" 1.9.2 "$tmp/c.md" "$n")"
printf '%s\n' '## 1.9.2' '-  Spaced.' '+ Plus item.' '' '  Not a continuation after a blank line.' '#No space is not a heading' > "$tmp/c.md"
cp "$SKELETON" "$n"; out=$("$SCRIPTS/changelog-bullets.sh" 1.9.2 "$tmp/c.md" "$n")
assert_contains "extra spaces after the marker are dropped" "$out" "added enhancement: Spaced."
assert_contains "+ items are entries" "$out" "added enhancement: Plus item."
assert_not_contains "an indented paragraph after a blank line is not joined" "$out" "Not a continuation"

finish
