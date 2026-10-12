#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tags=$(printf '%s\n' 7.8.1 7.8.3 7.8.2 7.10.0 7.9.0 nightly-2026-10-01 7.8.4-rc1 7.8.4)
p() { printf '%s\n' "$tags" | "$SCRIPTS/previous-tag.sh" "$1"; }

assert_eq "previous of 7.8.4 skips rc and nightly" 7.8.3 "$(p 7.8.4)"
assert_eq "sorts numerically, not lexically (7.10.0 > 7.9.0)" 7.9.0 "$(p 7.10.0)"
assert_eq "works when VERSION is not tagged yet" 7.10.0 "$(p 7.11.0)"
p 7.8.1 >/dev/null; assert_eq "lowest tag has no previous" 1 "$?"

finish
