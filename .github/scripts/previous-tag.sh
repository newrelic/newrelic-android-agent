#!/usr/bin/env bash
# Print the highest release tag strictly lower than VERSION. Tags are read from stdin.
# Only plain X.Y.Z tags count, so nightly-* and pre-release tags are ignored.
# Usage: git tag --list | previous-tag.sh 7.8.4
# Exit 1 (prints nothing) when there is no earlier release tag.
set -euo pipefail
export LC_ALL=C # ASCII patterns only; see lib.sh
[ "$#" -eq 1 ] || { echo "usage: $0 VERSION" >&2; exit 2; }
v=$1

{ grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' || true; printf '%s\n' "$v"; } \
  | sort -t. -k1,1n -k2,2n -k3,3n -u \
  | awk -v v="$v" '$0 == v { if (prev == "") exit 1; print prev; exit } { prev = $0 }'
