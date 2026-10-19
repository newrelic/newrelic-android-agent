#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
u=https://github.com/newrelic/newrelic-react-native-agent/pull
n="$tmp/next.mdx"; c="$tmp/CHANGELOG.md"; o="$tmp/out.mdx"
cb() { "$SCRIPTS/changelog-bullets.sh" "$1" "$c" "$n"; }
section() { sed -n "/release-note:$1 /,/release-note:$2 /p" "$n"; }

cat > "$c" <<'CL'
# Changelog
## 1.9.3

> ### Important
> - Callout bullets are not entries.

## Improvements

- Updated the Native Android agent to version 7.8.4.
- Only in the changelog.
  - nested entry

### Fixed in this release
* Fixed a crash on `<View>` unmount

## Security
- Patched a dependency

## 1.9.20

- Belongs to another version.

## 1.9.2

- Older entry.
CL

# ---- reconciliation (NR-620061 acceptance criteria) ----
cp "$SKELETON" "$n"
"$SCRIPTS/append-bullet.sh" "$n" enhancement "* Updated the native android agent to version 7.8.4 ([#330]($u/330)) {/* short: x */}"
"$SCRIPTS/append-bullet.sh" "$n" feature "* Only on the next branch. ([#331]($u/331)) {/* short: Only on the next branch */}"
out=$(cb 1.9.3)
assert_contains "an entry also on the next branch is reported as a duplicate" "$out" "duplicate: Updated the Native Android agent to version 7.8.4."
assert_eq "an entry in both sources appears once" 1 "$(grep -ci 'native android agent to version 7.8.4' "$n")"
assert_contains "a changelog-only entry survives" "$(section enhancement bug)" "* Only in the changelog. {/* short: Only in the changelog */}"
assert_contains "a next-branch-only bullet survives" "$(section feature enhancement)" "Only on the next branch."
assert_contains "nested list items are entries too" "$(section enhancement bug)" "* Nested entry."

# ---- sections from headings ----
assert_contains "Improvements maps to enhancements" "$out" "added enhancement: Only in the changelog."
assert_contains "a 'Fixed' heading maps to bug fixes" "$(section bug security)" "Fixed a crash on"
assert_contains "a security heading maps to security" "$(section security end)" "* Patched a dependency."
assert_not_contains "blockquoted callouts are skipped" "$(cat "$n")" "Callout bullets"
assert_not_contains "1.9.20 is not part of 1.9.2's or 1.9.3's entries" "$(cat "$n")" "another version"
assert_not_contains "entries of the next version down are not taken" "$(cat "$n")" "Older entry"
assert_contains "text is MDX-escaped outside code spans" "$(section bug security)" "* Fixed a crash on \`<View>\` unmount."

cp "$SKELETON" "$n"
out=$(cb 1.9.2)
assert_eq "## 1.9.20 does not match version 1.9.2" "added enhancement: Older entry." "$out"

cp "$SKELETON" "$n"
printf '%s\n' '# Changelog' '## 2.0.0' '- No heading means enhancement' '## 1.0.0' > "$c"
cb 2.0.0 > /dev/null
assert_contains "an entry with no heading is an enhancement" "$(section enhancement bug)" "* No heading means enhancement."

# The auto-bump in release-testing.yml writes this line; it is not a release note.
cp "$SKELETON" "$n"
printf '%s\n' '## 2.0.1 (2026-10-20)' '' '- Auto-release from commit: fix: x' '' > "$c"
assert_eq "auto-release lines are skipped and a dated heading still matches" "" "$(cb 2.0.1)"
printf '%s\n' '## 2.0.1 (2026-10-20)' '- Real entry' > "$c"
assert_eq "a dated version heading matches" "added enhancement: Real entry." "$(cb 2.0.1)"

cp "$SKELETON" "$n"; before=$(cat "$n")
printf '%s\n' '## 1.0.0' '- x' > "$c"
assert_eq "a version with no entries prints nothing" "" "$(cb 3.0.0)"
assert_eq "a version with no entries leaves the file alone" "$before" "$(cat "$n")"

printf '%s\n' '## 1.0.0' '- Same thing.' '- same   thing' > "$c"
cb 1.0.0 > /dev/null
assert_eq "duplicates within CHANGELOG.md collapse too" 1 "$(grep -ci 'same' "$n")"

# The generator turns the reconciled file into all four arrays.
cp "$SKELETON" "$n"
"$SCRIPTS/append-bullet.sh" "$n" feature "* Added a thing. ([#5]($u/5)) {/* short: Added a thing */}"
printf '%s\n' '## 1.9.3' '## Improvements' '- Updated the Native iOS agent to version 7.7.8.' > "$c"
cb 1.9.3 > /dev/null
"$SCRIPTS/generate-docs.sh" --next "$n" --version 1.9.3 --date 2026-10-20 --out "$o" 2> /dev/null
assert_contains "changelog entries populate the enhancements array" "$(cat "$o")" "enhancements:
  - 'Updated the Native iOS agent to version 7.7.8'"
assert_contains "PR bullets populate the features array" "$(cat "$o")" "features:
  - 'Added a thing'"
assert_contains "empty arrays are still emitted" "$(cat "$o")" "bugs: []"
assert_not_contains "empty sections are deleted" "$(cat "$o")" "## Bug fixes"

# A native agent PR updating both agents writes both CHANGELOG.md sentences into one bullet
# (update_native_agent_version.yml); neither sentence is added again.
cp "$SKELETON" "$n"
"$SCRIPTS/append-bullet.sh" "$n" enhancement "* Updated the Native Android agent to version 7.8.4. Updated the Native iOS agent to version 7.7.8. ([#332]($u/332)) {/* short: x */}"
printf '%s\n' '## 1.9.4' '## Improvements' '- Updated the Native Android agent to version 7.8.4.' '- Updated the Native iOS agent to version 7.7.8.' > "$c"
cb 1.9.4 > /dev/null
assert_eq "both native agent sentences dedupe against the combined PR bullet" 1 "$(grep -c '^\* ' "$n")"

# ---- bad arguments ----
"$SCRIPTS/changelog-bullets.sh" 1.9 "$c" "$n" > /dev/null 2>&1; assert_eq "a bad version exits 2" 2 "$?"
"$SCRIPTS/changelog-bullets.sh" 1.9.3 "$tmp/missing.md" "$n" > /dev/null 2>&1; assert_eq "a missing changelog exits 2" 2 "$?"
"$SCRIPTS/changelog-bullets.sh" 1.9.3 "$c" "$tmp/missing.mdx" > /dev/null 2>&1; assert_eq "a missing next file exits 2" 2 "$?"

# append-bullet.sh --no-link
cp "$SKELETON" "$n"
"$SCRIPTS/append-bullet.sh" "$n" bug "* No link." > /dev/null 2>&1; assert_eq "without --no-link a link-less bullet is refused" 2 "$?"
"$SCRIPTS/append-bullet.sh" --no-link "$n" bug "* No link."; assert_eq "--no-link accepts a link-less bullet" 0 "$?"
"$SCRIPTS/append-bullet.sh" --no-link "$n" bug "No star." > /dev/null 2>&1; assert_eq "--no-link still requires '* '" 2 "$?"

finish
