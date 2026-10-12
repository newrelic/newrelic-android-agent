#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
b() { "$SCRIPTS/build-bullet.sh"; }

out=$(PR_TITLE='fix: guard HarvestTimer against null (NR-1)' PR_BODY='no note here' b)
assert_eq "title-derived bullet" "Guarded HarvestTimer against null." "$(jq -r .bullet <<<"$out")"
assert_eq "short has no full stop" "Guarded HarvestTimer against null" "$(jq -r .short <<<"$out")"
assert_eq "section from commit type" bug "$(jq -r .section <<<"$out")"

out=$(PR_TITLE='feat: thing' b)
assert_eq "feat maps to feature" feature "$(jq -r .section <<<"$out")"
out=$(PR_TITLE='refactor: tidy' b)
assert_eq "other types default to enhancement" enhancement "$(jq -r .section <<<"$out")"

out=$(PR_TITLE='fix: x' HINT_SECTION=security b)
assert_eq "label hint overrides the commit type" security "$(jq -r .section <<<"$out")"
out=$(PR_TITLE='fix: x' HINT_SECTION=bogus b)
assert_eq "invalid hint is ignored" bug "$(jq -r .section <<<"$out")"

out=$(PR_TITLE='fix: boring title' PR_BODY="$(printf '## Release note\nFixed a crash in `Foo` when <bar> was null.\n')" b)
assert_eq "author release note beats the title, MDX-escaped, code kept" 'Fixed a crash in `Foo` when &lt;bar&gt; was null.' "$(jq -r .bullet <<<"$out")"
assert_eq "short is plain text" "Fixed a crash in Foo when bar was null" "$(jq -r .short <<<"$out")"

out=$(PR_TITLE='fix: boring title' PR_BODY="$(printf '## Release note\nN/A\n')" b)
assert_eq "placeholder note falls back to the title" "Boring title." "$(jq -r .bullet <<<"$out")"

out=$(PR_TITLE='feat: handle {braces} and <tags>' b)
assert_eq "title text is MDX-escaped" 'Handled &#123;braces&#125; and &lt;tags&gt;.' "$(jq -r .bullet <<<"$out")"

long=$(printf 'word %.0s' $(seq 1 60))
out=$(PR_TITLE="fix: $long" b)
len=$(jq -r '.short | length' <<<"$out")
[ "$len" -le 120 ] && r=ok || r="too long ($len)"; assert_eq "short is capped at 120 characters" ok "$r"

PR_TITLE='' b >/dev/null 2>&1; assert_eq "empty title is an error" 1 "$?"

finish
