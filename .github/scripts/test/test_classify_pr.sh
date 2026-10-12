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

finish
