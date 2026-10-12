#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"

# run_check TITLE BODY HEAD LABELS -> sets $out and $rc
run_check() {
  out=$(PR_TITLE="$1" PR_BODY="$2" PR_HEAD="$3" PR_LABELS="$4" "$SCRIPTS/check-pr.sh" 2>&1); rc=$?
}

run_check 'fix: guard HarvestTimer against null (NR-1)' 'no note' NR-1 'release-note:bug'
assert_eq "labelled PR passes" 0 "$rc"
assert_contains "carries the update-in-place marker" "$out" '<!-- release-notes-check -->'
assert_contains "shows the section from the label" "$out" 'under **Bug fixes** (from its label)'
assert_contains "previews the bullet" "$out" '> Guarded HarvestTimer against null.'
assert_contains "suggests a Release note section" "$out" '## Release note'
assert_not_contains "no failure block when it passes" "$out" 'Fix before merging'

run_check 'fix: guard HarvestTimer against null' "$(printf '## Release note\nFixed a crash when the app was backgrounded.\n')" NR-1 'release-note:bug'
assert_eq "PR with a Release note passes" 0 "$rc"
assert_contains "previews the author's note" "$out" '> Fixed a crash when the app was backgrounded.'
assert_not_contains "no Release note suggestion when present" "$out" 'No `## Release note` section'

run_check 'fix: something' '' NR-1 ''
assert_eq "no label fails" 1 "$rc"
assert_contains "names the four labels" "$out" 'release-note:security'
assert_contains "still previews with a guessed section" "$out" 'guessed from the title'
assert_contains "failure block present" "$out" 'Fix before merging'

run_check 'fix: something' '' NR-1 "$(printf 'release-note:bug\nrelease-note:feature')"
assert_eq "two labels fail" 1 "$rc"
assert_contains "explains the conflict" "$out" 'more than one'

run_check 'docs: tweak readme' '' NR-1 ''
assert_eq "unlabelled docs PR passes" 0 "$rc"
assert_contains "says it is left out" "$out" 'Left out of the release notes'

run_check 'Bump okhttp from 4.11 to 4.12' '' dependabot/gradle/okhttp 'dependencies'
assert_eq "unlabelled Dependabot PR passes" 0 "$rc"
assert_contains "Dependabot PR is left out" "$out" 'Left out of the release notes'
run_check 'Bump okhttp from 4.11 to 4.12' '' dependabot/gradle/okhttp 'release-note:security'
assert_eq "labelled Dependabot PR passes" 0 "$rc"
assert_contains "labelled Dependabot PR is previewed" "$out" 'under **Security updates**'

run_check 'docs: document the API' '' NR-1 'release-note:enhancement'
assert_eq "labelled docs PR passes" 0 "$rc"
assert_contains "labelled docs PR is previewed" "$out" 'under **Improvements and enhancements**'

run_check 'fix: whatever' 'internal [skip-docs]' NR-1 ''
assert_eq "skip-docs passes without a label" 0 "$rc"
assert_contains "explains why" "$out" 'not included in the release notes ([skip-docs])'

run_check 'Backport main to develop' '' backport 'x'
assert_eq "bridge PR passes" 0 "$rc"
run_check 'Release v7.8.4' '' release/7.8.4 ''
assert_eq "release branch passes" 0 "$rc"

run_check 'Android: crash on start' '' NR-1 'release-note:bug'
assert_eq "an unknown title prefix still passes" 0 "$rc"
assert_contains "but is told it is not a Conventional Commit type" "$out" 'not in Conventional Commits form'

run_check 'Tidy up the harvester' '' NR-1 'release-note:enhancement'
assert_eq "non-conventional title still passes" 0 "$rc"
assert_contains "but is told about it" "$out" 'not in Conventional Commits form'

long='fix: this title is deliberately very long so that it goes beyond seventy characters'
run_check "$long" '' NR-1 'release-note:bug'
assert_eq "long title still passes" 0 "$rc"
assert_contains "long title warns" "$out" 'characters; it becomes the squash-merge commit message'

run_check 'security: reject cleartext uploads' '' NR-1 ''
assert_eq "unlabelled security PR fails on the label" 1 "$rc"
assert_contains "security type guesses the security section" "$out" 'under **Security updates**'

run_check 'fix: {braces} <tags>' '' NR-1 'release-note:bug'
assert_contains "preview is MDX-escaped like the real bullet" "$out" '&#123;braces&#125; &lt;tags&gt;'

PR_TITLE='' "$SCRIPTS/check-pr.sh" >/dev/null 2>&1; assert_eq "empty title is an internal error (exit 2)" 2 "$?"

# ---- internal errors never produce a report (review finding) ----
out=$(PR_TITLE='   ' PR_BODY='' PR_HEAD=x PR_LABELS='release-note:bug' "$SCRIPTS/check-pr.sh" 2>/dev/null); rc=$?
assert_eq "a blank title is an internal error, not 'needs attention'" 2 "$rc"
assert_eq "an internal error writes nothing to stdout" "" "$out"
out=$(PR_TITLE='   ' PR_BODY='' PR_HEAD=x PR_LABELS='release-note:bug' "$SCRIPTS/check-pr.sh" 2>&1 >/dev/null)
assert_contains "the reason goes to stderr" "$out" "blank"

run_check 'fix: x' '' NR-1 'release-note:bug'
assert_eq "the first line of the report is exactly the marker" '<!-- release-notes-check -->' "$(printf '%s\n' "$out" | head -1)"

# ---- hostile text in the PR cannot make the comment misbehave ----
run_check 'fix: </details> <!-- release-notes-check --> @newrelic/team #123 [x](http://evil) ![i](http://e/i.png)' '' NR-1 'release-note:bug'
assert_eq "hostile title still gets a report" 0 "$rc"
assert_eq "the marker appears exactly once" 1 "$(printf '%s\n' "$out" | grep -c 'release-notes-check -->')"
zw=$(printf '\342\200\213')
assert_contains "an @mention is defused in the preview" "$out" "@${zw}newrelic/team"
assert_contains "a #123 back-reference is defused in the preview" "$out" "#${zw}123"
assert_not_contains "a Markdown link is not live in the preview" "$out" "[x](http://evil)"
assert_not_contains "a Markdown image is not live in the preview" "$out" "![i](http://e/i.png)"
assert_contains "brackets are escaped in the preview" "$out" '\[x\]'

long=$(awk 'BEGIN { s = "<"; for (i = 0; i < 16; i++) s = s s; print s }')
run_check 'fix: long note' "$(printf '## Release note\n%s\n' "$long")" NR-1 'release-note:bug'
assert_eq "a huge release note still gets a report" 0 "$rc"
[ "${#out}" -lt 5000 ] && r=capped || r="too long (${#out})"; assert_eq "the preview is capped well under the comment limit" capped "$r"

run_check 'fix: x' '' NR-1 "$(printf 'release-note:bug\r')"
assert_eq "a label with a trailing carriage return is not recognised" 1 "$rc"
run_check 'fix: x' '' NR-1 "$(printf 'bug\nrelease-note:bug\nneeds-review')"
assert_eq "unrelated labels next to the release-note label are fine" 0 "$rc"
run_check 'refactor: x' '' NR-1 ''
assert_contains "the label error says how to opt out" "$out" '[skip-docs]'

# ---- fork PRs and unexpected aborts (final review) ----
out=$(PR_TITLE='fix: a fork change' PR_BODY='' PR_HEAD=main PR_FORK=true PR_LABELS='release-note:bug' "$SCRIPTS/check-pr.sh" 2>&1); rc=$?
assert_eq "a fork PR from its own main is checked, not skipped" 0 "$rc"
assert_contains "...and previewed" "$out" "under **Bug fixes**"
out=$(PR_TITLE='fix: a fork change' PR_BODY='' PR_HEAD=main PR_FORK=true PR_LABELS='' "$SCRIPTS/check-pr.sh" 2>&1); rc=$?
assert_eq "a fork PR from main with no label needs a label" 1 "$rc"
out=$(PR_TITLE='fix: x' PR_BODY='' PR_HEAD=main PR_FORK=false PR_LABELS='' "$SCRIPTS/check-pr.sh" 2>&1); rc=$?
assert_eq "a same-repo PR from main is still treated as a sync PR" 0 "$rc"
assert_contains "...and says why" "$out" "bridge or revert PR"

# An unexpected command failure must be exit 2 even when the failing command itself returns 1.

stubs=$(mktemp -d); trap 'rm -rf "$stubs"' EXIT
real_jq=$(command -v jq)
cat > "$stubs/jq" <<STUB
#!/usr/bin/env bash
case "\$*" in *"-r"*) exit 1 ;; *) exec "$real_jq" "\$@" ;; esac
STUB
chmod +x "$stubs/jq"
out=$(PATH="$stubs:$PATH" PR_TITLE='fix: x' PR_BODY='' PR_HEAD=x PR_LABELS='release-note:bug' "$SCRIPTS/check-pr.sh" 2>/dev/null); rc=$?
assert_eq "an unexpected abort inside the check is exit 2, not 'needs a label'" 2 "$rc"
assert_eq "...with nothing on stdout" "" "$out"

finish
