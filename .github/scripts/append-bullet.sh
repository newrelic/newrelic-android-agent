#!/usr/bin/env bash
# Insert one bullet at the end of a section of a next-release .mdx file.
# Usage: append-bullet.sh FILE SECTION BULLET_LINE
#   SECTION: feature | enhancement | bug | security
# Idempotent: a bullet whose PR number is already in FILE is not added again.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

[ "$#" -eq 3 ] || { echo "usage: $0 FILE SECTION BULLET_LINE" >&2; exit 2; }
file=$1 section=$2 line=$3

is_valid_section "$section" || { echo "unknown section: $section" >&2; exit 2; }
[ -f "$file" ] || { echo "missing file: $file" >&2; exit 2; }

pr=$(printf '%s\n' "$line" | bullet_pr_number)
if [ -n "$pr" ] && file_has_pr "$file" "$pr"; then
  echo "PR #$pr already present in $file; skipping." >&2
  exit 0
fi

marker=$(section_marker "$section")
grep -qxF "$marker" "$file" || { echo "marker $marker not found in $file" >&2; exit 2; }

# ENVIRON (not awk -v) so backslashes in PR titles are not interpreted as escapes.
MARKER="$marker" LINE="$line" awk '
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
        if (lines[last] ~ /^## /) print ""
        print ENVIRON["LINE"]
      }
    }
  }
' "$file" > "$file.tmp"
mv "$file.tmp" "$file"
