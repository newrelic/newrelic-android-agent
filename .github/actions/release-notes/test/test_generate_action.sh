#!/usr/bin/env bash
# The generate action's "Open or update the docs PR" run: block (extracted from generate/action.yml),
# run the way the runner runs it, against bare repos standing in for docs-website and the bot's fork.
. "$(dirname "$0")/helpers.sh"
. "$TEST_DIR/fake-gh.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp" "$FAKE_GH"' EXIT
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.com GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.com
q() { "$@" > /dev/null 2>&1; }
step() { # NAME: the run: block of the generate step with that name
  ruby -ryaml -e 'puts YAML.load_file(ARGV[0])["runs"]["steps"].find { |s| s["name"] == ARGV[1] }["run"]' \
    "$TEST_DIR/../generate/action.yml" "$1"
}
UP="$tmp/newrelic/docs-website.git" FK="$tmp/newrelic-mobile-agents-bot/docs-website.git"
BRANCH=react-native_release_notes_1-9-3
repos() {
  rm -rf "$tmp/newrelic" "$tmp/newrelic-mobile-agents-bot" "$tmp/seed" "$FAKE_GH"/*.log
  mkdir -p "$tmp/newrelic" "$tmp/newrelic-mobile-agents-bot"
  q git init --bare "$UP"; q git init --bare "$FK"
  q git clone "$UP" "$tmp/seed"; q git -C "$tmp/seed" commit --allow-empty -m base
  q git -C "$tmp/seed" push origin HEAD:refs/heads/develop; q git -C "$tmp/seed" push "$FK" HEAD:refs/heads/develop
}
echo '{"login":"newrelic-mobile-agents-bot"}' > "$FAKE_GH/user.json"
echo '{"parent":{"full_name":"newrelic/docs-website"},"source":{"full_name":"newrelic/docs-website"}}' \
  > "$FAKE_GH/repo-newrelic-mobile-agents-bot-docs-website.json"

export RUNNER_TEMP="$tmp/runner" GITHUB_ACTION_PATH="$TEST_DIR/../generate" GIT_BASE="file://$tmp"
mkdir -p "$RUNNER_TEMP/release-notes/out"
printf 'note\n' > "$RUNNER_TEMP/release-notes/out/react-native-agent-193.mdx"
printf 'body\n' > "$RUNNER_TEMP/release-notes/pr-body.md"
run_open() { # FORK_PAT value; the env the action passes
  (cd "$tmp" && GH_TOKEN=b BOT_PAT=b FORK_PAT=$1 BOT_EMAIL=bot@example.com OVERWRITE=false \
    DOCS_REPO=newrelic/docs-website DOCS_BASE=develop DOCS_DIR=notes SUBJECT='React Native agent' VERSION=1.9.3 \
    FILE=react-native-agent-193.mdx BRANCH=$BRANCH \
    bash --noprofile --norc -e -o pipefail -c "$(step "Open or update the docs PR")")
}
on() { git -C "$1" show "$BRANCH:notes/react-native-agent-193.mdx" 2> /dev/null; }

repos
run_open f > "$tmp/out" 2>&1; rc=$?
assert_eq "with a fork token the step exits 0" 0 "$rc"
assert_eq "with a fork token the branch lands on the fork" "note" "$(on "$FK")"
assert_eq "and not on docs-website" "" "$(on "$UP")"
assert_contains "the git credential helper is set up first" "$(cat "$FAKE_GH/calls.log")" "auth setup-git"

repos
run_open '' > "$tmp/out" 2>&1; rc=$?
assert_eq "without a fork token the step exits 0" 0 "$rc"
assert_eq "without a fork token the branch lands on docs-website" "note" "$(on "$UP")"
assert_eq "and not on the fork" "" "$(on "$FK")"

finish
