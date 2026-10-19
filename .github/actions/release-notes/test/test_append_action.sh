#!/usr/bin/env bash
# The append action's own run: blocks (extracted from append/action.yml), run the way the runner
# runs them: one step after another in one job, sharing $RUNNER_TEMP.
. "$(dirname "$0")/helpers.sh"
export GITHUB_REPOSITORY=newrelic/example-agent
. "$TEST_DIR/fake-gh.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp" "$FAKE_GH"' EXIT
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.com GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.com
q() { "$@" > /dev/null 2>&1; }

# step NAME: the run: block of the append step with that name.
step() {
  ruby -ryaml -e 'puts YAML.load_file(ARGV[0])["runs"]["steps"].find { |s| s["name"] == ARGV[1] }["run"]' \
    "$TEST_DIR/../append/action.yml" "$1"
}
run_step() { # NAME  (env comes from the caller). The runner runs `shell: bash` as bash -e -o pipefail.
  (cd "$tmp/agent" && bash --noprofile --norc -e -o pipefail -c "$(step "$1")")
}

# An agent repo whose origin has the merge commit of PR #5 tagged v1.0.0.
git init -q --bare "$tmp/origin.git"
q git clone "$tmp/origin.git" "$tmp/agent"
q git -C "$tmp/agent" commit --allow-empty -m "Merge pull request #5"
sha=$(git -C "$tmp/agent" rev-parse HEAD)
q git -C "$tmp/agent" tag v1.0.0; q git -C "$tmp/agent" push origin HEAD:refs/heads/main --tags
printf '{"mergeCommit":{"oid":"%s"}}' "$sha" > "$FAKE_GH/pr-5.json"

# What an earlier append in the same job (the CI dry runs) left behind.
export RUNNER_TEMP="$tmp/runner" GITHUB_OUTPUT="$tmp/output" GITHUB_STEP_SUMMARY="$tmp/summary"
mkdir -p "$RUNNER_TEMP/release-notes/docs/notes"
echo "appended #4 -> bug []" > "$RUNNER_TEMP/release-notes/status.txt"
printf 'before\n' > "$RUNNER_TEMP/release-notes/before.mdx"
printf 'after\n' > "$RUNNER_TEMP/release-notes/docs/notes/next.mdx"
: > "$GITHUB_OUTPUT"; : > "$GITHUB_STEP_SUMMARY"

GITHUB_ACTION_PATH="$TEST_DIR/../append" GH_TOKEN=x PR_NUMBER=5 TAG_PREFIX=v run_step "Skip a PR that is already released" > /dev/null
assert_eq "a PR in a release tag is skipped" "skipped #5: already released in v1.0.0" "$(cat "$RUNNER_TEMP/release-notes/status.txt")"
assert_contains "and the step says so" "$(cat "$GITHUB_OUTPUT")" "skip=true"

DRY_RUN=true DOCS_DIR=notes SKELETON_NAME=next.mdx run_step "Show the result"; rc=$?
assert_eq "the summary step of a skipped PR succeeds" 0 "$rc"
summary=$(cat "$GITHUB_STEP_SUMMARY")
assert_contains "the summary reports the skip" "$summary" "skipped #5: already released in v1.0.0"
assert_not_contains "the summary shows no diff left over from an earlier append" "$summary" "would change like this"

# A dry run that appends: the summary shows the change, and diff's exit 1 must not fail the step.
dir="$RUNNER_TEMP/release-notes"; mkdir -p "$dir/docs/notes"
echo "appended #6 -> bug []" > "$dir/status.txt"
printf 'before\n' > "$dir/before.mdx"; printf 'after\n' > "$dir/docs/notes/next.mdx"
: > "$GITHUB_STEP_SUMMARY"
DRY_RUN=true DOCS_DIR=notes SKELETON_NAME=next.mdx run_step "Show the result"; rc=$?
assert_eq "the summary step of a dry run with a change succeeds" 0 "$rc"
assert_contains "and shows the change" "$(cat "$GITHUB_STEP_SUMMARY")" "+after"

finish
