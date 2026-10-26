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

# ---- --prefix: React Native and the hybrids tag v1.9.2, Android and MAUI tag 7.8.3 ----
vtags=$(printf '%s\n' v1.9.0 v1.9.1 v1.9.2 1.9.5 nightly-2026-10-01 v1.9.3-rc1 release-1.9.2 V1.9.2)
pp() { printf '%s\n' "$vtags" | "$SCRIPTS/previous-tag.sh" --prefix "$@"; }
assert_eq "--prefix v answers with the prefix kept" v1.9.2 "$(pp v 1.9.3)"
assert_eq "--prefix v ignores bare, nightly, rc, other-prefix and upper-case tags" v1.9.1 "$(pp v 1.9.2)"
assert_eq "--prefix '' is the same as no prefix" 1.9.5 "$(pp '' 1.9.9)"
assert_eq "--prefix '' ignores v tags" "" "$(pp '' 1.9.4 2>/dev/null)"
pp v 1.9.0 > /dev/null; assert_eq "--prefix v: lowest tag has no previous" 1 "$?"
pp v1 1.9.3 > /dev/null 2>&1; assert_eq "a prefix with a digit exits 2" 2 "$?"
pp 'v.*' 1.9.3 > /dev/null 2>&1; assert_eq "a prefix with regex characters exits 2" 2 "$?"
printf '%s\n' v1.9.2 | "$SCRIPTS/previous-tag.sh" --prefix > /dev/null 2>&1; assert_eq "--prefix with no value exits 2" 2 "$?"
pp v v1.9.3 > /dev/null 2>&1; assert_eq "VERSION is bare even with --prefix" 2 "$?"

# ---- release-tags.sh: the already-released check ----
rt() { printf '%s\n' "$vtags" | "$SCRIPTS/release-tags.sh" "$@"; }
assert_eq "release-tags --prefix v keeps only v-release tags" "v1.9.0 v1.9.1 v1.9.2" "$(rt --prefix v | tr '\n' ' ' | sed 's/ $//')"
assert_eq "release-tags with no prefix keeps only bare release tags" "1.9.5" "$(rt)"
assert_eq "release-tags with nothing matching prints nothing and exits 0" ":0" "$(printf 'nightly-1\n' | "$SCRIPTS/release-tags.sh" --prefix v; echo ":$?")"
printf '' | "$SCRIPTS/release-tags.sh" --prefix 1 > /dev/null 2>&1; assert_eq "release-tags with a bad prefix exits 2" 2 "$?"

finish
