#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
u=https://github.com/newrelic/newrelic-android-agent/pull
n="$tmp/next.mdx"; o="$tmp/out.mdx"; fl="$tmp/flags.txt"
cp "$REPO_ROOT/android-agent-next.mdx" "$n"
a() { "$SCRIPTS/append-bullet.sh" "$n" "$@"; }
gen() { "$SCRIPTS/generate-docs.sh" --next "$n" --version 7.8.4 --date 2026-10-20 --out "$o" "$@" 2>/dev/null; }

a bug "* Fixed a crash on \`Foo\` startup. ([#1]($u/1)) {/* short: Fixed a crash on Foo startup */}"
a bug "* Fixed Bob's bug. ([#2]($u/2)) {/* short: Fixed Bob's bug */} {/* flag: unlabelled */}"
a feature "* Added offline storage. ([#3]($u/3)) {/* short: Added offline storage */}"
# a person adds this one by editing the file, which is why it bypasses append-bullet.sh (that refuses link-less bullets)
awk '{ print } /^## Improvements and enhancements$/ { print ""; print "* Hand written bullet with no link or short." }' "$n" > "$n.tmp" && mv "$n.tmp" "$n"

gen --download-link https://central.sonatype.com/artifact/com.newrelic.agent.android/android-agent --flags-out "$fl"
out=$(cat "$o")

assert_contains "frontmatter has blank category" "$out" "
category:
"
assert_contains "downloadLink emitted" "$out" "downloadLink: 'https://central.sonatype.com/artifact/com.newrelic.agent.android/android-agent'"
assert_contains "features array populated" "$out" "features:
  - 'Added offline storage'"
assert_contains "bugs array populated and YAML-quoted" "$out" "bugs:
  - 'Fixed a crash on Foo startup'
  - 'Fixed Bob''s bug'"
assert_contains "short derived for hand-written bullet" "$out" "  - 'Hand written bullet with no link or short.'"
assert_contains "empty security array present" "$out" "security: []"
assert_not_contains "empty security section deleted" "$out" "## Security updates"
assert_not_contains "comments stripped from body" "$out" "{/*"
assert_contains "bug bullet in body with dash and link" "$out" "- Fixed a crash on \`Foo\` startup. ([#1]($u/1))"
assert_contains "support statement preserved" "$out" "## Support statement"
assert_contains "eol link preserved" "$out" "mobile-agents-eol-policy/#android-eol"
assert_eq "flags file lists flagged PRs" "#2 unlabelled" "$(cat "$fl")"
assert_eq "section order feature before bug" "1" "$(printf '%s\n' "$out" | grep -n -e '^## ' | sed -n '1p;2p;3p;4p' | awk -F: '/New features/{f=$1} /Bug fixes/{b=$1} END{print (f<b)}')"

gen; assert_not_contains "downloadLink key omitted when no link" "$(cat "$o")" "downloadLink"

# nothing to publish
cp "$REPO_ROOT/android-agent-next.mdx" "$n"
"$SCRIPTS/generate-docs.sh" --next "$n" --version 7.8.4 --date 2026-10-20 --out "$o" 2>/dev/null
assert_eq "no bullets exits 3" 3 "$?"

# reconciliation: only-in-A, only-in-B, in-both appear once
cp "$REPO_ROOT/android-agent-next.mdx" "$n"
a bug "* Only on next branch. ([#10]($u/10)) {/* short: Only on next */}"
a bug "* In both. ([#11]($u/11)) {/* short: In both */}"
a bug "* In both again. ([#11]($u/11)) {/* short: Duplicate */}"
a bug "* Only in compare. ([#12]($u/12)) {/* short: Only in compare */}"
a bug "* Released later, not in this release. ([#99]($u/99)) {/* short: Later */}"
"$SCRIPTS/filter-bullets.sh" "$n" keep 10 11 12 > "$tmp/release-src.mdx"
"$SCRIPTS/generate-docs.sh" --next "$tmp/release-src.mdx" --version 7.8.4 --date 2026-10-20 --out "$o" 2>/dev/null
out=$(cat "$o")
assert_contains "only-in-A survives" "$out" "Only on next branch."
assert_contains "only-in-B survives" "$out" "Only in compare."
assert_eq "in-both appears once" 1 "$(printf '%s\n' "$out" | grep -c '^- In both')"
assert_not_contains "PR outside the release is excluded" "$out" "Released later"

# skeleton headings must match lib.sh (the generator uses lib.sh)
. "$SCRIPTS/lib.sh"
for s in $SECTIONS; do
  got=$(grep -A1 -xF "$(section_marker "$s")" "$REPO_ROOT/android-agent-next.mdx" | tail -1)
  assert_eq "skeleton heading for $s matches lib.sh" "## $(section_heading "$s")" "$got"
done

finish
