#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

# Every agent's sample config is valid, and yields what the actions expect.
for a in android react-native cordova capacitor flutter unity net-maui; do
  out=$(cd "$AGENTS/$a" && "$SCRIPTS/config.sh" release-notes.json 2>&1); rc=$?
  assert_eq "$a: sample config is valid" 0 "$rc"
  assert_contains "$a: docs_dir is under the mobile release notes" "$out" "docs_dir=src/content/docs/release-notes/mobile-release-notes/"
done
rn=$(cd "$AGENTS/react-native" && "$SCRIPTS/config.sh" release-notes.json)
assert_contains "release branch prefix drops -next" "$rn" "release_branch_prefix=react-native_release_notes_"
assert_contains "skeleton_name is the file name" "$rn" "skeleton_name=react-native-agent-next.mdx"
assert_contains "docs base is develop" "$rn" "docs_base=develop"
assert_contains "fallback text defaults to Updated version." "$rn" "fallback_text=Updated version."
android=$(cd "$AGENTS/android" && "$SCRIPTS/config.sh" release-notes.json)
assert_contains "an empty tagPrefix is allowed" "$android" "
tag_prefix=
"
assert_contains "no changelog field means changelog is empty" "$android" "
changelog=
"
unity=$(cd "$AGENTS/unity" && "$SCRIPTS/config.sh" release-notes.json)
assert_contains "fallbackText overrides the default" "$unity" "fallback_text=Updated native agent dependencies."
assert_eq "every output line is name=value" 0 "$(printf '%s\n' "$rn" | grep -cvE '^[a-z_]+=' || true)"

# ---- each rule fails with a message naming the field ----
cp "$SKELETON" "$tmp/react-native-agent-next.mdx"
base=$(cat "$AGENTS/react-native/release-notes.json")
try() { # name jq-edit expected-message
  printf '%s' "$base" | jq "$2" > "$tmp/c.json"
  out=$(cd "$tmp" && "$SCRIPTS/config.sh" c.json 2>&1); rc=$?
  assert_eq "$1: exits 2" 2 "$rc"
  assert_contains "$1: says why" "$out" "$3"
}
try "missing subject" 'del(.subject)' 'missing required field "subject"'
try "missing tagPrefix" 'del(.tagPrefix)' 'missing required field "tagPrefix"'
try "unknown field" '.downloadlink = "x"' 'unknown field "downloadlink"'
try "non-string value" '.tagPrefix = 1' '"tagPrefix" must be a single-line string'
try "newline in a value" '.subject = "React\nNative agent"' '"subject" must be a single-line string'
try "filePrefix with a slash" '.filePrefix = "../x-"' '"filePrefix" may only hold'
try "filePrefix with a space" '.filePrefix = "x agent-"' '"filePrefix" may only hold'
try "docsFolder with a dot" '.docsFolder = "x.notes"' '"docsFolder" may only hold'
try "nextBranch not ending in -next" '.nextBranch = "react-native"' '"nextBranch" must end in -next'
try "nextBranch that is only -next" '.nextBranch = "-next"' '"nextBranch" must end in -next'
try "nextBranch with a slash" '.nextBranch = "a/b-next"' '"nextBranch" may only hold'
try "tagPrefix with a digit" '.tagPrefix = "v1"' '"tagPrefix" may only hold letters'
try "tagPrefix with a dash" '.tagPrefix = "release-"' '"tagPrefix" may only hold letters'
try "subject with a colon" '.subject = "React: agent"' '"subject" may only hold'
try "http downloadLink" '.downloadLink = "http://example.com"' '"downloadLink" must start with https://'
try "empty downloadLink" '.downloadLink = ""' '"downloadLink" must start with https://'
try "unknown placeholder" '.downloadLink = "https://x/{Version}"' 'only use the placeholders {version}, {tag} and {npmPackage}'
try "unbalanced brace" '.downloadLink = "https://x/{version"' 'only use the placeholders'
try "quote in downloadLink" '.downloadLink = "https://x/it'"'"'s"' '"downloadLink" must not contain'
try "absolute skeleton path" '.skeleton = "/etc/x.mdx"' '"skeleton" must be a relative path'
try "skeleton escaping the repo" '.skeleton = "../x.mdx"' '"skeleton" must be a relative path'
try "skeleton that is not .mdx" '.skeleton = "x.md"' '"skeleton" must be an .mdx file'
try "changelog escaping the repo" '.changelog = "docs/../../CHANGELOG.md"' '"changelog" must be a relative path'
try "empty changelog" '.changelog = ""' '"changelog" must not be empty'
try "blank fallbackText" '.fallbackText = "  "' '"fallbackText" must not be blank'
try "missing skeleton file" '.skeleton = "nope.mdx"' 'skeleton nope.mdx does not exist'
try "skeleton subject differs" '.subject = "Flutter agent"' 'has "subject: React Native agent"; the config says "Flutter agent"'

printf '%s' "$base" | jq '.subject = 1 | .filePrefix = 2' > "$tmp/c.json"
out=$(cd "$tmp" && "$SCRIPTS/config.sh" c.json 2>&1)
assert_contains "every problem is reported, not just the first (1)" "$out" '"subject" must be'
assert_contains "every problem is reported, not just the first (2)" "$out" '"filePrefix" must be'

printf '%s' "$base" > "$tmp/c.json"
for m in '{/* release-note:feature */}' '{/* release-note:security */}' '{/* release-note:end */}'; do
  grep -vxF "$m" "$SKELETON" > "$tmp/react-native-agent-next.mdx"
  out=$(cd "$tmp" && "$SCRIPTS/config.sh" c.json 2>&1); rc=$?
  assert_eq "skeleton without $m exits 2" 2 "$rc"
  assert_contains "skeleton without $m is named" "$out" "is missing $m"
done
sed 's/$/\r/' "$SKELETON" > "$tmp/react-native-agent-next.mdx"
(cd "$tmp" && "$SCRIPTS/config.sh" c.json > /dev/null 2>&1); assert_eq "a CRLF skeleton is accepted" 0 "$?"

printf '[1]' > "$tmp/c.json"; (cd "$tmp" && "$SCRIPTS/config.sh" c.json > /dev/null 2>&1); assert_eq "a JSON array exits 2" 2 "$?"
printf '{' > "$tmp/c.json"; (cd "$tmp" && "$SCRIPTS/config.sh" c.json > /dev/null 2>&1); assert_eq "invalid JSON exits 2" 2 "$?"
(cd "$tmp" && "$SCRIPTS/config.sh" missing.json > /dev/null 2>&1); assert_eq "a missing config exits 2" 2 "$?"
(cd "$tmp" && "$SCRIPTS/config.sh" > /dev/null 2>&1); assert_eq "no argument exits 2" 2 "$?"


# ---- the optional booleans: prLinks, labelledOnly, readyForReview ----
assert_contains "prLinks defaults to true" "$rn" "pr_links=true"
assert_contains "labelledOnly defaults to false" "$rn" "labelled_only=false"
assert_contains "readyForReview defaults to false" "$rn" "ready_for_review=false"
printf '%s' "$base" | jq '.prLinks = false | .labelledOnly = true | .readyForReview = true' > "$tmp/b.json"
out=$(cd "$tmp" && "$SCRIPTS/config.sh" b.json 2>&1); rc=$?
assert_eq "the three booleans set: exits 0" 0 "$rc"
assert_contains "prLinks false is printed" "$out" "pr_links=false"
assert_contains "labelledOnly true is printed" "$out" "labelled_only=true"
assert_contains "readyForReview true is printed" "$out" "ready_for_review=true"
try "prLinks as a string" '.prLinks = "no"' '"prLinks" must be true or false'
try "labelledOnly as a number" '.labelledOnly = 1' '"labelledOnly" must be true or false'
try "readyForReview as null" '.readyForReview = null' '"readyForReview" must be true or false'
try "a string field given a boolean" '.tagPrefix = true' '"tagPrefix" must be a single-line string'

finish
