#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
f="$tmp/next.mdx"
fresh() { cp "$REPO_ROOT/android-agent-next.mdx" "$f"; }
url=https://github.com/newrelic/newrelic-android-agent/pull
b1="* Fixed crash A. ([#1]($url/1)) {/* short: Fixed crash A */}"
b2="* Fixed crash B. ([#2]($url/2)) {/* short: Fixed crash B */}"
f1="* Added thing. ([#3]($url/3)) {/* short: Added thing */}"

fresh
"$SCRIPTS/append-bullet.sh" "$f" bug "$b1"
expected=$(printf '%s\n' '{/* release-note:bug */}' '## Bug fixes' '' "$b1" '')
actual=$(sed -n '/release-note:bug/,/release-note:security/p' "$f" | sed '$d')
assert_eq "first bullet goes under heading with blank lines kept" "$expected" "$actual"

"$SCRIPTS/append-bullet.sh" "$f" bug "$b2"
actual=$(sed -n '/release-note:bug/,/release-note:security/p' "$f" | sed '$d')
expected=$(printf '%s\n' '{/* release-note:bug */}' '## Bug fixes' '' "$b1" "$b2" '')
assert_eq "second bullet follows the first" "$expected" "$actual"

"$SCRIPTS/append-bullet.sh" "$f" feature "$f1"
assert_contains "feature bullet lands in feature section" "$(sed -n '/release-note:feature/,/release-note:enhancement/p' "$f")" "Added thing."
assert_not_contains "bug section untouched by feature append" "$(sed -n '/release-note:bug/,/release-note:security/p' "$f")" "Added thing."

before=$(cat "$f")
"$SCRIPTS/append-bullet.sh" "$f" bug "$b1" 2>/dev/null
assert_eq "re-appending the same PR is a no-op" "$before" "$(cat "$f")"

assert_contains "support statement preserved" "$(cat "$f")" "## Support statement"
assert_contains "frontmatter preserved" "$(head -1 "$f")" "---"

fresh
"$SCRIPTS/append-bullet.sh" "$f" bug '* Fixed `a\nb` path C:\temp. ([#9]('"$url"'/9))'
assert_contains "backslashes survive" "$(cat "$f")" 'C:\temp.'

fresh
"$SCRIPTS/append-bullet.sh" "$f" nonsense "$b1" 2>/dev/null; assert_eq "unknown section exits 2" 2 "$?"
printf 'no markers\n' > "$f"
"$SCRIPTS/append-bullet.sh" "$f" bug "$b1" 2>/dev/null; assert_eq "missing marker exits 2" 2 "$?"

finish
