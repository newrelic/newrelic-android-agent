#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tags=$(printf '%s\n' 7.8.1 7.8.3 7.8.2 7.10.0 7.9.0 nightly-2026-10-01 7.8.4-rc1 7.8.4)
p() { printf '%s\n' "$tags" | "$SCRIPTS/previous-tag.sh" "$1"; }

assert_eq "previous of 7.8.4 skips rc and nightly" 7.8.3 "$(p 7.8.4)"
assert_eq "sorts numerically, not lexically (7.10.0 > 7.9.0)" 7.9.0 "$(p 7.10.0)"
assert_eq "works when VERSION is not tagged yet" 7.10.0 "$(p 7.11.0)"
p 7.8.1 >/dev/null; assert_eq "lowest tag has no previous" 1 "$?"

# ---- review findings ----
assert_eq "patch field is compared numerically (7.8.10 > 7.8.9)" 7.8.9 "$(printf '%s\n' 7.8.8 7.8.9 7.8.10 | "$SCRIPTS/previous-tag.sh" 7.8.10)"
assert_eq "major field is compared numerically (10.0.0 > 9.0.0)" 9.0.0 "$(printf '%s\n' 9.0.0 8.5.0 | "$SCRIPTS/previous-tag.sh" 10.0.0)"
out=$(printf '%s\n' 7.8.1 7.8.2 | "$SCRIPTS/previous-tag.sh" 7.0.0); rc=$?
assert_eq "VERSION below every tag exits 1" 1 "$rc"
assert_eq "VERSION below every tag prints nothing" "" "$out"
out=$(printf '' | "$SCRIPTS/previous-tag.sh" 7.8.4); rc=$?
assert_eq "empty tag list exits 1" 1 "$rc"
assert_eq "empty tag list prints nothing" "" "$out"
assert_eq "duplicate tags do not change the answer" 7.8.2 "$(printf '%s\n' 7.8.2 7.8.2 7.8.1 | "$SCRIPTS/previous-tag.sh" 7.8.3)"
assert_eq "a v-prefixed tag is ignored" 7.8.2 "$(printf '%s\n' v7.8.3 7.8.2 | "$SCRIPTS/previous-tag.sh" 7.8.4)"
assert_eq "CRLF-terminated tags are ignored, not misparsed" 7.8.1 "$(printf '7.8.2\r\n7.8.1\n' | "$SCRIPTS/previous-tag.sh" 7.8.4)"

# Two spellings of the same number collapse under sort -u. That must be an error, never "exit 0, nothing".
out=$(printf '%s\n' 7.08.1 7.8.1 7.7.0 | "$SCRIPTS/previous-tag.sh" 7.8.1); rc=$?
[ "$rc" -ne 0 ] && r=failed || r=passed; assert_eq "a collapsed VERSION is an error" failed "$r"
assert_eq "a collapsed VERSION prints nothing" "" "$out"

for bad in '' '7.8' 'v7.8.4' '7.8.4-SNAPSHOT' '7.8.4 ' "$(printf '7.8.4\nx')"; do
  printf '%s\n' 7.8.3 | "$SCRIPTS/previous-tag.sh" "$bad" > /dev/null 2>&1; rc=$?
  assert_eq "invalid VERSION '$(printf '%s' "$bad" | tr '\n' '~')' exits 2" 2 "$rc"
done

# A long tag list must not trip SIGPIPE under pipefail.
many=$(awk 'BEGIN { for (i = 1; i <= 30000; i++) printf "1.%d.0\n", i }')
out=$(printf '%s\n' "$many" | "$SCRIPTS/previous-tag.sh" 1.100.0); rc=$?
assert_eq "30000 tags: exits 0" 0 "$rc"
assert_eq "30000 tags: right answer" 1.99.0 "$out"

finish
