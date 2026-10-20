#!/usr/bin/env bash
# Map a PR's labels to a release-note section.
# Usage: classify-pr.sh [LABEL...]     (labels one per line on stdin when no arguments)
# Prints feature | enhancement | bug | security.
# Exit 0: exactly one of release-note:{feature,enhancement,bug,security}. Exit 10: none. Exit 11: more than one
# (prints nothing). Other release-note:* values are ignored. Matching is exact and case-sensitive.
# With no arguments it reads stdin, so never call it with an unquoted empty variable.
set -euo pipefail
export LC_ALL=C # ASCII patterns only; see lib.sh

if [ "$#" -eq 0 ]; then labels=$(cat); else labels=$(printf '%s\n' "$@"); fi

found=$(printf '%s\n' "$labels" | sed -En 's/^release-note:(feature|enhancement|bug|security)$/\1/p')
count=$(printf '%s' "$found" | grep -c . || true)

case "$count" in
  0) exit 10 ;;
  1) printf '%s\n' "$found" ;;
  *) exit 11 ;;
esac
