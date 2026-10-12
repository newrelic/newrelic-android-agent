#!/usr/bin/env bash
# Runs every test_*.sh next to this file, each in its own bash process.
set -u
dir=$(cd "$(dirname "$0")" && pwd)
status=0
for t in "$dir"/test_*.sh; do
  echo "== $(basename "$t")"
  bash "$t" || status=1
done
exit $status
