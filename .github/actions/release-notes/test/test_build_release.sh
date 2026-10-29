#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
export GITHUB_REPOSITORY=newrelic/newrelic-react-native-agent RELEASE_NOTES_RETRY_DELAY=0
. "$TEST_DIR/fake-gh.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp" "$FAKE_GH"' EXIT
u=https://github.com/$GITHUB_REPOSITORY/pull

# The next branch already holds bullets for #10 and #11, plus #99, merged after the tag.
next="$tmp/next.mdx"; cp "$SKELETON" "$next"
"$SCRIPTS/append-bullet.sh" "$next" feature "* Added offline storage. ([#10]($u/10)) {/* short: Added offline storage */}"
"$SCRIPTS/append-bullet.sh" "$next" bug "* Fixed a crash on startup. ([#11]($u/11)) {/* short: Fixed a crash on startup */}"
"$SCRIPTS/append-bullet.sh" "$next" bug "* Fixed a later thing. ([#99]($u/99)) {/* short: Fixed a later thing */}"
before=$(cat "$next")
# #12 is a fork PR (no per-PR run), #13 an unlabelled chore, #14 cannot be read.
fake_notes 10 11 12 13 14
fake_pr 12 'fix: keep the session id across restarts' '' NR-12 '["release-note:bug"]' true
fake_pr 13 'chore: bump the build image' '' NR-13 '[]'
printf '%s\n' '## 1.9.3' '' '## Improvements' '' '- Updated the Native Android agent to version 7.8.4.' '- Fixed a crash on startup.' > "$tmp/CHANGELOG.md"
printf '%s\n' '## Kept tail' > "$tmp/tail.mdx"

work="$tmp/work"; mkdir "$work"; out="$tmp/react-native-agent-193.mdx"
build() {
  "$SCRIPTS/build-release.sh" --subject "React Native agent" --version 1.9.3 --tag v1.9.3 --prev-tag v1.9.2 \
    --date 2026-10-20 --next "$next" --out "$out" --work "$work" "$@"
}
log=$(build --changelog "$tmp/CHANGELOG.md" --tail-file "$tmp/tail.mdx" \
  --download-link https://www.npmjs.com/package/newrelic-react-native-agent/v/1.9.3); rc=$?
note=$(cat "$out"); body=$(cat "$work/pr-body.md")
assert_eq "a release builds" 0 "$rc"
assert_contains "asks for the PRs between the two tags" "$(cat "$FAKE_GH/calls.log")" "tag_name=v1.9.3 -f previous_tag_name=v1.9.2"
assert_contains "a next-branch bullet is kept" "$note" "- Added offline storage. ([#10]($u/10))"
assert_contains "a fork PR missing from the next branch gets a bullet" "$note" "- Kept the session id across restarts. ([#12]($u/12))"
assert_not_contains "a PR merged after the tag is left for the next release" "$note" "#99"
assert_contains "a CHANGELOG-only entry is added" "$note" "- Updated the Native Android agent to version 7.8.4."
assert_eq "an entry in the CHANGELOG and on the next branch appears once" 1 "$(grep -c '^- Fixed a crash on startup' "$out")"
assert_contains "the subject is the agent's" "$note" "subject: React Native agent"
assert_contains "the download link is written" "$note" "downloadLink: 'https://www.npmjs.com/package/newrelic-react-native-agent/v/1.9.3'"
assert_contains "the tail ends the note" "$note" "
## Kept tail"
assert_eq "--next is not modified" "$before" "$(cat "$next")"
assert_eq "release-prs.txt lists the release's PRs" "10 11 12 13 14" "$(tr '\n' ' ' < "$work/release-prs.txt" | sed 's/ $//')"
assert_contains "the PR text names the subject and version" "$body" "Release notes for **React Native agent 1.9.3**"
assert_contains "the PR text names both tags" "$body" "between \`v1.9.2\` and \`v1.9.3\`"
assert_contains "a left-out PR is listed, qualified with the repo" "$body" "- $GITHUB_REPOSITORY#13: internal change"
assert_contains "a PR that could not be read is listed as missing" "$body" "- $GITHUB_REPOSITORY#14"
assert_contains "CHANGELOG additions are listed" "$body" "- (enhancement) Updated the Native Android agent to version 7.8.4."
assert_not_contains "no ticket numbers in the PR text" "$body" "NR-"
assert_contains "the log warns about the missing bullet" "$log" "::warning::Could not build a bullet for PR #14"

# --pr-links false changes the note only: the PR text and the pruned PR list stay the same.
cp "$work/pr-body.md" "$tmp/body-with-links.md"; cp "$work/release-prs.txt" "$tmp/prs-with-links.txt"
build --changelog "$tmp/CHANGELOG.md" --tail-file "$tmp/tail.mdx" \
  --download-link https://www.npmjs.com/package/newrelic-react-native-agent/v/1.9.3 --pr-links false > /dev/null; rc=$?
assert_eq "--pr-links false: a release builds" 0 "$rc"
assert_not_contains "--pr-links false: no PR link in the note" "$(cat "$out")" "/pull/"
assert_contains "--pr-links false: the bullets stay" "$(cat "$out")" "- Added offline storage.
"
assert_eq "--pr-links false: the PR text is unchanged" "$(cat "$tmp/body-with-links.md")" "$(cat "$work/pr-body.md")"
assert_eq "--pr-links false: release-prs.txt is unchanged" "$(cat "$tmp/prs-with-links.txt")" "$(cat "$work/release-prs.txt")"

# LABELLED_ONLY=true: an unlabelled PR is left out and listed in the PR text; a labelled one is built.
LABELLED_ONLY=true build --tail-file "$tmp/tail.mdx" > /dev/null; rc=$?
assert_eq "labelledOnly: a release builds" 0 "$rc"
assert_contains "labelledOnly: the unlabelled PR is listed as left out" "$(cat "$work/pr-body.md")" "- $GITHUB_REPOSITORY#13: no release-note label"
assert_contains "labelledOnly: a labelled fork PR still gets a bullet" "$(cat "$out")" "- Kept the session id across restarts."

# Android: no CHANGELOG, no download-link placeholder, the Support statement tail.
cp "$SKELETON" "$next"; rm -f "$out"
fake_notes 12
build --tail-file "$tmp/tail.mdx" > /dev/null
assert_not_contains "without --changelog the PR text does not mention it" "$(cat "$work/pr-body.md")" "CHANGELOG"
assert_not_contains "without --download-link there is no downloadLink" "$(cat "$out")" "downloadLink"

# Nothing at all: the fallback bullet, and the PR text says so.
fake_notes 13
log=$(build --fallback-text "Updated native agent dependencies."); rc=$?
assert_eq "a release with no bullets still builds" 0 "$rc"
assert_contains "the fallback bullet is used" "$(cat "$out")" "- Updated native agent dependencies."
assert_contains "the fallback text is in the enhancements array" "$(cat "$out")" "enhancements:
  - 'Updated native agent dependencies'"
assert_contains "the PR text says to replace the fallback" "$(cat "$work/pr-body.md")" "### No bullets"
assert_contains "the log warns about the fallback" "$log" "::warning::No release-note bullets"
build > /dev/null; assert_contains "the default fallback is Updated version." "$(cat "$out")" "- Updated version."

# No PRs: an error without a CHANGELOG (Android), a warning with one.
fake_notes
build > /dev/null 2>&1; assert_eq "no PRs and no CHANGELOG exits 1" 1 "$?"
log=$(build --changelog "$tmp/CHANGELOG.md"); rc=$?
assert_eq "no PRs with a CHANGELOG builds" 0 "$rc"
assert_contains "no PRs with a CHANGELOG warns" "$log" "::warning::No PRs found between v1.9.2 and v1.9.3; using the CHANGELOG only."
assert_contains "no PRs with a CHANGELOG uses its entries" "$(cat "$out")" "- Fixed a crash on startup."
assert_contains "no PRs: the PR text says the note comes from the CHANGELOG only" "$(cat "$work/pr-body.md")" "### No PRs found"

touch "$FAKE_GH/notes-fail"
log=$(build 2>&1); assert_eq "a generate-notes call that keeps failing exits 1" 1 "$?"
assert_contains "it was tried three times" "$log" "failed 3 times"
rm "$FAKE_GH/notes-fail"
fake_notes 10; touch "$FAKE_GH/notes-fail-once"
log=$(build 2>&1); assert_eq "a generate-notes call that fails once is retried" 0 "$?"
assert_contains "the retry is logged" "$log" "releases/generate-notes failed (attempt 1); retrying."
fake_notes 10; touch "$FAKE_GH/notes-blank-once"
log=$(build 2>&1); assert_eq "an empty generate-notes answer is retried, not taken as no PRs" 0 "$?"
assert_contains "the empty answer is logged as a failed attempt" "$log" "attempt 1"
assert_eq "the retry's PRs are used" "10" "$(tr '\n' ' ' < "$work/release-prs.txt" | sed 's/ $//')"

# The run is linked when the runner says which run this is.
fake_notes 10; cp "$SKELETON" "$next"
"$SCRIPTS/append-bullet.sh" "$next" feature "* Added offline storage. ([#10]($u/10)) {/* short: x */}"
GITHUB_RUN_ID=42 GITHUB_SERVER_URL=https://github.com build > /dev/null
assert_contains "the PR text links the run" "$(cat "$work/pr-body.md")" "[run 42](https://github.com/$GITHUB_REPOSITORY/actions/runs/42)"

# ---- bad arguments ----
build --bogus x > /dev/null 2>&1; assert_eq "an unknown option exits 2" 2 "$?"
"$SCRIPTS/build-release.sh" --version 1.9.3 > /dev/null 2>&1; assert_eq "missing options exit 2" 2 "$?"
build --changelog "$tmp/missing.md" > /dev/null 2>&1; assert_eq "an unreadable CHANGELOG exits 2" 2 "$?"
build --fallback-text "  " > /dev/null 2>&1; assert_eq "a blank fallback exits 2" 2 "$?"
(unset GITHUB_REPOSITORY; build > /dev/null 2>&1); assert_eq "no GITHUB_REPOSITORY fails" 1 "$?"

finish
