#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
f="$tmp/next.mdx"
fresh() { cp "$REPO_ROOT/android-agent-next.mdx" "$f"; }
url=https://github.com/newrelic/newrelic-android-agent/pull
b1="* Fixed crash A. ([#1]($url/1)) {/* short: Fixed crash A */}"
b2="* Fixed crash B. ([#2]($url/2)) {/* short: Fixed crash B */}"
f1="* Added thing. ([#3]($url/3)) {/* short: Added thing */}"
# Command substitution strips trailing newlines, so compare through a sentinel to keep them.
snap() { printf '%s\nEND' "$(cat "$1")"; }
section_of() { # file marker-key next-marker-key -> section text including its trailing blank line
  sed -n "/release-note:$2/,/release-note:$3/p" "$1" | sed '$d'
  echo x
}

fresh
"$SCRIPTS/append-bullet.sh" "$f" bug "$b1"
expected=$(printf '%s\n' '{/* release-note:bug */}' '## Bug fixes' '' "$b1" ''; echo x)
assert_eq "first bullet goes under the heading, blank lines before and after kept" "$expected" "$(section_of "$f" bug security)"

"$SCRIPTS/append-bullet.sh" "$f" bug "$b2"
expected=$(printf '%s\n' '{/* release-note:bug */}' '## Bug fixes' '' "$b1" "$b2" ''; echo x)
assert_eq "second bullet follows the first, trailing blank line kept" "$expected" "$(section_of "$f" bug security)"

"$SCRIPTS/append-bullet.sh" "$f" feature "$f1"
assert_contains "feature bullet lands in feature section" "$(sed -n '/release-note:feature/,/release-note:enhancement/p' "$f")" "Added thing."
assert_not_contains "bug section untouched by feature append" "$(sed -n '/release-note:bug/,/release-note:security/p' "$f")" "Added thing."

before=$(snap "$f")
"$SCRIPTS/append-bullet.sh" "$f" bug "$b1" 2>/dev/null
assert_eq "re-appending the same PR is a no-op" "$before" "$(snap "$f")"

# Appending changes exactly one line: everything else (frontmatter, markers, Support statement) is intact.
fresh; cp "$f" "$tmp/orig.mdx"
"$SCRIPTS/append-bullet.sh" "$f" enhancement "$f1"
added=$(diff "$tmp/orig.mdx" "$f" | grep -c '^> ' || true)
removed=$(diff "$tmp/orig.mdx" "$f" | grep -c '^< ' || true)
assert_eq "an append adds exactly one bullet line and one blank line" "2 0" "$added $removed"
assert_contains "support statement preserved" "$(cat "$f")" "## Support statement"
assert_eq "frontmatter preserved" "$(sed -n '1,10p' "$tmp/orig.mdx")" "$(sed -n '1,10p' "$f")"

# The last section sits right before the end marker.
fresh
"$SCRIPTS/append-bullet.sh" "$f" security "$b2"
expected=$(printf '%s\n' '{/* release-note:security */}' '## Security updates' '' "$b2" '' '{/* release-note:end */}'; echo x)
assert_eq "last section: bullet lands before the end marker" "$expected" "$(sed -n '/release-note:security/,/release-note:end/p' "$f"; echo x)"

fresh
"$SCRIPTS/append-bullet.sh" "$f" bug '* Fixed `a\nb` path C:\temp. ([#9]('"$url"'/9))'
assert_contains "backslashes survive" "$(cat "$f")" 'C:\temp.'

# Hand-written text in a section gets a blank line before the bullet.
fresh
awk '{print} /^## Bug fixes$/ {print ""; print "Some intro text."}' "$REPO_ROOT/android-agent-next.mdx" > "$f"
"$SCRIPTS/append-bullet.sh" "$f" bug "$b1"
assert_contains "a bullet after a paragraph is separated by a blank line" "$(cat "$f")" "$(printf 'Some intro text.\n\n%s' "$b1")"

# Refusals leave the file untouched and exit 2.
fresh; before=$(snap "$f")
"$SCRIPTS/append-bullet.sh" "$f" nonsense "$b1" 2>/dev/null; assert_eq "unknown section exits 2" 2 "$?"
"$SCRIPTS/append-bullet.sh" "$f" bug "$(printf '* X ([#6](%s/6))\n{/* release-note:end */}\n## Evil' "$url")" 2>/dev/null; assert_eq "embedded newline is refused" 2 "$?"
"$SCRIPTS/append-bullet.sh" "$f" bug "$(printf '* X ([#6](%s/6))\r' "$url")" 2>/dev/null; assert_eq "carriage return is refused" 2 "$?"
"$SCRIPTS/append-bullet.sh" "$f" bug '{/* release-note:feature */}' 2>/dev/null; assert_eq "a marker line is refused" 2 "$?"
"$SCRIPTS/append-bullet.sh" "$f" bug '* No link here' 2>/dev/null; assert_eq "a bullet without a PR link is refused" 2 "$?"
"$SCRIPTS/append-bullet.sh" "$tmp/missing.mdx" bug "$b1" 2>/dev/null; assert_eq "missing file exits 2" 2 "$?"
assert_eq "refused appends leave the file untouched" "$before" "$(snap "$f")"

printf 'no markers\n' > "$f"
"$SCRIPTS/append-bullet.sh" "$f" bug "$b1" 2>/dev/null; assert_eq "missing section marker exits 2" 2 "$?"
fresh; grep -vxF '{/* release-note:end */}' "$REPO_ROOT/android-agent-next.mdx" > "$f"; before=$(snap "$f")
"$SCRIPTS/append-bullet.sh" "$f" security "$b1" 2>/dev/null; assert_eq "missing end marker exits 2" 2 "$?"
assert_eq "missing end marker leaves the file untouched" "$before" "$(snap "$f")"

# No temp file is left next to the target.
fresh
"$SCRIPTS/append-bullet.sh" "$f" bug "$b1"
stray=""
for x in "$tmp"/*; do
  case "$x" in "$tmp/next.mdx" | "$tmp/orig.mdx") ;; *) stray="$stray $x" ;; esac
done
assert_eq "no stray temp files beside the target" "" "$stray"

finish
