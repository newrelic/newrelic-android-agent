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

# --- Publishing (bare repos stand in for docs-website and the bot's fork) ---

export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.com GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.com
q() { "$@" > /dev/null 2>&1; }
UP="$tmp/newrelic/docs-website.git" FK="$tmp/newrelic-mobile-agents-bot/docs-website.git"
# repos: docs-website with develop holding notes/.keep, the fork holding the same develop, and a
# merge-upstream that syncs the fork's develop. $tmp/seed is a clone of docs-website.
repos() {
  rm -rf "$tmp/newrelic" "$tmp/newrelic-mobile-agents-bot" "$tmp/seed" "$tmp/editor"
  mkdir -p "$tmp/newrelic" "$tmp/newrelic-mobile-agents-bot"
  q git init --bare "$UP"; q git init --bare "$FK"
  q git clone "$UP" "$tmp/seed"; mkdir -p "$tmp/seed/notes"; : > "$tmp/seed/notes/.keep"
  q git -C "$tmp/seed" add -A; q git -C "$tmp/seed" commit -m base
  q git -C "$tmp/seed" push origin HEAD:refs/heads/develop; q git -C "$tmp/seed" push "$FK" HEAD:refs/heads/develop
  printf 'git -C "%s" push -q "%s" develop:develop\n' "$UP" "$FK" > "$FAKE_GH/merge-upstream.sh"
}
on() { git -C "$1" show "$BRANCH:notes/rn-193.mdx" 2> /dev/null; } # on REPO: the note on the branch
publish_env() { export BOT_PAT=b BOT_EMAIL=bot@example.com; }

reset; repos; publish_env
# Without the test's GIT_AUTHOR_*/GIT_COMMITTER_* (they outrank git config), as on a runner.
out=$(unset GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL; pub 2> "$tmp/err"); rc=$?
assert_eq "no fork: exits 0" 0 "$rc"
assert_eq "no fork: the note is on the branch on docs-website" "note" "$(on "$UP")"
assert_eq "no fork: the fork gets nothing" "" "$(on "$FK")"
assert_contains "no fork: pushed to docs-website with the bot token" "$out" "push newrelic/docs-website $BRANCH with bot token"
assert_contains "no fork: the PR is opened" "$out" "opened https://github.com/newrelic/docs-website/pull/999"
assert_contains "the PR is today's draft, title and label" "$(calls)" \
  "pr create --repo newrelic/docs-website --base develop --head $BRANCH --draft --title React Native agent 1.9.3 release notes --label documentation"
assert_eq "the commit message is today's" "docs: add React Native agent 1.9.3 release notes" "$(git -C "$UP" log -1 --format=%s "$BRANCH")"
assert_eq "the commit is the bot's" "newrelic-mobile-agent-bot bot@example.com" "$(git -C "$UP" log -1 --format='%an %ae' "$BRANCH")"

reset; repos; publish_env; export FORK_PAT=f; fake_fork newrelic/docs-website
out=$(pub 2> "$tmp/err"); rc=$?
assert_eq "fork: exits 0" 0 "$rc"
assert_eq "fork: the note is on the branch on the fork" "note" "$(on "$FK")"
assert_eq "fork: docs-website gets no branch" "" "$(on "$UP")"
assert_contains "fork: pushed with the fork token" "$out" "push $FORK $BRANCH with fork token"
assert_contains "fork: the PR's head is the fork's branch" "$(calls)" "--head newrelic-mobile-agents-bot:$BRANCH --draft"
assert_contains "fork: the fork is synced with the fork token" "$(tokens)" "f|api -X"
assert_contains "fork: the PR is opened with the bot token" "$(tokens)" "b|pr create"

fake_open_pr 26400 newrelic-mobile-agents-bot
out=$(pub 2> "$tmp/err"); rc=$?
assert_eq "an identical re-run exits 0" 0 "$rc"
assert_contains "an identical re-run reuses the branch" "$out" "already holds identical notes; reusing it."
assert_not_contains "an identical re-run pushes nothing" "$out" "push "
assert_contains "an identical re-run updates the open PR" "$out" "updated #26400"
assert_contains "the open PR's text is refreshed" "$(calls)" "pr edit 26400 --repo newrelic/docs-website --body-file"

q git clone --branch "$BRANCH" "$FK" "$tmp/editor"; echo edited > "$tmp/editor/notes/rn-193.mdx"
q git -C "$tmp/editor" commit -am "a person's edit"; q git -C "$tmp/editor" push origin "$BRANCH"
tip=$(git -C "$FK" rev-parse "$BRANCH")
err=$(pub 2>&1 > /dev/null); rc=$?
assert_eq "a person's edit on the fork: exits 1" 1 "$rc"
assert_contains "a person's edit: the error says so" "$err" \
  "$BRANCH already exists on $FORK with different content (it may hold a person's edits)"
assert_eq "a person's edit is kept" "$tip" "$(git -C "$FK" rev-parse "$BRANCH")"

out=$(pub --overwrite 2> "$tmp/err"); rc=$?
assert_eq "overwrite: exits 0" 0 "$rc"
assert_eq "overwrite: the branch holds the note again" "note" "$(on "$FK")"
assert_contains "overwrite: pushed with the fork token" "$out" "push $FORK $BRANCH with fork token"

reset; repos; publish_env; export FORK_PAT=f; fake_fork newrelic/docs-website
q git -C "$tmp/seed" commit --allow-empty -m newer; q git -C "$tmp/seed" push origin HEAD:refs/heads/develop
cat > "$FAKE_GH/merge-upstream.sh" << EOF
git -C "$tmp/seed" commit -q --allow-empty -m "moved after the clone"
git -C "$tmp/seed" push -q origin HEAD:refs/heads/develop
git -C "$UP" push -q "$FK" develop:develop
EOF
pub > /dev/null 2> "$tmp/err"; rc=$?
assert_eq "docs-website moved since the fork was synced and after the clone: exits 0" 0 "$rc"
assert_eq "the branch is the note on the develop commit that was cloned" "newer" "$(git -C "$FK" log -1 --format=%s "$BRANCH^" 2> /dev/null)"

reset; repos; publish_env; export FORK_PAT=f; fake_fork newrelic/docs-website; touch "$FAKE_GH/merge-fail"
err=$(pub 2>&1 > /dev/null); rc=$?
assert_eq "a failed fork sync exits 1" 1 "$rc"
assert_contains "a failed fork sync says so" "$err" "sync $FORK"
assert_eq "a failed fork sync pushes nothing" "" "$(on "$FK")"

reset; repos; publish_env; export FORK_PAT=f; fake_fork newrelic/docs-website; fake_open_pr 26303 newrelic
out=$(pub 2> "$tmp/err"); rc=$?
assert_eq "an open PR on docs-website, with a fork token: exits 0" 0 "$rc"
assert_contains "it pushes to docs-website with the bot token" "$out" "push newrelic/docs-website $BRANCH with bot token"
assert_contains "it updates that PR" "$(calls)" "pr edit 26303 --repo newrelic/docs-website --body-file"
assert_not_contains "it opens no second PR" "$(calls)" "pr create"
assert_eq "the fork gets nothing" "" "$(on "$FK")"

reset; repos; publish_env; export FORK_PAT=f; fake_fork newrelic/docs-website; touch "$FAKE_GH/maintainer-fail"
out=$(pub 2>&1); rc=$?
assert_eq "maintainer edits refused: still exits 0" 0 "$rc"
assert_eq "maintainer edits refused: the PR is created on the second try" 2 "$(grep -c '^pr create' "$FAKE_GH/calls.log")"
assert_contains "the second try turns maintainer edits off" "$(grep '^pr create' "$FAKE_GH/calls.log" | tail -1)" "--no-maintainer-edit"
assert_contains "and it says so" "$out" "::notice::"
assert_contains "naming maintainers" "$out" "maintainers"

reset; repos; publish_env
q git -C "$tmp/seed" pull -q origin develop; echo published > "$tmp/seed/notes/rn-193.mdx"
q git -C "$tmp/seed" add -A; q git -C "$tmp/seed" commit -m published; q git -C "$tmp/seed" push origin HEAD:refs/heads/develop
err=$(pub 2>&1 > /dev/null); rc=$?
assert_eq "a note already on the base branch: exits 1" 1 "$rc"
assert_contains "it says so, as today" "$err" "rn-193.mdx already exists on newrelic/docs-website develop. Run again with overwrite=true to replace it."
assert_eq "it pushes nothing" "" "$(on "$UP")"

finish
