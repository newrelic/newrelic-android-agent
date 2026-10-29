#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
u=https://github.com/newrelic/newrelic-react-native-agent/pull
n="$tmp/next.mdx"; o="$tmp/out.mdx"; fl="$tmp/flags.txt"
cp "$SKELETON" "$n"
a() { "$SCRIPTS/append-bullet.sh" "$n" "$@"; }
gen() { "$SCRIPTS/generate-docs.sh" --subject "React Native agent" --next "$n" --version 7.8.4 --date 2026-10-20 --out "$o" "$@" 2>/dev/null; }

a bug "* Fixed a crash on \`Foo\` startup. ([#1]($u/1)) {/* short: Fixed a crash on Foo startup */}"
a bug "* Fixed Bob's bug. ([#2]($u/2)) {/* short: Fixed Bob's bug */} {/* flag: unlabelled */}"
a feature "* Added offline storage. ([#3]($u/3)) {/* short: Added offline storage */}"
# a person adds this one by editing the file, which is why it bypasses append-bullet.sh (that refuses link-less bullets)
awk '{ print } /^## Improvements and enhancements$/ { print ""; print "* Hand written bullet with no link or short." }' "$n" > "$n.tmp" && mv "$n.tmp" "$n"

gen --download-link https://www.npmjs.com/package/newrelic-react-native-agent/v/1.9.3 --flags-out "$fl"
out=$(cat "$o")

assert_contains "subject is the React Native agent" "$out" "
subject: React Native agent
"
assert_not_contains "no category line (the template drops it for single-track subjects)" "$out" "category:"
assert_contains "downloadLink emitted" "$out" "downloadLink: 'https://www.npmjs.com/package/newrelic-react-native-agent/v/1.9.3'"
assert_contains "features array populated" "$out" "features:
  - 'Added offline storage'"
assert_contains "bugs array populated and YAML-quoted" "$out" "bugs:
  - 'Fixed a crash on Foo startup'
  - 'Fixed Bob''s bug'"
assert_contains "short derived for hand-written bullet" "$out" "  - 'Hand written bullet with no link or short'"
assert_contains "empty security array present" "$out" "security: []"
assert_not_contains "empty security section deleted" "$out" "### Security updates"
assert_not_contains "comments stripped from body" "$out" "{/*"
assert_contains "bug bullet in body with dash and link" "$out" "- Fixed a crash on \`Foo\` startup. ([#1]($u/1))"
assert_not_contains "no Support statement for React Native" "$out" "Support statement"
assert_not_contains "skeleton downloadLink placeholder is not copied" "$out" "X.Y.Z"
printf '%s\n' '{/* release-note:bug */}' "* Fixed x. ([#1]($u/1))" '{/* release-note:end */}' '## Kept' > "$tmp/tail.mdx"
"$SCRIPTS/generate-docs.sh" --subject "React Native agent" --next "$tmp/tail.mdx" --version 7.8.4 --date 2026-10-20 --out "$tmp/tail.out" 2>/dev/null
assert_contains "text after the end marker is still kept" "$(cat "$tmp/tail.out")" "- Fixed x. ([#1]($u/1))

## Kept"
assert_eq "flags file lists flagged PRs" "#2 unlabelled" "$(cat "$fl")"
assert_eq "section order feature before bug" "1" "$(printf '%s\n' "$out" | grep -n -e '^### ' | sed -n '1p;2p;3p;4p' | awk -F: '/New features/{f=$1} /Bug fixes/{b=$1} END{print (f<b)}')"

# ---- the docs-website template layout ----
assert_contains "sections use the template's ### headings" "$out" "
### Bug fixes
"
assert_eq "no ## section headings" "" "$(printf '%s\n' "$out" | grep -E '^## (New features|Improvements and enhancements|Bug fixes|Security updates)$')"
line_of() { printf '%s\n' "$out" | grep -n -m1 -E "$1" | cut -d: -f1; }
[ "$(line_of '^downloadLink:')" -gt "$(line_of '^security:')" ] && r=after || r=before
assert_eq "downloadLink comes after the four lists, as in the template" after "$r"
assert_eq "the four lists keep the template order" "features enhancements bugs security" \
  "$(printf '%s\n' "$out" | grep -oE '^(features|enhancements|bugs|security):' | tr -d : | tr '\n' ' ' | sed 's/ $//')"

# ---- every summary is recomputed from the bullet text (stored shorts may be old 120-character cuts) ----
cp "$SKELETON" "$n"
a bug "* Fixed the opt-in Swift Package Manager integration (\`NEWRELIC_USE_SPM=1\`): apps no longer crash at launch with \`Library not loaded: @rpath/NewRelic.framework/NewRelic\`, archives no longer fail on a duplicate xcframework signature, and the iOS agent resolves to the newest 7.7.x release. ([#334]($u/334)) {/* short: Fixed the opt-in Swift Package Manager integration (NEWRELIC_USE_SPM=1): apps no longer crash at launch with Library not */}"
a enhancement "* Handled &#123;braces&#125; and &lt;tags&gt;. ([#5]($u/5)) {/* short: Handled braces and tags */}"
gen; out=$(cat "$o")
assert_contains "a stored fragment is replaced by the recomputed summary" "$out" "bugs:
  - 'Fixed the opt-in Swift Package Manager integration (NEWRELIC_USE_SPM=1)'"
assert_not_contains "the stored fragment is gone" "$out" "Library not'"
assert_contains "MDX entities don't leak into a summary" "$out" "  - 'Handled braces and tags'"

gen; assert_not_contains "downloadLink key omitted when no link" "$(cat "$o")" "downloadLink"

# nothing to publish
cp "$SKELETON" "$n"
"$SCRIPTS/generate-docs.sh" --subject "React Native agent" --next "$n" --version 7.8.4 --date 2026-10-20 --out "$o" 2>/dev/null
assert_eq "no bullets exits 3" 3 "$?"

# reconciliation: only-in-A, only-in-B, in-both appear once
cp "$SKELETON" "$n"
a bug "* Only on next branch. ([#10]($u/10)) {/* short: Only on next */}"
a bug "* In both. ([#11]($u/11)) {/* short: In both */}"
a bug "* In both again. ([#11]($u/11)) {/* short: Duplicate */}"
a bug "* Only in compare. ([#12]($u/12)) {/* short: Only in compare */}"
a bug "* Released later, not in this release. ([#99]($u/99)) {/* short: Later */}"
"$SCRIPTS/filter-bullets.sh" "$n" keep 10 11 12 > "$tmp/release-src.mdx"
"$SCRIPTS/generate-docs.sh" --subject "React Native agent" --next "$tmp/release-src.mdx" --version 7.8.4 --date 2026-10-20 --out "$o" 2>/dev/null
out=$(cat "$o")
assert_contains "only-in-A survives" "$out" "Only on next branch."
assert_contains "only-in-B survives" "$out" "Only in compare."
assert_eq "in-both appears once" 1 "$(printf '%s\n' "$out" | grep -c '^- In both')"
assert_not_contains "PR outside the release is excluded" "$out" "Released later"

# skeleton headings must match lib.sh (the generator uses lib.sh)
. "$SCRIPTS/lib.sh"
for s in $SECTIONS; do
  got=$(grep -A1 -xF "$(section_marker "$s")" "$SKELETON" | tail -1)
  assert_eq "skeleton heading for $s matches lib.sh" "## $(section_heading "$s")" "$got"
done

# ---- malformed input and bad arguments are refused, never silently mishandled ----
g() { "$SCRIPTS/generate-docs.sh" --subject "React Native agent" "$@" 2>/dev/null; }
ok_args=(--version 7.8.4 --date 2026-10-20)
bul="* Fixed x. ([#1]($u/1)) {/* short: Fixed x */}"
skel="$SKELETON"

printf '%s\n' '---' '---' '{/* release-note:bogus */}' "$bul" '{/* release-note:end */}' 'Support' > "$tmp/bogus.mdx"
rm -f "$o"; g --next "$tmp/bogus.mdx" "${ok_args[@]}" --out "$o"; rc=$?
assert_eq "unknown section marker exits 2" 2 "$rc"
[ -e "$o" ] && r=written || r=none; assert_eq "unknown section marker writes no output" none "$r"
printf '%s\n' '{/* release-note:../../../tmp/escape */}' "$bul" '{/* release-note:end */}' > "$tmp/escape.mdx"
g --next "$tmp/escape.mdx" "${ok_args[@]}" --out "$o"; assert_eq "path-like section marker exits 2" 2 "$?"
printf '%s\n' "$bul" '{/* release-note:bug */}' '{/* release-note:end */}' > "$tmp/stray.mdx"
g --next "$tmp/stray.mdx" "${ok_args[@]}" --out "$o"; assert_eq "bullet before any marker exits 2" 2 "$?"
grep -vxF '{/* release-note:end */}' "$skel" > "$tmp/noend.mdx"
g --next "$tmp/noend.mdx" "${ok_args[@]}" --out "$o"; assert_eq "missing end marker exits 2" 2 "$?"

cp "$skel" "$tmp/crlf.mdx"; "$SCRIPTS/append-bullet.sh" "$tmp/crlf.mdx" bug "$bul"
sed 's/$/\r/' "$tmp/crlf.mdx" > "$tmp/crlf2.mdx"
g --next "$tmp/crlf2.mdx" "${ok_args[@]}" --out "$o"; assert_eq "CRLF input is accepted" 0 "$?"
assert_contains "CRLF input still yields the bullet" "$(cat "$o")" "- Fixed x."
assert_not_contains "CRLF input leaves no carriage returns in the output" "$(od -c "$o" | grep -c '\\r' || true)" "1"

g --next "$tmp/crlf.mdx" --version 7.8 --date 2026-10-20 --out "$o"; assert_eq "short version exits 2" 2 "$?"
g --next "$tmp/crlf.mdx" --version "$(printf '7.8.4\nevil: 1')" --date 2026-10-20 --out "$o"; assert_eq "version with a newline exits 2" 2 "$?"
g --next "$tmp/crlf.mdx" --version 7.8.4 --date "2026-10-20'" --out "$o"; assert_eq "bad date exits 2" 2 "$?"
g --next "$tmp/crlf.mdx" "${ok_args[@]}" --download-link http://x --out "$o"; assert_eq "non-https link exits 2" 2 "$?"
g --next "$tmp/crlf.mdx" "${ok_args[@]}" --download-link "https://x/it's" --out "$o"; assert_eq "link with a quote exits 2" 2 "$?"
g --next "$tmp/crlf.mdx" "${ok_args[@]}" --out; assert_eq "option with no value exits 2" 2 "$?"
g --next "$tmp/crlf.mdx" "${ok_args[@]}" --bogus x --out "$o"; assert_eq "unknown option exits 2" 2 "$?"
g --next "$tmp/missing.mdx" "${ok_args[@]}" --out "$o"; assert_eq "missing input file exits 2" 2 "$?"
g "${ok_args[@]}" --out "$o"; assert_eq "missing --next exits 2" 2 "$?"

g --next "$tmp/crlf.mdx" "${ok_args[@]}" --out "$o"
last2=$(tail -c2 "$o" | od -An -tx1 | tr -d ' \n')
[ "$last2" != 0a0a ] && [ "${last2#??}" = 0a ] && r=yes || r=no
assert_eq "output ends with exactly one newline" yes "$r"

# Frontmatter must stay valid YAML for hostile short forms (checked with a real parser when ruby exists).
if command -v ruby >/dev/null 2>&1; then
  : > "$tmp/hostile.txt"
  i=100
  for sh in '- dash' 'a: b' '# hash' 'yes' 'null' '123' "it's" 'back\slash' '[x]' '---' 'caf'$'\303\251'; do
    i=$((i + 1))
    # Summaries come from the bullet text now, so the hostile value is the text.
    printf '%s\n' "* $sh ([#$i]($u/$i)) {/* short: H$i */}" >> "$tmp/hostile.txt"
  done
  awk -v h="$tmp/hostile.txt" '{ print } /^## Bug fixes$/ { print ""; while ((getline line < h) > 0) print line }' "$skel" > "$tmp/hostile.mdx"
  g --next "$tmp/hostile.mdx" "${ok_args[@]}" --out "$o"
  parsed=$(ruby -E UTF-8:UTF-8 -ryaml -e 'y = YAML.load(File.read(ARGV[0]).split("---\n")[1]); puts y["bugs"].join("|"); puts y["version"].class; puts y["category"].inspect' "$o")
  assert_eq "hostile short forms round-trip through a YAML parser" "- dash|a: b|# hash|yes|null|123|it's|back\\slash|[x]|---|café
String
nil" "$parsed"
else
  echo "  skip YAML round-trip: ruby not installed"
fi

# ---- subject and tail come from the caller (the shared code has no agent built in) ----
cp "$SKELETON" "$n"; a bug "$bul"
g --next "$n" "${ok_args[@]}" --subject ".NET MAUI agent" --out "$o"
assert_contains "--subject is written to the frontmatter" "$(cat "$o")" "
subject: .NET MAUI agent
"
g --next "$n" "${ok_args[@]}" --subject "Evil: agent" --out "$o"; assert_eq "a subject with a colon exits 2" 2 "$?"
g --next "$n" "${ok_args[@]}" --subject "$(printf 'A\nversion: 9')" --out "$o"; assert_eq "a subject with a newline exits 2" 2 "$?"
"$SCRIPTS/generate-docs.sh" --next "$n" "${ok_args[@]}" --out "$o" 2>/dev/null; assert_eq "a missing --subject exits 2" 2 "$?"

printf '%s\n' '## Support statement' '' 'Update every three months.' > "$tmp/tail.txt"
g --next "$n" "${ok_args[@]}" --tail-file "$tmp/tail.txt" --out "$o"
assert_contains "--tail-file is appended after the sections" "$(cat "$o")" "- Fixed x. ([#1]($u/1))

## Support statement

Update every three months."
printf '%s\n' '{/* release-note:bug */}' "$bul" '{/* release-note:end */}' '## Stale tail from the next branch' > "$tmp/stale.mdx"
g --next "$tmp/stale.mdx" "${ok_args[@]}" --tail-file "$tmp/tail.txt" --out "$o"
assert_not_contains "--tail-file replaces the tail of --next" "$(cat "$o")" "Stale tail"
: > "$tmp/empty-tail.txt"
g --next "$tmp/stale.mdx" "${ok_args[@]}" --tail-file "$tmp/empty-tail.txt" --out "$o"
assert_not_contains "an empty --tail-file means no tail at all" "$(cat "$o")" "Stale tail"
last2=$(tail -c2 "$o" | od -An -tx1 | tr -d ' \n')
[ "$last2" != 0a0a ] && [ "${last2#??}" = 0a ] && r=yes || r=no
assert_eq "with an empty tail the output still ends with one newline" yes "$r"
g --next "$n" "${ok_args[@]}" --tail-file "$tmp/missing.txt" --out "$o"; assert_eq "a missing --tail-file exits 2" 2 "$?"

finish
