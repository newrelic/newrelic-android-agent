#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/bin"

# gh stub: "pr view" prints $STUB_PR_JSON, "pr comment" is recorded.
cat > "$tmp/bin/gh" <<'STUB'
#!/usr/bin/env bash
case "$1 $2" in
  "pr view") echo "view" >> "$STUB_CALL_LOG"
             [ "${STUB_FAIL:-0}" = 1 ] && exit 1
             cat "$STUB_PR_JSON" ;;
  "pr comment") echo "$*" >> "$STUB_COMMENT_LOG" ;;
esac
STUB
chmod +x "$tmp/bin/gh"
export PATH="$tmp/bin:$PATH" GITHUB_REPOSITORY=newrelic/newrelic-android-agent
export STUB_PR_JSON="$tmp/pr.json" STUB_COMMENT_LOG="$tmp/comments.log" STUB_CALL_LOG="$tmp/calls.log"
: > "$STUB_CALL_LOG"; : > "$STUB_COMMENT_LOG"
n="$tmp/next.mdx"; cp "$REPO_ROOT/android-agent-next.mdx" "$n"

pr_json() { # number title body head merged labels-json
  jq -n --argjson n "$1" --arg t "$2" --arg b "$3" --arg h "$4" --arg m "$5" --argjson l "$6" \
    '{number:$n,title:$t,body:$b,headRefName:$h,mergedAt:(if $m=="" then null else $m end),
      url:("https://github.com/newrelic/newrelic-android-agent/pull/"+($n|tostring)),
      labels:($l|map({name:.}))}' > "$STUB_PR_JSON"
}
run() { "$SCRIPTS/process-pr.sh" "$1" "$n"; }

pr_json 5 'fix: guard HarvestTimer against null (NR-1)' 'Fixes it' NR-1-crash '2026-10-01T00:00:00Z' '["release-note:bug"]'
assert_eq "labelled PR is appended" "appended #5 -> bug []" "$(run 5)"
assert_contains "bullet line has link and short comment" "$(cat "$n")" '* Guarded HarvestTimer against null. ([#5](https://github.com/newrelic/newrelic-android-agent/pull/5)) {/* short: Guarded HarvestTimer against null */}'
assert_not_contains "labelled PR has no unlabelled flag" "$(cat "$n")" "flag: unlabelled"

assert_eq "no comment for a labelled PR" "" "$(cat "$STUB_COMMENT_LOG")"
calls_before=$(wc -l < "$STUB_CALL_LOG" | tr -d ' ')
assert_eq "same PR again is skipped" "skipped #5: already in next file" "$(run 5)"
assert_eq "an already-present PR makes no gh call" "$calls_before" "$(wc -l < "$STUB_CALL_LOG" | tr -d ' ')"

pr_json 6 'fix: other' '' NR-2 '2026-10-01T00:00:00Z' '[]'
assert_eq "unlabelled PR is appended" "appended #6 -> bug [unlabelled]" "$(run 6)"
assert_eq "no comment when COMMENT_ON_UNLABELLED is unset" "" "$(cat "$STUB_COMMENT_LOG")"
cp "$REPO_ROOT/android-agent-next.mdx" "$n"
export COMMENT_ON_UNLABELLED=1
assert_eq "unlabelled PR appended with flag" "appended #6 -> bug [unlabelled]" "$(run 6)"
assert_contains "unlabelled flag recorded in the file" "$(cat "$n")" "{/* flag: unlabelled */}"
assert_contains "unlabelled PR gets a comment" "$(cat "$STUB_COMMENT_LOG")" "pr comment 6"
unset COMMENT_ON_UNLABELLED

pr_json 7 'chore: update gradle.properties' '' NR-3 '2026-10-01T00:00:00Z' '[]'
assert_eq "release chore skipped" "skipped #7: release chore" "$(run 7)"
pr_json 8 'Merge main' '' release/7.8.4 '2026-10-01T00:00:00Z' '[]'
assert_eq "release branch skipped" "skipped #8: release branch" "$(run 8)"
pr_json 14 'docs: tweak readme' '' NR-8 '2026-10-01T00:00:00Z' '[]'
assert_contains "unlabelled internal change skipped" "$(run 14)" "skipped #14: internal change"
pr_json 15 'docs: document the new API' '' NR-9 '2026-10-01T00:00:00Z' '["release-note:enhancement"]'
assert_eq "labelled docs PR is included" "appended #15 -> enhancement []" "$(run 15)"
pr_json 16 'Bump okhttp from 4.11 to 4.12' '' dependabot/gradle/okhttp '2026-10-01T00:00:00Z' '["dependencies"]'
assert_contains "unlabelled Dependabot PR skipped" "$(run 16)" "skipped #16: internal change"
pr_json 12 'Back port main to develop' '' main '2026-10-01T00:00:00Z' '[]'
assert_eq "bridge PR skipped" "skipped #12: bridge or revert PR" "$(run 12)"
pr_json 13 'fix: with note' "$(printf '## Release note\nFixed the thing users saw.\n')" NR-7 '2026-10-01T00:00:00Z' '["release-note:bug"]'
assert_eq "author release note used" "appended #13 -> bug []" "$(run 13)"
assert_contains "author text lands in the file" "$(cat "$n")" '* Fixed the thing users saw. ([#13]'
pr_json 9 'docs: tweak' 'internal [skip-docs]' NR-4 '2026-10-01T00:00:00Z' '[]'
assert_eq "[skip-docs] skipped" "skipped #9: [skip-docs]" "$(run 9)"
pr_json 10 'feat: wip' '' NR-5 '' '[]'
assert_eq "unmerged PR skipped" "skipped #10: not merged" "$(run 10)"
pr_json 11 'feat: two' '' NR-6 '2026-10-01T00:00:00Z' '["release-note:bug","release-note:feature"]'
export COMMENT_ON_UNLABELLED=1; : > "$STUB_COMMENT_LOG"
assert_eq "two labels are flagged as conflicting, section from the title" "appended #11 -> feature [conflicting-labels]" "$(run 11)"
assert_contains "conflicting labels are recorded in the file" "$(cat "$n")" "{/* flag: conflicting-labels */}"
assert_contains "the comment says there is more than one label" "$(cat "$STUB_COMMENT_LOG")" "more than one"
assert_not_contains "the comment does not claim there is no label" "$(cat "$STUB_COMMENT_LOG")" "has no"
unset COMMENT_ON_UNLABELLED

pr_json 17 'docs: x' '' NR-10 '2026-10-01T00:00:00Z' '["release-note:bug","release-note:feature"]'
assert_eq "an internal-looking PR with conflicting labels is still included" "appended #17 -> enhancement [conflicting-labels]" "$(run 17)"

pr_json 18 'Sync main into develop' '' develop '2026-10-01T00:00:00Z' '[]'
assert_eq "a sync PR from develop is skipped" "skipped #18: bridge or revert PR" "$(run 18)"

# a fork PR from the contributor's own "main" is a normal change, not a sync PR
jq -n '{number:23,title:"fix: crash from a fork",body:"",headRefName:"main",isCrossRepository:true,mergedAt:"2026-10-01T00:00:00Z",url:"https://github.com/newrelic/newrelic-android-agent/pull/23",labels:[{name:"release-note:bug"}]}' > "$STUB_PR_JSON"
assert_eq "a fork PR from main is included" "appended #23 -> bug []" "$(run 23)"

# null fields from gh are tolerated
jq -n '{number:19,title:"fix: null fields",body:null,headRefName:"NR-11",mergedAt:"2026-10-01T00:00:00Z",url:"https://github.com/newrelic/newrelic-android-agent/pull/19",labels:null}' > "$STUB_PR_JSON"
assert_eq "null labels and body are treated as unlabelled with no note" "appended #19 -> bug [unlabelled]" "$(run 19)"

# hostile title becomes exactly one well-formed bullet line
lines_before=$(grep -c '^\* ' "$n")
pr_json 20 'fix: $(touch '"$tmp"'/pwned) `id` {/* flag: x */} <b>' '' NR-12 '2026-10-01T00:00:00Z' '["release-note:bug"]'
run 20 > /dev/null
assert_eq "a hostile title adds exactly one bullet line" "$((lines_before + 1))" "$(grep -c '^\* ' "$n")"
[ -e "$tmp/pwned" ] && r=executed || r=inert; assert_eq "a command substitution in a title is never executed" inert "$r"
assert_eq "a hostile title cannot spoof a parseable flag" 0 "$(grep -c '{/\* flag: x' "$n")"

# a very large body is fine. The body goes through a file (jq --rawfile): Linux caps a single
# argument at 128 KiB, so building this fixture with jq --arg would fail for the wrong reason.
big=$(awk 'BEGIN { s = "x"; for (i = 0; i < 17; i++) s = s s; print s }')
printf '## Release note\nFixed it.\n\n%s' "$big" > "$tmp/big-body.txt"
jq -n --rawfile b "$tmp/big-body.txt" '{number:21,title:"fix: big body",body:$b,headRefName:"NR-13",mergedAt:"2026-10-01T00:00:00Z",url:"https://github.com/newrelic/newrelic-android-agent/pull/21",labels:[{name:"release-note:bug"}]}' > "$STUB_PR_JSON"
assert_eq "a body over 100 KB is handled" "appended #21 -> bug []" "$(run 21)"

# failures are loud and leave the file alone
before=$(cat "$n")
pr_json 22 'fix: y' '' NR-14 '2026-10-01T00:00:00Z' '["release-note:bug"]'
STUB_FAIL=1 run 22 > /dev/null 2>&1; rc=$?; [ "$rc" -ne 0 ] && r=failed || r=passed; assert_eq "a gh failure fails the script" failed "$r"
echo 'not json' > "$STUB_PR_JSON"; run 22 > /dev/null 2>&1; assert_eq "malformed gh output exits 1" 1 "$?"
run abc > /dev/null 2>&1; assert_eq "a non-numeric PR number exits 2" 2 "$?"
"$SCRIPTS/process-pr.sh" 22 "$tmp/missing.mdx" > /dev/null 2>&1; assert_eq "a missing next file exits 2" 2 "$?"
assert_eq "failures leave the file untouched" "$before" "$(cat "$n")"

finish
