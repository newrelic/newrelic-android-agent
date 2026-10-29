#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
. "$SCRIPTS/lib.sh"
unset PR_BODY HINT_SECTION
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
[ "$len" -le 201 ] && r=ok || r="too long ($len)"; assert_eq "short is capped at 200 characters plus an ellipsis" ok "$r"
assert_eq "a cut short ends with an ellipsis" "$(printf '\342\200\246')" "$(jq -r '.short[-1:]' <<<"$out")"

PR_TITLE='' b >/dev/null 2>&1; assert_eq "empty title is an error" 1 "$?"

# ---- hostile and unusual input (review findings) ----
for loc in C en_US.UTF-8; do
  out=$(LC_ALL=$loc PR_TITLE="$(printf 'feat: \360\237\230\200 foo')" b 2>&1)
  assert_eq "emoji-first title works under LC_ALL=$loc" "$(printf '\360\237\230\200 foo.')" "$(jq -r .bullet <<<"$out")"
  out=$(LC_ALL=$loc PR_TITLE="$(printf 'fix: \303\234ber caf\303\251')" b 2>&1)
  assert_eq "accented-first title works under LC_ALL=$loc" "$(printf '\303\234ber caf\303\251.')" "$(jq -r .bullet <<<"$out")"
done

out=$(PR_TITLE='fix: handle `{/* flag: x */}` in parser' b)
line="* $(jq -r .bullet <<<"$out") ([#5](https://github.com/o/r/pull/5)) {/* short: $(jq -r .short <<<"$out") */}"
assert_eq "a comment opener in a code span cannot be stripped away" 0 "$(printf '%s' "$line" | strip_comments | grep -c '^\* Handled `' | grep -qx 1; echo $?)"
assert_eq "a comment opener in a title cannot spoof a flag" 0 "$(printf '%s\n' "$line" | grep -oE '\{/\* flag: [a-z-]+ \*/\}' | wc -l | tr -d ' ')"
assert_eq "the PR link is still the one the parsers see" 5 "$(printf '%s\n' "$line" | bullet_pr_number)"

PR_TITLE='   ' b >/dev/null 2>&1; assert_eq "whitespace-only title is an error" 1 "$?"
PR_TITLE="$(printf 'fix: a\001b\033c')" b | jq -e '.bullet | test("[[:cntrl:]]") | not' > /dev/null; assert_eq "control characters never reach the bullet" 0 "$?"

out=$(PR_TITLE='fix: boring' PR_BODY="$(printf '## Release note\nAdded a feature people asked for.\n')" b)
assert_eq "a release note changes the text but not the section" bug "$(jq -r .section <<<"$out")"
out=$(PR_TITLE='fix: boring' PR_BODY="$(printf '## Release note\n- item one\n- item two\n')" b)
assert_eq "list items in a note are joined, not dropped" "Item one - item two." "$(jq -r .bullet <<<"$out")"

finish
