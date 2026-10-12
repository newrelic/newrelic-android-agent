#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
f="$tmp/next.mdx"
u=https://github.com/newrelic/newrelic-android-agent/pull
cat > "$f" <<EOT
## Bug fixes

* One ([#1]($u/1))
* Two ([#2]($u/2))
* Hand written, no link

{/* release-note:end */}
EOT

assert_eq "keep 1 keeps #1 and hand-written bullet" \
  "$(printf '%s\n' '## Bug fixes' '' "* One ([#1]($u/1))" '* Hand written, no link' '' '{/* release-note:end */}')" \
  "$("$SCRIPTS/filter-bullets.sh" "$f" keep 1)"
assert_eq "drop 1 leaves only #2" \
  "$(printf '%s\n' '## Bug fixes' '' "* Two ([#2]($u/2))" '' '{/* release-note:end */}')" \
  "$("$SCRIPTS/filter-bullets.sh" "$f" drop 1)"
assert_eq "keep with no PRs keeps only unlinked bullets" \
  "$(printf '%s\n' '## Bug fixes' '' '* Hand written, no link' '' '{/* release-note:end */}')" \
  "$("$SCRIPTS/filter-bullets.sh" "$f" keep)"
"$SCRIPTS/filter-bullets.sh" "$f" sideways 2>/dev/null; assert_eq "bad mode exits 2" 2 "$?"

# ---- review findings ----
"$SCRIPTS/filter-bullets.sh" "$tmp/missing.mdx" keep 1 > /dev/null 2>&1; assert_eq "a missing FILE exits 2" 2 "$?"

cat > "$f" <<EOT
## New features

* Twelve ([#12]($u/12))
* One twenty three ([#123]($u/123))

## Bug fixes

* Seven ([#7]($u/7))
*emphasis* is not a bullet
**bold** is not a bullet either

{/* release-note:end */}
EOT
out=$("$SCRIPTS/filter-bullets.sh" "$f" keep 12)
assert_contains "keep 12 keeps #12" "$out" "Twelve"
assert_not_contains "keep 12 does not keep #123" "$out" "One twenty three"
assert_not_contains "keep 12 does not keep #7" "$out" "Seven"
assert_contains "lines starting with * but not '* ' pass through (emphasis)" "$out" "*emphasis* is not a bullet"
assert_contains "lines starting with ** pass through" "$out" "**bold** is not a bullet either"
assert_contains "headings in other sections pass through" "$out" "## Bug fixes"
total=$(grep -c '^\* ' "$f")
kept=$("$SCRIPTS/filter-bullets.sh" "$f" keep 12 7 | grep -c '^\* ')
dropped=$("$SCRIPTS/filter-bullets.sh" "$f" drop 12 7 | grep -c '^\* ')
assert_eq "keep and drop partition the bullets" "$total" "$((kept + dropped))"

printf '* Last line has no newline ([#1](%s/1))' "$u" > "$f"
assert_contains "a last line without a newline is still processed" "$("$SCRIPTS/filter-bullets.sh" "$f" keep 1)" "Last line has no newline"
assert_eq "...and dropped when asked" "" "$("$SCRIPTS/filter-bullets.sh" "$f" drop 1)"

printf '* CRLF bullet ([#1](%s/1))\r\n* other ([#2](%s/2))\r\n' "$u" "$u" > "$f"
assert_eq "CRLF bullets are filtered by PR" 1 "$("$SCRIPTS/filter-bullets.sh" "$f" keep 1 | grep -c 'CRLF bullet')"

printf '* bad \377 byte ([#5](%s/5))\n* ok ([#6](%s/6))\n' "$u" "$u" > "$f"
assert_eq "invalid UTF-8 does not confuse the PR number (drop 5)" 1 "$("$SCRIPTS/filter-bullets.sh" "$f" drop 5 | grep -c '^\* ')"
assert_eq "invalid UTF-8 does not confuse the PR number (keep 5)" 1 "$("$SCRIPTS/filter-bullets.sh" "$f" keep 5 | grep -c 'bad')"

finish
