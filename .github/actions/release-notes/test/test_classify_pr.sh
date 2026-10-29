#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
c() { "$SCRIPTS/classify-pr.sh" "$@"; }

assert_eq "single feature label" feature "$(c bug-report release-note:feature)"
assert_eq "labels on stdin" security "$(printf 'x\nrelease-note:security\n' | c)"
c documentation >/dev/null; assert_eq "no release-note label exits 10" 10 "$?"
c >/dev/null </dev/null; assert_eq "no labels at all exits 10" 10 "$?"
out=$(c release-note:bug release-note:feature); rc=$?
assert_eq "two labels exits 11" 11 "$rc"
assert_eq "two labels prints nothing" "" "$out"
c release-note:other >/dev/null; assert_eq "unknown release-note label exits 10" 10 "$?"

# ---- review findings ----
assert_eq "single enhancement label" enhancement "$(c release-note:enhancement)"
assert_eq "single bug label" bug "$(c release-note:bug)"
c release-note:bugfix > /dev/null; assert_eq "a suffix is not matched" 10 "$?"
c xrelease-note:bug > /dev/null; assert_eq "a prefix is not matched" 10 "$?"
c Release-Note:Bug > /dev/null; assert_eq "matching is case-sensitive" 10 "$?"
printf 'release-note:bug\nrelease-note:feature\n' | c > /dev/null; assert_eq "two labels on stdin exit 11" 11 "$?"
assert_eq "an unknown release-note label next to a real one is ignored" bug "$(c release-note:other release-note:bug)"
printf 'release-note:bug\r\n' | c > /dev/null; assert_eq "a CRLF-terminated label is not recognised" 10 "$?"

finish
