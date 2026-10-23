#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
export GITHUB_SERVER_URL=https://github.com GITHUB_REPOSITORY=newrelic/example-agent GITHUB_RUN_ID=42
. "$TEST_DIR/fake-gh.sh"
trap 'rm -rf "$FAKE_GH"' EXIT
mr() { "$SCRIPTS/mark-ready.sh" --repo newrelic/docs-website --branch-prefix react-native_release_notes_ --prefix v "$@"; }
# fake_open_pr NUMBER IS-DRAFT: the one open PR that "gh pr list" finds for the branch.
fake_open_pr() {
  jq -n --argjson n "$1" --argjson d "$2" \
    '[{number:$n,isDraft:$d,url:("https://github.com/newrelic/docs-website/pull/"+($n|tostring))}]' > "$FAKE_GH/pr-list.json"
}
reset() { rm -f "$FAKE_GH/calls.log" "$FAKE_GH/pr-list.json" "$FAKE_GH"/*-fail; }
calls() { cat "$FAKE_GH/calls.log" 2> /dev/null; }

reset; fake_open_pr 26303 true
out=$(mr --version 1.9.3 --actor octocat 2> /dev/null); rc=$?
assert_eq "a draft PR exits 0" 0 "$rc"
assert_contains "it looks up the release's branch" "$(calls)" "pr list --repo newrelic/docs-website --head react-native_release_notes_1-9-3 --state open"
assert_contains "it marks the PR ready" "$(calls)" "pr ready 26303 --repo newrelic/docs-website"
assert_eq "it marks the PR ready before commenting" "pr ready" "$(grep -o -E '^pr (ready|comment)' "$FAKE_GH/calls.log" | head -1)"
assert_contains "the comment names the person who checked the note" "$(calls)" "pr comment 26303 --repo newrelic/docs-website --body Marked ready for review by @octocat"
assert_contains "the comment links the run" "$(calls)" "https://github.com/newrelic/example-agent/actions/runs/42"
assert_eq "it prints the PR" "Marked ready for review: https://github.com/newrelic/docs-website/pull/26303" "$out"

reset; fake_open_pr 26303 true
mr --version v1.9.3 --actor octocat > /dev/null 2>&1
assert_contains "a version typed as its tag finds the same branch" "$(calls)" "--head react-native_release_notes_1-9-3 "

reset; fake_open_pr 26310 true
"$SCRIPTS/mark-ready.sh" --repo newrelic/docs-website --branch-prefix android_release_notes_ --prefix '' --version 7.8.4 > /dev/null 2>&1
assert_contains "an empty tag prefix works (Android)" "$(calls)" "--head android_release_notes_7-8-4 "

reset; fake_open_pr 26303 true
(unset GITHUB_RUN_ID; mr --version 1.9.3 > /dev/null 2>&1)
assert_contains "without an actor or a run the comment still says what happened" "$(calls)" "--body Marked ready for review."
assert_not_contains "without an actor nobody is mentioned" "$(calls)" "@"

reset; fake_open_pr 26303 false
out=$(mr --version 1.9.3 --actor octocat 2> /dev/null); rc=$?
assert_eq "a PR that is already ready exits 0" 0 "$rc"
assert_eq "it says the PR was already ready" "Already ready for review: https://github.com/newrelic/docs-website/pull/26303" "$out"
assert_not_contains "it does not mark it again" "$(calls)" "pr ready"
assert_not_contains "it does not comment again" "$(calls)" "pr comment"

reset
err=$(mr --version 1.9.3 2>&1 > /dev/null); rc=$?
assert_eq "no open PR exits 1" 1 "$rc"
assert_contains "no open PR names the branch it looked for" "$err" "react-native_release_notes_1-9-3"

reset; fake_open_pr 26303 true; touch "$FAKE_GH/list-fail"
mr --version 1.9.3 > /dev/null 2>&1; assert_eq "a failed lookup exits 1" 1 "$?"
assert_not_contains "a failed lookup changes nothing" "$(calls)" "pr ready"

reset; fake_open_pr 26303 true; touch "$FAKE_GH/ready-fail"
mr --version 1.9.3 > /dev/null 2>&1; assert_eq "a failed 'ready' exits 1" 1 "$?"
assert_not_contains "a failed 'ready' posts no comment" "$(calls)" "pr comment"

reset; fake_open_pr 26303 true; touch "$FAKE_GH/comment-fail"
err=$(mr --version 1.9.3 --actor octocat 2>&1 > /dev/null); rc=$?
assert_eq "a failed comment still exits 0 (the PR is ready)" 0 "$rc"
assert_contains "a failed comment is a warning" "$err" "::warning::"

reset
mr --version 1.9 > /dev/null 2>&1; assert_eq "a bad version exits 2" 2 "$?"
mr --version 1.9.3 --prefix v1 > /dev/null 2>&1; assert_eq "a bad tag prefix exits 2" 2 "$?"
mr --version 1.9.3 --repo docs-website > /dev/null 2>&1; assert_eq "a repo that is not owner/name exits 2" 2 "$?"
mr --version 1.9.3 --branch-prefix 'react native_' > /dev/null 2>&1; assert_eq "a bad branch prefix exits 2" 2 "$?"
"$SCRIPTS/mark-ready.sh" --repo newrelic/docs-website --branch-prefix x_release_notes_ --version 1.9.3 > /dev/null 2>&1
assert_eq "a missing --prefix exits 2" 2 "$?"
mr --version 1.9.3 --bogus x > /dev/null 2>&1; assert_eq "an unknown argument exits 2" 2 "$?"
assert_eq "bad arguments call gh not at all" "" "$(calls)"

finish
