#!/usr/bin/env bash
# Print only the release tags (PREFIX followed by X.Y.Z) among the tags read from stdin, in order.
# Usage: git tag --list --contains SHA | release-tags.sh [--prefix PREFIX]
# The same rule as previous-tag.sh, so nightly-*, pre-release and differently-prefixed tags never
# count as a release. Prints nothing (exit 0) when there are none; exit 2 when PREFIX is not letters.
set -euo pipefail
export LC_ALL=C # ASCII patterns only; see lib.sh
prefix=""
if [ "${1:-}" = --prefix ]; then
  [ "$#" -ge 2 ] || { echo "usage: $0 [--prefix PREFIX]" >&2; exit 2; }
  prefix=$2; shift 2
fi
[ "$#" -eq 0 ] || { echo "usage: $0 [--prefix PREFIX]" >&2; exit 2; }
[[ $prefix =~ ^[A-Za-z]*$ ]] || { echo "invalid prefix: $prefix" >&2; exit 2; }
grep -E "^${prefix}[0-9]+\.[0-9]+\.[0-9]+$" || true
