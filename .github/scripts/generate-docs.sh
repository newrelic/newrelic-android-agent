#!/usr/bin/env bash
# Turn an accumulated next-release .mdx into the final release-notes .mdx.
# Usage: generate-docs.sh --next FILE --version VER --date YYYY-MM-DD --out FILE
#                         [--download-link URL] [--flags-out FILE]
# - Fills the four frontmatter arrays from each bullet's {/* short: ... */} comment.
# - Deletes every section whose array is empty; keeps `category:` present and blank.
# - Keeps everything after the {/* release-note:end */} marker (the Support statement) verbatim.
# - Writes one "#<pr> <flag>" line per flagged bullet to --flags-out for the PR description.
# Exits 3 when the file has no bullets at all; exits 2 on bad arguments or a malformed input file
# (unknown section marker, bullet before any marker, missing end marker) instead of dropping bullets.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

next="" version="" date="" out="" link="" flags_out=""
while [ "$#" -gt 0 ]; do
  [ "$#" -ge 2 ] || { echo "$1 needs a value" >&2; exit 2; }
  case "$1" in
    --next) next=$2 ;; --version) version=$2 ;; --date) date=$2 ;;
    --out) out=$2 ;; --download-link) link=$2 ;; --flags-out) flags_out=$2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift 2
done
for v in next version date out; do
  [ -n "${!v}" ] || { echo "--$v is required" >&2; exit 2; }
done
[ -f "$next" ] || { echo "missing file: $next" >&2; exit 2; }

# These land in the frontmatter, so they must not be able to add keys or break the YAML.
# ([[ =~ ]] sees the whole string; grep would accept "7.8.4<newline>evil: 1" because its first line matches.)
version_re='^[0-9]+\.[0-9]+\.[0-9]+$'
date_re='^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
[[ $version =~ $version_re ]] || { echo "--version must look like 7.8.4" >&2; exit 2; }
[[ $date =~ $date_re ]] || { echo "--date must look like 2026-10-20" >&2; exit 2; }
case $link in '' | https://*) ;; *) echo "--download-link must be an https URL" >&2; exit 2 ;; esac
case $link in *"'"* | *$'\n'*) echo "--download-link must not contain quotes or newlines" >&2; exit 2 ;; esac

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
# Work on a copy without carriage returns so a CRLF-edited file still matches the exact markers.
tr -d '\r' < "$next" > "$tmp/next.clean"
grep -qxF "$END_MARKER" "$tmp/next.clean" || { echo "end marker $END_MARKER not found in $next" >&2; exit 2; }
: > "$tmp/flags"; : > "$tmp/support"
for s in $SECTIONS; do : > "$tmp/$s.body"; : > "$tmp/$s.short"; done

section="" in_support=0
while IFS= read -r l || [ -n "$l" ]; do
  if [ "$in_support" = 1 ]; then printf '%s\n' "$l" >> "$tmp/support"; continue; fi
  case "$l" in
    "$END_MARKER") in_support=1; section=""; continue ;;
    '{/* release-note:'*' */}')
      section=${l#'{/* release-note:'}; section=${section%' */}'}
      is_valid_section "$section" || { echo "unknown section marker: $section" >&2; exit 2; }
      continue ;;
    '* '*)
      [ -n "$section" ] || { echo "bullet before any section marker: $l" >&2; exit 2; }
      short=$(printf '%s\n' "$l" | sed -En 's/.*\{\/\* short: ([^{}]*) \*\/\}.*/\1/p')
      if [ -z "$short" ]; then
        # No short comment: derive one from the bullet text minus its trailing PR link.
        short=$(printf '%s' "$l" | strip_comments | sed -E 's/^\* //; s/ \(\[#[0-9]+\]\([^)]*\)\)$//')
        short=$(plain_short "$short")
      fi
      pr=$(printf '%s\n' "$l" | bullet_pr_number)
      for f in $(printf '%s\n' "$l" | grep -oE '\{/\* flag: [a-z-]+ \*/\}' | sed -E 's/.*flag: ([a-z-]+).*/\1/'); do
        printf '#%s %s\n' "${pr:-hand-written}" "$f" >> "$tmp/flags"
      done
      printf '%s\n' "$short" >> "$tmp/$section.short"
      printf '%s\n' "$(printf '%s' "$l" | strip_comments | sed 's/^\* /- /')" >> "$tmp/$section.body"
      ;;
  esac
done < "$tmp/next.clean"

total=0
for s in $SECTIONS; do total=$((total + $(wc -l < "$tmp/$s.short"))); done
[ "$total" -gt 0 ] || { echo "no release-note bullets found in $next" >&2; exit 3; }

emit_array() { # section
  key=$(section_array_key "$1")
  if [ -s "$tmp/$1.short" ]; then
    printf '%s:\n' "$key"
    while IFS= read -r s; do printf '  - %s\n' "$(yaml_quote "$s")"; done < "$tmp/$1.short"
  else
    printf '%s: []\n' "$key"
  fi
}

{
  printf -- '---\n'
  printf 'subject: %s\n' "$DOCS_SUBJECT"
  printf "releaseDate: '%s'\n" "$date"
  printf 'version: %s\n' "$version"
  [ -z "$link" ] || printf "downloadLink: '%s'\n" "$link"
  printf 'category:\n'
  for s in $SECTIONS; do emit_array "$s"; done
  printf -- '---\n'
  for s in $SECTIONS; do
    [ -s "$tmp/$s.body" ] || continue
    printf '\n## %s\n\n' "$(section_heading "$s")"
    cat "$tmp/$s.body"
  done
  printf '\n'
  cat "$tmp/support"
} > "$out"

[ -z "$flags_out" ] || cp "$tmp/flags" "$flags_out"
echo "wrote $out ($total bullets)" >&2
