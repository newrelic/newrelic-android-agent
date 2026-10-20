#!/usr/bin/env bash
# Filter the bullet lines of a next-release .mdx by PR number; everything else passes through.
# Usage: filter-bullets.sh FILE keep|drop [PR_NUMBER...]
#   keep: only bullets for the listed PRs.   drop: all bullets except those for the listed PRs.
# A bullet with no PR link (added by hand in the draft PR) counts as belonging to the release
# being cut: it is kept by "keep" and consumed by "drop".
# Only column-0 "* " lines are bullets. A bullet's PR is the LAST ".../pull/N)" link on its line
# ({/* ... */} comments are ignored), so a pull link written into a hand-edited bullet is read as that
# bullet's PR. Output goes to stdout: write it to a temp file and move it into place only on exit 0.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

[ "$#" -ge 2 ] || { echo "usage: $0 FILE keep|drop [PR...]" >&2; exit 2; }
file=$1 mode=$2
[ -r "$file" ] || { echo "cannot read $file" >&2; exit 2; }
shift 2
case "$mode" in keep | drop) ;; *) echo "mode must be keep or drop" >&2; exit 2 ;; esac
prs=" $* "

while IFS= read -r l || [ -n "$l" ]; do
  case "$l" in
    '* '*)
      n=$(printf '%s\n' "$l" | bullet_pr_number)
      case "$prs" in *" $n "*) member=1 ;; *) member=0 ;; esac
      [ -n "$n" ] || member=1
      if [ "$mode" = keep ] && [ "$member" = 1 ]; then printf '%s\n' "$l"; fi
      if [ "$mode" = drop ] && [ "$member" = 0 ]; then printf '%s\n' "$l"; fi
      ;;
    *) printf '%s\n' "$l" ;;
  esac
done < "$file"
