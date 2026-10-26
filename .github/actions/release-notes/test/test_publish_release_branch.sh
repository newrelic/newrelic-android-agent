#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
. "$TEST_DIR/fake-gh.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp" "$FAKE_GH"' EXIT
export GIT_BASE="file://$tmp" # no network: ls-remote and clones stay in $tmp
BRANCH=react-native_release_notes_1-9-3
FORK=newrelic-mobile-agents-bot/docs-website
printf 'note\n' > "$tmp/rn-193.mdx"; printf 'body\n' > "$tmp/body.md"
pub() {
  "$SCRIPTS/publish-release-branch.sh" --docs-repo newrelic/docs-website --docs-base develop --docs-dir notes \
    --file rn-193.mdx --source "$tmp/rn-193.mdx" --branch "$BRANCH" --subject 'React Native agent' \
    --version 1.9.3 --body-file "$tmp/body.md" "$@"
}
reset() {
  rm -f "$FAKE_GH"/*.json "$FAKE_GH"/*.log "$FAKE_GH"/*-fail "$FAKE_GH/merge-upstream.sh"
  unset FORK_PAT GH_TOKEN
}
calls() { cat "$FAKE_GH/calls.log" 2> /dev/null; }
tokens() { cat "$FAKE_GH/tokens.log" 2> /dev/null; }
# fake_fork PARENT: the bot's docs-website, a fork of PARENT.
fake_fork() {
  echo '{"login":"newrelic-mobile-agents-bot"}' > "$FAKE_GH/user.json"
  jq -n --arg p "$1" '{full_name:"newrelic-mobile-agents-bot/docs-website",parent:{full_name:$p},source:{full_name:$p}}' \
    > "$FAKE_GH/repo-newrelic-mobile-agents-bot-docs-website.json"
}
# fake_open_pr NUMBER HEAD-OWNER: the open PR that "gh pr list" finds for the branch.
fake_open_pr() {
  jq -n --argjson n "$1" --arg o "$2" \
    '[{number:$n,url:("https://github.com/newrelic/docs-website/pull/"+($n|tostring)),
       headRepositoryOwner:{login:$o},headRepository:{name:"docs-website"}}]' > "$FAKE_GH/pr-list.json"
}

# --- Where the branch goes (dry run) ---

reset; export GH_TOKEN=g
out=$(pub --dry-run 2> /dev/null); rc=$?
assert_eq "no fork token, no PR: exits 0" 0 "$rc"
assert_contains "no fork token, no PR: the branch goes to docs-website" "$out" "target=newrelic/docs-website"
assert_not_contains "no fork token: the token's user is never looked up" "$(calls)" "api user"
assert_contains "the PR lookup asks for the release branch" "$(calls)" "pr list --repo newrelic/docs-website --head $BRANCH --state open"

reset; export GH_TOKEN=g FORK_PAT=f; fake_fork newrelic/docs-website
out=$(pub --dry-run 2> /dev/null); rc=$?
assert_eq "a fork token: exits 0" 0 "$rc"
assert_contains "a fork token: the branch goes to the bot's fork" "$out" "target=$FORK"
assert_contains "the fork's owner is looked up with the fork token" "$(tokens)" "f|api user"
assert_contains "a dry run looks up the PR with GH_TOKEN" "$(tokens)" "g|pr list"

reset; export GH_TOKEN=g FORK_PAT=f; echo '{"login":"newrelic-mobile-agents-bot"}' > "$FAKE_GH/user.json"
err=$(pub --dry-run 2>&1 > /dev/null); rc=$?
assert_eq "a fork token whose docs-website is missing: exits 1" 1 "$rc"
assert_contains "the missing fork is named" "$err" "$FORK"
assert_contains "and the error says what it should be" "$err" "not a fork of newrelic/docs-website"
assert_not_contains "a bad fork stops before the PR lookup" "$(calls)" "pr list"

reset; export GH_TOKEN=g FORK_PAT=f; fake_fork someone/else
err=$(pub --dry-run 2>&1 > /dev/null); rc=$?
assert_eq "a fork of another repo: exits 1" 1 "$rc"
assert_contains "a fork of another repo: says so" "$err" "not a fork of newrelic/docs-website"

reset; export GH_TOKEN=g FORK_PAT=f; fake_fork newrelic/docs-website; fake_open_pr 26303 newrelic
out=$(pub --dry-run 2> /dev/null); rc=$?
assert_eq "an open PR on docs-website, with a fork token: exits 0" 0 "$rc"
assert_contains "an open PR is updated where it lives (docs-website)" "$out" "target=newrelic/docs-website"
assert_contains "and the PR is named" "$out" "existing PR #26303"

reset; export GH_TOKEN=g; fake_open_pr 26400 newrelic-mobile-agents-bot
err=$(pub --dry-run 2>&1 > /dev/null); rc=$?
assert_eq "an open PR on the fork, without a fork token: exits 1" 1 "$rc"
assert_contains "it says where the PR's branch is" "$err" "is on $FORK"
assert_contains "and which secret is missing" "$err" "BOT_FORK_PAT"

reset; export GH_TOKEN=g FORK_PAT=f; fake_fork newrelic/docs-website; fake_open_pr 26400 newrelic-mobile-agents-bot
out=$(pub --dry-run 2> /dev/null)
assert_contains "an open PR on the fork, with a fork token: goes to the fork" "$out" "target=$FORK"

# --- Bad arguments ---

reset
"$SCRIPTS/publish-release-branch.sh" --docs-repo newrelic/docs-website --docs-base develop --docs-dir notes \
  --file rn-193.mdx --source "$tmp/rn-193.mdx" --subject S --version 1.9.3 --body-file "$tmp/body.md" --dry-run > /dev/null 2>&1
assert_eq "a missing --branch exits 2" 2 "$?"
pub --dry-run --docs-repo docs-website > /dev/null 2>&1; assert_eq "a docs repo that is not owner/name exits 2" 2 "$?"
pub --dry-run --bogus > /dev/null 2>&1; assert_eq "an unknown flag exits 2" 2 "$?"
pub --dry-run --branch 'bad branch' > /dev/null 2>&1; assert_eq "a branch name with a space exits 2" 2 "$?"
assert_eq "bad arguments call gh not at all" "" "$(calls)"

finish
