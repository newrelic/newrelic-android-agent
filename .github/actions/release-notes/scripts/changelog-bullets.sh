#!/usr/bin/env bash
# Add the CHANGELOG entries for one version to a release .mdx that already holds its PR bullets.
# Usage: changelog-bullets.sh VERSION CHANGELOG NEXT_FILE      (NEXT_FILE is edited in place)
# The version's section starts at a heading of any level whose text starts with VERSION, bare,
# v-prefixed or [bracketed], followed by a space, "(", "]" or the end of the line ("# 7.1.12",
# "## 1.6.13", "### 1.3.2", "## v1.9.2 (2026-10-05)", "## [1.3.2] - 2026-10-05"). It ends at the
# next heading of that shape for any version. Every other heading inside it names a section,
# whatever its level: security -> security, fix/bug -> bug, feature or "New in this release" ->
# feature, anything else ("Improvements", "Upgrade notes", no heading) -> enhancement.
# Each "-"/"*"/"+" list item (nested ones too) is one entry; indented lines under an item continue
# it. Blockquoted callouts and "Auto-release from commit:" lines are skipped.
# An entry whose text already appears in a bullet of NEXT_FILE (case, spacing and full stops
# ignored) is not added, so a change that is both in the CHANGELOG and on the next branch appears once.
# Prints one line per entry, "added <section>: <text>" or "duplicate: <text>". A version with no
# entries prints nothing and exits 0. Exit 2 on bad arguments.
set -euo pipefail
dir=$(cd "$(dirname "$0")" && pwd)
. "$dir/lib.sh"

[ "$#" -eq 3 ] || { echo "usage: $0 VERSION CHANGELOG NEXT_FILE" >&2; exit 2; }
version=$1 changelog=$2 next=$3
version_re='^[0-9]+\.[0-9]+\.[0-9]+$'
[[ $version =~ $version_re ]] || { echo "VERSION must look like 1.9.3: $version" >&2; exit 2; }
[ -r "$changelog" ] || { echo "cannot read $changelog" >&2; exit 2; }
[ -f "$next" ] || { echo "missing file: $next" >&2; exit 2; }

# Comparison key for bullet text: no comments, PR link, list marker, case, full stops or extra spaces.
# Only a full stop before a space or at the end goes, so "7.8.3" keeps its dots.
bullet_key() {
  printf '%s\n' "$1" | strip_comments \
    | sed -E 's/^\* //; s/ \(\[#[0-9]+\]\([^)]*\)\)$//' \
    | tr '[:upper:]' '[:lower:]' \
    | sed -E 's/[.]+( |$)/\1/g; s/ +/ /g; s/^ //; s/ $//'
}

keys=$(mktemp); trap 'rm -f "$keys"' EXIT
grep -E '^\* ' "$next" | tr -d '\r' | while IFS= read -r l; do bullet_key "$l"; done > "$keys" || true

# Tab-separated "section<TAB>entry" lines for the version. awk reads to the end of the file even
# after the version's section ends: an early exit would kill tr with SIGPIPE under pipefail once the
# file outgrows the pipe buffer (16 KB on macOS, 64 KB on Linux).
entries=$(tr -d '\r' < "$changelog" | awk -v v="$version" '
  function flush() { if (cur != "") print cs "\t" cur; cur = "" }
  BEGIN { esc = v; gsub(/\./, "\\.", esc); s = "enhancement"; cur = "" }
  done { next }
  # Up to three leading spaces still make a heading (CommonMark), so " ## 1.5.11" counts. A "]"
  # first in a bracket is literal in every awk, which "\]" is not.
  /^ ? ? ?#+[ \t]+\[?v?[0-9]+\.[0-9]+\.[0-9]+([] \t(]|$)/ {
    if (inv) { flush(); inv = 0; done = 1; next }
    if ($0 ~ ("^ ? ? ?#+[ \t]+\\[?v?" esc "([] \t(]|$)")) inv = 1
    next
  }
  !inv { next }
  /^ ? ? ?#+[ \t]/ {
    flush()
    h = tolower($0)
    if (h ~ /security/) s = "security"
    else if (h ~ /fix|bug/) s = "bug"
    else if (h ~ /feature|new in this release/) s = "feature"
    else s = "enhancement"
    next
  }
  /^[ \t]*[-*+][ \t]+/ {
    flush()
    t = $0; sub(/^[ \t]*[-*+][ \t]+/, "", t)
    if (t ~ /^Auto-release from commit:/) next
    cur = t; cs = s
    next
  }
  /^[ \t]+[^ \t]/ { if (cur != "") { t = $0; sub(/^[ \t]+/, "", t); cur = cur " " t }; next }
  { flush() }
  END { flush() }')

[ -n "$entries" ] || exit 0
printf '%s\n' "$entries" | while IFS="$(printf '\t')" read -r section entry; do
  text=$(oneline "$entry")
  [ -n "$text" ] || continue
  text=$(defuse_comments "$(mdx_escape "$(ensure_period "$(ucfirst "$text")")")")
  key=$(bullet_key "* $text")
  dup=0
  while IFS= read -r k; do
    case "$k" in *"$key"*) dup=1; break ;; esac
  done < "$keys"
  if [ "$dup" = 1 ]; then echo "duplicate: $text"; continue; fi
  short=$(note_summary "$text")
  "$dir/append-bullet.sh" --no-link "$next" "$section" "* $text {/* short: $short */}"
  printf '%s\n' "$key" >> "$keys"
  echo "added $section: $text"
done
