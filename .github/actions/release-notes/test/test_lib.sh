#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
. "$SCRIPTS/lib.sh"

assert_eq "oneline collapses newlines and runs" "a b c" "$(oneline "$(printf '  a\n\n b\t\tc  ')")"

assert_eq "mdx_escape escapes JSX outside code" 'Use &lt;T&gt; and &#123;x&#125;' "$(mdx_escape 'Use <T> and {x}')"
assert_eq "mdx_escape keeps inline code verbatim" 'Call `List<String>` and &lt;b&gt;' "$(mdx_escape 'Call `List<String>` and <b>')"
assert_eq "mdx_escape escapes everything when backticks are unbalanced" 'a ` &lt;b&gt;' "$(mdx_escape 'a ` <b>')"
assert_eq "mdx_escape leaves plain text alone" 'Fixed a crash.' "$(mdx_escape 'Fixed a crash.')"

assert_eq "plain_short strips markup and asterisks" 'Fixed crash in Foo /' "$(plain_short 'Fixed `crash` in <Foo> */')"
case "$(plain_short 'a **// b')" in *'*/'*) r=spliced ;; *) r=ok ;; esac
assert_eq "plain_short cannot splice a new */ together" ok "$r"
case "$(plain_short '*{}/ x')" in *'*/'*) r=spliced ;; *) r=ok ;; esac
assert_eq "plain_short cannot splice */ across removed braces" ok "$r"
assert_eq "yaml_quote doubles single quotes" "'it''s fine'" "$(yaml_quote "it's fine")"

assert_eq "section_from_title fix" bug "$(section_from_title 'fix: crash on start')"
assert_eq "section_from_title scoped fix" bug "$(section_from_title 'Fix(session-replay): blank frame')"
assert_eq "section_from_title feat" feature "$(section_from_title 'feat: add thing')"
assert_eq "section_from_title other defaults to enhancement" enhancement "$(section_from_title 'refactor: tidy')"

line='* Fixed a crash. ([#123](https://github.com/newrelic/newrelic-react-native-agent/pull/123)) {/* short: Fixed crash */}'
assert_eq "bullet_pr_number reads trailing link" 123 "$(printf '%s\n' "$line" | bullet_pr_number)"
assert_eq "strip_comments removes comments" '* Fixed a crash. ([#123](https://github.com/newrelic/newrelic-react-native-agent/pull/123))' "$(printf '%s' "$line" | strip_comments)"

tmp=$(mktemp); printf '%s\n' "$line" > "$tmp"
file_has_pr "$tmp" 123 && r=yes || r=no; assert_eq "file_has_pr finds 123" yes "$r"
file_has_pr "$tmp" 12 && r=yes || r=no; assert_eq "file_has_pr does not match prefix 12" no "$r"
rm -f "$tmp"

assert_eq "section_heading" "Bug fixes" "$(section_heading bug)"
assert_eq "section_array_key security" security "$(section_array_key security)"

# ---- PR details -> bullet ----
assert_eq "title_to_bullet: noun phrase is left as written" "Crash on start." "$(title_to_bullet 'fix: crash on start')"
assert_eq "title_to_bullet: known verb, Jira key stripped" "Restored alpha in colors." "$(title_to_bullet 'fix: restore alpha in colors (NR-625270)')"
assert_eq "title_to_bullet: noun-led feat" "Offline storage for Session Replay." "$(title_to_bullet 'feat: offline storage for Session Replay')"
assert_eq "title_to_bullet: scoped type" "Raised AGP support ceiling to 9.4.0." "$(title_to_bullet 'feat(gradle): raise AGP support ceiling to 9.4.0')"
assert_eq "title_to_bullet: acronyms and CamelCase survive" "ANR in HarvestTimer." "$(title_to_bullet 'fix: ANR in HarvestTimer')"
assert_eq "title_to_bullet: unknown verb left as written" "Don't start replay." "$(title_to_bullet "fix: don't start replay")"
assert_eq "title_to_bullet: no prefix" "Improved startup time." "$(title_to_bullet 'Improve startup time')"
assert_eq "title_to_bullet: unknown type just capitalises" "Tidy harvest code." "$(title_to_bullet 'refactor: tidy harvest code')"
assert_eq "title_to_bullet: bracketed Jira key" "Guarded against null." "$(title_to_bullet '[NR-639] fix: guard against null')"
assert_eq "title_to_bullet: branch-style Nr 123 removed" "Removed nr headers." "$(title_to_bullet 'Nr 382848 remove nr headers')"
assert_eq "title_to_bullet: [skip ci] removed" "Fixed stale javadoc." "$(title_to_bullet 'docs(NR-382848): fix stale javadoc [skip ci]')"
assert_eq "title_to_bullet: existing full stop kept" "Added thing." "$(title_to_bullet 'feat: add thing.')"
assert_eq "past tense: -e verbs" "Synchronized" "$(past_tense synchronize)"
assert_eq "past tense: consonant+y" "Simplified" "$(past_tense simplify)"
assert_eq "past tense: doubled consonant" "Skipped" "$(past_tense skip)"
assert_eq "past tense: doubled consonant stop" "Stopped" "$(past_tense stop)"
assert_eq "past tense: nouns are not conjugated (log)" "" "$(past_tense log)"
assert_eq "past tense: nouns are not conjugated (cache)" "" "$(past_tense cache)"
assert_eq "past tense: irregular" "Kept" "$(past_tense keep)"
assert_eq "past tense: unknown word is empty" "" "$(past_tense crash)"

assert_eq "section_from_title: bare Fix is a bug" bug "$(section_from_title 'Fix react native sourcemap endpoint')"
is_internal_title 'docs(NR-1): x' && r=yes || r=no; assert_eq "docs scope is internal" yes "$r"
is_internal_title 'chore: bump' && r=yes || r=no; assert_eq "chore is internal" yes "$r"
is_internal_title 'fix: x' && r=yes || r=no; assert_eq "fix is user-facing" no "$r"
is_internal_title 'perf: x' && r=yes || r=no; assert_eq "perf is user-facing" no "$r"
is_internal_pr 'Bump okhttp from 4.11 to 4.12' dependabot/gradle/okhttp && r=yes || r=no; assert_eq "dependabot branch is internal" yes "$r"
is_internal_pr 'fix: x' NR-1 && r=yes || r=no; assert_eq "normal PR is not internal" no "$r"
is_internal_pr 'chore: bump' NR-1 && r=yes || r=no; assert_eq "internal title type is internal" yes "$r"

body=$(printf 'Intro text\n\n## Release note\nFixed a crash when the app\nwas backgrounded.\n\n## Testing\nran it\n')
assert_eq "release note from heading joins the paragraph" "Fixed a crash when the app was backgrounded." "$(extract_release_note "$body")"
assert_eq "release note from one-line form" "Added thing." "$(extract_release_note "$(printf 'x\nRelease note: Added thing.\ny\n')")"
assert_eq "release note with notes plural and h3" "Added thing." "$(extract_release_note "$(printf '### Release Notes:\n- Added thing.\n')")"
assert_eq "release note ignores HTML comment hints" "" "$(extract_release_note "$(printf '## Release note\n<!-- Describe the change\nfor users -->\n\n## Testing\n')")"
assert_eq "release note keeps text after an inline comment" "Added thing." "$(extract_release_note "$(printf '## Release note\n<!-- hint --> Added thing.\n')")"
assert_eq "release note placeholder n/a is no note" "" "$(extract_release_note "$(printf '## Release note\nN/A\n')")"
assert_eq "release note placeholder None is no note" "" "$(extract_release_note "$(printf 'Release note: none\n')")"
assert_eq "no release note section" "" "$(extract_release_note 'just a description')"
assert_eq "release note stops at the next heading" "Added thing." "$(extract_release_note "$(printf '## Release note\nAdded thing.\n## Other\nmore\n')")"

assert_eq "short_form drops the full stop" "Fixed a crash" "$(short_form 'Fixed a crash.' 120)"
assert_eq "short_form cuts at a word boundary" "aaa bbb" "$(short_form 'aaa bbb ccc ddd' 9)"

assert_eq "section_from_title: security type" security "$(section_from_title 'security: reject cleartext uploads')"
assert_eq "section_from_title: scoped security type" security "$(section_from_title 'security(net): pin certs')"
skip_reason 'fix: x' '' NR-1 >/dev/null && r=skip || r=keep; assert_eq "skip_reason: normal PR is kept" keep "$r"
assert_eq "skip_reason: release branch" "release branch" "$(skip_reason 'anything' '' release/7.8.4)"
assert_eq "skip_reason: release chore" "release chore" "$(skip_reason 'chore: update gradle.properties' '' NR-1)"
assert_eq "skip_reason: backport" "bridge or revert PR" "$(skip_reason 'Backport main to develop' '' NR-1)"
assert_eq "skip_reason: revert" "bridge or revert PR" "$(skip_reason 'Revert "x"' '' NR-1)"
assert_eq "skip_reason: skip-docs" "[skip-docs]" "$(skip_reason 'fix: x' 'text [skip-docs]' NR-1)"

# ---- review findings: regression tests ----
f=$(mktemp)
printf '%s\n' '* Fixed x. ([#7](https://github.com/o/r/pull/7)) {/* short: see o/r/pull/9) */}' > "$f"
file_has_pr "$f" 7 && r=yes || r=no; assert_eq "file_has_pr reads the real link, not the short comment (7)" yes "$r"
file_has_pr "$f" 9 && r=yes || r=no; assert_eq "file_has_pr ignores a pull link inside the short comment (9)" no "$r"
assert_eq "bullet_pr_number ignores comments" 7 "$(cat "$f" | bullet_pr_number)"
rm -f "$f"

assert_eq "ANR is not mistaken for a Jira key (space form)" "ANR 30 seconds timeout." "$(title_to_bullet 'fix: ANR 30 seconds timeout')"
assert_eq "ANR is not mistaken for a Jira key (dash form)" "ANR-5 crash." "$(title_to_bullet 'fix: ANR-5 crash')"
assert_eq "strip_title_noise: real key mid-title" "fix: crash" "$(strip_title_noise 'fix: crash NR-123')"
assert_eq "strip_title_noise: key at the start" "remove headers" "$(strip_title_noise 'NR-123: remove headers')"
assert_eq "title_to_bullet: only a Jira key leaves nothing" "" "$(title_to_bullet 'NR-123')"
assert_eq "title_to_bullet: unknown prefix word is kept" "Android: crash on start." "$(title_to_bullet 'Android: crash on start')"
assert_eq "title_to_bullet: space before the scope" "Guarded x." "$(title_to_bullet 'fix (NR-1): guard x')"
assert_eq "title_to_bullet: noun titles are not conjugated" "Log spam on retry." "$(title_to_bullet 'fix: log spam on retry')"
assert_eq "title_to_bullet: report as a verb" "Reported 5G for NR cellular connections." "$(title_to_bullet 'feat: report 5G for NR cellular connections')"

assert_eq "skip_reason: chore(release) with a scope" "release chore" "$(skip_reason 'chore(release): 7.8.4' '' NR-1)"
assert_eq "skip_reason: revert with a type prefix" "bridge or revert PR" "$(skip_reason 'revert: bad change' '' NR-1)"
assert_eq "skip_reason: Merge branch" "bridge or revert PR" "$(skip_reason "Merge branch 'main' into develop" '' NR-1)"
skip_reason 'Merge duplicate harvest requests' '' NR-1 >/dev/null && r=skip || r=keep; assert_eq "skip_reason: an ordinary title starting Merge is kept" keep "$r"

assert_eq "extract_release_note: placeholder with a trailing period" "" "$(extract_release_note 'Release note: N/A.')"

assert_eq "short_form: cut landing on a word end keeps the word" "aaa bbb" "$(short_form 'aaa bbb ccc' 7)"
assert_eq "short_form: a single long word is cut" "aaaaa" "$(short_form 'aaaaaaaaaa' 5)"
assert_eq "short_form: a cut inside a multibyte character drops the partial character" "caf" "$(short_form "$(printf 'caf\303\251 au lait')" 4)"
assert_eq "short_form: a cut after a whole multibyte character keeps it" "$(printf 'caf\303\251')" "$(short_form "$(printf 'caf\303\251 au lait')" 5)"
printf '%s' "$(short_form "$(printf 'caf\303\251 au lait')" 4)" | iconv -f UTF-8 -t UTF-8 > /dev/null 2>&1 && r=valid || r=invalid
assert_eq "short_form: result is always valid UTF-8" valid "$r"

assert_eq "ensure_period adds one" "Done." "$(ensure_period 'Done')"
assert_eq "ensure_period keeps ? and !" "Done?" "$(ensure_period 'Done?')"
assert_eq "yaml_quote of empty" "''" "$(yaml_quote '')"
is_valid_section bug && r=yes || r=no; assert_eq "is_valid_section accepts bug" yes "$r"
is_valid_section nonsense && r=yes || r=no; assert_eq "is_valid_section rejects junk" no "$r"
assert_eq "section_marker" '{/* release-note:bug */}' "$(section_marker bug)"

# ---- non-ASCII text, control characters and comment smuggling (review of build-bullet) ----
assert_eq "ucfirst: emoji first character is left alone" "$(printf '\360\237\230\200 foo')" "$(ucfirst "$(printf '\360\237\230\200 foo')")"
assert_eq "ucfirst: non-ASCII letter is left alone, rest intact" "$(printf '\303\274ber')" "$(ucfirst "$(printf '\303\274ber')")"
assert_eq "ucfirst: ASCII lower case is raised" "Hello" "$(ucfirst hello)"
assert_eq "ucfirst: empty" "" "$(ucfirst '')"
assert_eq "char_length counts characters, not bytes" 4 "$(char_length "$(printf 'caf\303\251')")"
assert_eq "char_length: emoji is one character" 3 "$(char_length "$(printf 'a\360\237\230\200b')")"
assert_eq "oneline: vertical tab and form feed become spaces" "a b c" "$(oneline "$(printf 'a\vb\fc')")"
assert_eq "oneline: other control characters are dropped" "ab" "$(oneline "$(printf 'a\001\033b')")"
assert_eq "defuse_comments breaks a comment opener" 'x { /* y */} z' "$(defuse_comments 'x {/* y */} z')"
assert_eq "defuse_comments leaves other text alone" 'a {b} * / c' "$(defuse_comments 'a {b} * / c')"
assert_eq "title_to_bullet: emoji-first title survives" "$(printf '\360\237\220\233 handle crash.')" "$(title_to_bullet "$(printf '\360\237\220\233 handle crash')")"
assert_eq "section_from_title does not choke on emoji" enhancement "$(section_from_title "$(printf '\360\237\230\200 foo')")"

assert_eq "skip_reason: a sync PR from main" "bridge or revert PR" "$(skip_reason 'Sync it' '' main)"
assert_eq "skip_reason: a sync PR from develop" "bridge or revert PR" "$(skip_reason 'Sync it' '' develop)"
assert_eq "skip_reason: a release-please PR" "release PR" "$(skip_reason 'chore(develop): release 7.8.4' '' release-please--branches--develop)"

# ---- head-branch rules only apply to same-repo PRs (final review) ----
assert_eq "skip_reason: a same-repo PR from main is a sync PR" "bridge or revert PR" "$(skip_reason 'fix: x' '' main false)"
skip_reason 'fix: x' '' main true > /dev/null && r=skip || r=keep; assert_eq "skip_reason: a FORK PR from its own main is an ordinary change" keep "$r"
skip_reason 'fix: x' '' develop true > /dev/null && r=skip || r=keep; assert_eq "skip_reason: a fork PR from develop is an ordinary change" keep "$r"
skip_reason 'fix: x' '' release/1.0 true > /dev/null && r=skip || r=keep; assert_eq "skip_reason: a fork PR from release/1.0 is an ordinary change" keep "$r"
assert_eq "skip_reason: title rules still apply to fork PRs" "bridge or revert PR" "$(skip_reason 'Revert something' '' main true)"
assert_eq "skip_reason: [skip-docs] still applies to fork PRs" "[skip-docs]" "$(skip_reason 'fix: x' 'a [skip-docs]' main true)"

# ---- note_summary: the frontmatter summary of a bullet ----
filler() { local s="" i; for ((i = 0; i < $1; i++)); do s="$s word"; done; printf '%s' "$s"; } # " word" x N
ELL=$(printf '\342\200\246')
assert_eq "note_summary: a short text is kept whole (the combined native bump)" \
  "Updated the Native Android agent to version 7.8.4. Updated the Native iOS agent to version 7.7.9" \
  "$(note_summary 'Updated the Native Android agent to version 7.8.4. Updated the Native iOS agent to version 7.7.9.')"
assert_eq "note_summary: the trailing full stop is dropped" "Fixed a crash" "$(note_summary 'Fixed a crash.')"
long="Fixed a crash on the harvest thread. The agent copied its internal session attributes$(filler 40) during a harvest."
assert_eq "note_summary: a long text gives its first sentence" "Fixed a crash on the harvest thread" "$(note_summary "$long")"
spm="Fixed the opt-in Swift Package Manager integration (NEWRELIC_USE_SPM=1): apps no longer crash at launch with Library not loaded: @rpath/NewRelic.framework/NewRelic, archives no longer fail on a duplicate xcframework signature, and the iOS agent resolves to the newest 7.7.x release."
assert_eq "note_summary: one long sentence gives what comes before its first colon" \
  "Fixed the opt-in Swift Package Manager integration (NEWRELIC_USE_SPM=1)" "$(note_summary "$spm")"
ver="Updated the agent to 7.8.4 e.g. for apps on iOS 15 and later, which$(filler 40) more"
r=$(note_summary "$ver")
assert_contains "note_summary: version dots and e.g. do not end a sentence" "$r" "Updated the agent to 7.8.4 e.g. for apps on iOS 15 and later, which word"
assert_eq "note_summary: with no sentence end or colon it is cut with an ellipsis" "$ELL" "${r: -3}"
dc="Fixed a crash in SessionAttributeManager::generateJSONObject on the harvest thread, which$(filler 40) more"
r=$(note_summary "$dc")
assert_contains "note_summary: a double colon is not a clause end" "$r" "Fixed a crash in SessionAttributeManager::generateJSONObject on the harvest thread, which"
cut=$(note_summary "aaa$(filler 70)")
body=${cut%"$ELL"}
[ "${#body}" -le 200 ] && r=ok || r="too long (${#body})"
assert_eq "note_summary: the word cut keeps at most 200 bytes before the ellipsis" ok "$r"
assert_eq "note_summary: the word cut ends on a whole word" "word" "${body##* }"

finish
