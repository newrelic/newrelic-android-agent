#!/usr/bin/env bash
# Insert one or more bullets, in order, at the end of a section of a next-release .mdx file.
# Usage: append-bullet.sh [--no-link] FILE SECTION BULLET_LINE [BULLET_LINE...]
#   SECTION: feature | enhancement | bug | security
# Each BULLET_LINE must be one line, start with "* " and end with the PR link (".../pull/N)"), and
# all of them must link the same PR (a release note written as a list). They come from untrusted
# PR text, so anything else is refused (exit 2) rather than written into the file.
# Idempotent: when the PR is already in FILE, nothing is added (exit 0).
# --no-link drops the PR-link requirement and the duplicate check, for bullets that have no PR
# (CHANGELOG.md entries; changelog-bullets.sh does its own duplicate check).
set -euo pipefail
. "$(dirname "$0")/lib.sh"

no_link=0
if [ "${1:-}" = --no-link ]; then no_link=1; shift; fi
[ "$#" -ge 3 ] || { echo "usage: $0 [--no-link] FILE SECTION BULLET_LINE [BULLET_LINE...]" >&2; exit 2; }
file=$1 section=$2
shift 2

is_valid_section "$section" || { echo "unknown section: $section" >&2; exit 2; }
[ -f "$file" ] || { echo "missing file: $file" >&2; exit 2; }

pr="" lines=""
for line in "$@"; do
  case $line in
    *$'\n'* | *$'\r'*) echo "bullet must be a single line" >&2; exit 2 ;;
    '* '*) ;;
    *) echo "bullet must start with '* '" >&2; exit 2 ;;
  esac
  if [ "$no_link" = 0 ]; then
    this=$(printf '%s\n' "$line" | bullet_pr_number)
    [ -n "$this" ] || { echo "bullet must end with a PR link (.../pull/N)" >&2; exit 2; }
    [ -z "$pr" ] || [ "$this" = "$pr" ] || { echo "bullets for different PRs (#$pr, #$this) in one call" >&2; exit 2; }
    pr=$this
  fi
  lines="${lines:+$lines$'\n'}$line"
done
if [ "$no_link" = 0 ] && file_has_pr "$file" "$pr"; then
  echo "PR #$pr already present in $file; skipping." >&2
  exit 0
fi

marker=$(section_marker "$section")
grep -qxF "$marker" "$file" || { echo "marker $marker not found in $file" >&2; exit 2; }
grep -qxF "$END_MARKER" "$file" || { echo "end marker $END_MARKER not found in $file" >&2; exit 2; }

# Build the result in a temp file outside the target's directory, then copy it over the target:
# a failed run leaves the original untouched, nothing stray is left next to it, and the target's
# mode (and any symlink) is preserved.
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

# ENVIRON (not awk -v) so backslashes in PR titles are not interpreted as escapes.
MARKER="$marker" LINES="$lines" awk '
  { lines[NR] = $0 }
  END {
    start = 0
    for (i = 1; i <= NR; i++) if (lines[i] == ENVIRON["MARKER"]) { start = i; break }
    end = NR + 1
    for (i = start + 1; i <= NR; i++) if (index(lines[i], "{/* release-note:") == 1) { end = i; break }
    last = start
    for (i = start + 1; i < end; i++) if (lines[i] != "") last = i
    for (i = 1; i <= NR; i++) {
      print lines[i]
      if (i == last) {
        if (lines[last] !~ /^\* /) print ""
        print ENVIRON["LINES"]
      }
    }
  }
' "$file" > "$tmp"
cat "$tmp" > "$file"
