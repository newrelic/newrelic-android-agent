#!/usr/bin/env bash
# Print the highest release tag strictly lower than VERSION. Tags are read from stdin.
# Only PREFIX followed by X.Y.Z counts as a release tag (PREFIX defaults to none), so nightly-*,
# pre-release and differently-prefixed tags are ignored. The answer keeps the prefix.
# Usage: git tag --list | previous-tag.sh [--prefix PREFIX] VERSION      (VERSION is bare X.Y.Z)
#   git tag --list | previous-tag.sh 7.8.4              -> 7.8.3
#   git tag --list | previous-tag.sh --prefix v 1.9.3   -> v1.9.2
# Exit 0 and prints the tag; exit 1 and prints nothing when there is no earlier release tag (or
# VERSION cannot be placed in the sorted list); exit 2 when VERSION is not X.Y.Z or PREFIX is not letters.
# It never prints nothing with exit 0: the caller feeds the answer to the GitHub release-notes API,
# where an empty base would silently change which PRs are listed.
set -euo pipefail
export LC_ALL=C # ASCII patterns only; see lib.sh
prefix=""
if [ "${1:-}" = --prefix ]; then
  [ "$#" -ge 2 ] || { echo "usage: $0 [--prefix PREFIX] VERSION" >&2; exit 2; }
  prefix=$2; shift 2
fi
[ "$#" -eq 1 ] || { echo "usage: $0 [--prefix PREFIX] VERSION" >&2; exit 2; }
[[ $prefix =~ ^[A-Za-z]*$ ]] || { echo "invalid prefix: $prefix" >&2; exit 2; }
v=$1
re='^[0-9]+\.[0-9]+\.[0-9]+$'
[[ $v =~ $re ]] || { echo "invalid version: $v" >&2; exit 2; }

# The whole stream is read (no early awk exit, which could SIGPIPE sort under pipefail on a huge tag list).
prev=$({ { grep -E "^${prefix}[0-9]+\.[0-9]+\.[0-9]+$" || true; } | sed "s/^${prefix}//"; printf '%s\n' "$v"; } \
  | sort -t. -k1,1n -k2,2n -k3,3n -u \
  | awk -v v="$v" '
      $0 == v { found = 1; if (prev == "") bad = 1; else out = prev }
      !found { prev = $0 }
      END { if (found && !bad) print out; else exit 1 }')
printf '%s%s\n' "$prefix" "$prev"
