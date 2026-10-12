#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
assert_eq "extracts unique ascending PR numbers" "618
639
646" "$("$SCRIPTS/extract-pr-numbers.sh" < "$FIXTURES/generated-notes.txt")"
out=$(printf 'nothing here\n' | "$SCRIPTS/extract-pr-numbers.sh"); rc=$?
assert_eq "no PRs gives empty output" "" "$out"
assert_eq "no PRs is not an error" 0 "$rc"
assert_eq "duplicates collapse" "5" "$(printf '/pull/5\n/pull/5\n' | "$SCRIPTS/extract-pr-numbers.sh")"
assert_eq "order is numeric, not lexical (9 before 10)" "9
10" "$(printf '/pull/10\n/pull/9\n' | "$SCRIPTS/extract-pr-numbers.sh")"
assert_eq "a New Contributors line counts" "700" "$(printf '* @x made their first contribution in https://github.com/o/r/pull/700\n' | "$SCRIPTS/extract-pr-numbers.sh")"
assert_eq "/pull/N/files is read as PR N" "12" "$(printf 'https://github.com/o/r/pull/12/files\n' | "$SCRIPTS/extract-pr-numbers.sh")"
assert_eq "the compare line contributes nothing" "" "$(printf '**Full Changelog**: https://github.com/o/r/compare/7.8.2...7.8.3\n' | "$SCRIPTS/extract-pr-numbers.sh")"
assert_eq "CRLF input works" "3" "$(printf 'https://github.com/o/r/pull/3\r\n' | "$SCRIPTS/extract-pr-numbers.sh")"
finish
