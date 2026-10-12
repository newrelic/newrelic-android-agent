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

PR_TITLE='' "$SCRIPTS/check-pr.sh" >/dev/null 2>&1; assert_eq "empty title is an error" 1 "$?"

finish
