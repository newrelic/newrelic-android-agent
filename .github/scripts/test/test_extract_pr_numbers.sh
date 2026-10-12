#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
assert_eq "extracts unique ascending PR numbers" "618
639
646" "$("$SCRIPTS/extract-pr-numbers.sh" < "$FIXTURES/generated-notes.txt")"
assert_eq "no PRs gives empty output" "" "$(printf 'nothing here\n' | "$SCRIPTS/extract-pr-numbers.sh")"
finish
