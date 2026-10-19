#!/usr/bin/env bash
# Add the CHANGELOG.md entries for one version to a release .mdx that already holds its PR bullets.
# Usage: changelog-bullets.sh VERSION CHANGELOG NEXT_FILE      (NEXT_FILE is edited in place)
# The version's entries are the "-"/"*" list items between "## VERSION" and the next "## X.Y.Z".
# Each goes in the section named by the nearest heading above it: security -> security,
# fix/bug -> bug, feature or "New in this release" -> feature, anything else ("Improvements",
# no heading) -> enhancement. Blockquoted callouts and "Auto-release from commit:" lines are skipped.
# An entry whose text already appears in a bullet of NEXT_FILE (case, spacing and full stops
# ignored) is not added, so a change that is both in CHANGELOG.md and on the next branch appears once.
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

# Tab-separated "section<TAB>entry" lines for the version. Reading a file, so an early exit is safe.
entries=$(tr -d '\r' < "$changelog" | awk -v v="$version" '
  BEGIN { esc = v; gsub(/\./, "\\.", esc); s = "enhancement" }
  !inv && $0 ~ ("^## " esc "([ \t(]|$)") { inv = 1; next }
  !inv { next }
  /^## [0-9]/ { exit }
  /^#+ / {
    h = tolower($0)
    if (h ~ /security/) s = "security"
    else if (h ~ /fix|bug/) s = "bug"
    else if (h ~ /feature|new in this release/) s = "feature"
    else s = "enhancement"
    next
  }
  /^[ \t]*[-*+] / {
    t = $0; sub(/^[ \t]*[-*+] +/, "", t)
    if (t ~ /^Auto-release from commit:/) next
    print s "\t" t
  }')

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
  short=$(short_form "$(plain_short "$text")" 120)
  "$dir/append-bullet.sh" --no-link "$next" "$section" "* $text {/* short: $short */}"
  printf '%s\n' "$key" >> "$keys"
  echo "added $section: $text"
done
