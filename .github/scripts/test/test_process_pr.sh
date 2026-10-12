#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/bin"

# gh stub: "pr view" prints $STUB_PR_JSON, "pr comment" is recorded.
cat > "$tmp/bin/gh" <<'STUB'
#!/usr/bin/env bash
case "$1 $2" in
  "pr view") cat "$STUB_PR_JSON" ;;
  "pr comment") echo "$*" >> "$STUB_COMMENT_LOG" ;;
esac
STUB
chmod +x "$tmp/bin/gh"
export PATH="$tmp/bin:$PATH" GITHUB_REPOSITORY=newrelic/newrelic-android-agent
export STUB_PR_JSON="$tmp/pr.json" STUB_COMMENT_LOG="$tmp/comments.log"
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

assert_eq "same PR again is skipped" "skipped #5: already in next file" "$(run 5)"

pr_json 6 'fix: other' '' NR-2 '2026-10-01T00:00:00Z' '[]'
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
assert_eq "two labels treated as unlabelled, section from the title" "appended #11 -> feature [unlabelled]" "$(run 11)"

finish
