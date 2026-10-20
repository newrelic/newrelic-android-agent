#!/usr/bin/env bash
# shellcheck shell=bash disable=SC2034
# Tiny assertion helpers for the release-notes script tests. Source from a test_*.sh file.

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SCRIPTS="$(cd "$TEST_DIR/../scripts" && pwd)"
FIXTURES="$TEST_DIR/fixtures"
AGENTS="$TEST_DIR/agents"
# The skeleton most tests start from. It has nothing after the end marker; tests of the tail
# (Android's Support statement) use $AGENTS/android instead.
SKELETON="$AGENTS/react-native/react-native-agent-next.mdx"
PASS=0
FAILN=0

assert_eq() { # name expected actual
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1)); printf '  ok   %s\n' "$1"
  else
    FAILN=$((FAILN + 1)); printf '  FAIL %s\n    expected: %s\n    actual:   %s\n' "$1" "$2" "$3"
  fi
}

assert_contains() { # name haystack needle
  case "$2" in
    *"$3"*) PASS=$((PASS + 1)); printf '  ok   %s\n' "$1" ;;
    *) FAILN=$((FAILN + 1)); printf '  FAIL %s\n    missing: %s\n    in:\n%s\n' "$1" "$3" "$2" ;;
  esac
}

assert_not_contains() { # name haystack needle
  case "$2" in
    *"$3"*) FAILN=$((FAILN + 1)); printf '  FAIL %s\n    unexpected: %s\n    in:\n%s\n' "$1" "$3" "$2" ;;
    *) PASS=$((PASS + 1)); printf '  ok   %s\n' "$1" ;;
  esac
}

finish() {
  printf '  %s passed, %s failed\n' "$PASS" "$FAILN"
  [ "$FAILN" -eq 0 ]
}
