#!/usr/bin/env bash
# Shared helpers for the docs release-notes scripts. Source this file; do not execute it.
# shellcheck shell=bash disable=SC2034
# (variables below are consumed by the scripts that source this file)
# Portable to bash 3.2 (macOS) and bash 5 (CI): no associative arrays, mapfile or ${var,,}.

# Every pattern in these scripts is ASCII, so the text tools run in the C locale: UTF-8 bytes pass
# through untouched. Without this, BSD tr/awk/sed in a UTF-8 locale (the macOS default) abort on
# some multibyte input ("Illegal byte sequence"). Lengths are therefore bytes unless char_length
# is used.
export LC_ALL=C

# Where every agent's release notes live on docs-website, and the branch release PRs target.
DOCS_ROOT=src/content/docs/release-notes/mobile-release-notes
DOCS_BASE=develop

# An agent's display name ("React Native agent", ".NET MAUI agent"). It is written unquoted into
# the frontmatter and into PR titles, so it is kept to characters that are safe in both.
SUBJECT_RE='^[A-Za-z0-9.+][A-Za-z0-9 .+-]*$'

# Conventional Commit types recognised as a title prefix ("fix: ...", "feat(scope): ...").
CC_TYPES="feat|fix|perf|refactor|docs|test|ci|chore|build|style|security|revert"

# Section keys, in the order they appear in the published note.
SECTIONS="feature enhancement bug security"

is_valid_section() {
  case "$1" in feature | enhancement | bug | security) return 0 ;; *) return 1 ;; esac
}

section_heading() {
  case "$1" in
    feature) echo "New features" ;;
    enhancement) echo "Improvements and enhancements" ;;
    bug) echo "Bug fixes" ;;
    security) echo "Security updates" ;;
    *) return 1 ;;
  esac
}

# Frontmatter array key for a section.
section_array_key() {
  case "$1" in
    feature) echo "features" ;;
    enhancement) echo "enhancements" ;;
    bug) echo "bugs" ;;
    security) echo "security" ;;
    *) return 1 ;;
  esac
}

# Section markers are the anchors every script edits against. They are MDX comments
# (HTML comments are invalid MDX) so the file stays valid while it sits in a draft PR.
section_marker() { printf '{/* release-note:%s */}' "$1"; }
END_MARKER='{/* release-note:end */}'

# skeleton_tail FILE: what follows the end marker in a skeleton (Android's Support statement),
# which every release note ends with. Prints nothing when the marker is the last line.
skeleton_tail() {
  tr -d '\r' < "$1" | awk -v m="$END_MARKER" 'found { print } $0 == m { found = 1 }'
}

# Collapse any whitespace run (including newlines) to one space and trim.
oneline() {
  printf '%s' "$1" | tr '\r\n\t\v\f' '     ' | tr -d '\000-\010\016-\037\177' | sed -E 's/ +/ /g; s/^ //; s/ $//'
}

# Length in characters of UTF-8 text: the bytes that are not continuation bytes.
char_length() {
  printf '%s' "$1" | tr -d '\200-\277' | wc -c | tr -d ' '
}

# Break any "{/*" so text from a PR (e.g. inside a code span, which mdx_escape leaves alone)
# cannot open a comment that strip_comments or the flag parser would later act on.
defuse_comments() {
  printf '%s' "$1" | sed 's|{/\*|{ /*|g'
}

# Escape characters MDX would parse as JSX/expressions. Text inside `inline code` is left alone.
# With an odd number of backticks the code spans are ambiguous, so everything is escaped.
mdx_escape() {
  printf '%s' "$1" | awk '{
    n = split($0, parts, "`")
    balanced = (n % 2 == 1)
    out = ""
    for (i = 1; i <= n; i++) {
      seg = parts[i]
      if (!balanced || i % 2 == 1) {
        gsub(/</, "\\&lt;", seg)
        gsub(/>/, "\\&gt;", seg)
        gsub(/\{/, "\\&#123;", seg)
        gsub(/\}/, "\\&#125;", seg)
      }
      out = out (i > 1 ? "`" : "") seg
    }
    printf "%s", out
  }'
}

# Plain text safe inside a {/* short: ... */} comment and a YAML string: no markup at all.
# Every * is dropped (not just "*/") because removing "*/" once can splice a new one together.
plain_short() {
  oneline "$(printf '%s' "$1" | sed -E 's/[`{}<>*]//g')"
}

# YAML single-quoted scalar.
yaml_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"
}

# Fallback classification from a Conventional Commits title when no label and no model answer.
section_from_title() {
  local lower
  lower=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
  case "$lower" in
    fix:* | fix\(* | fix!:* | fix\ * | fixes\ * | fixed\ *) echo bug ;;
    security:* | security\(* | security!:*) echo security ;;
    feat:* | feat\(* | feat!:*) echo feature ;;
    *) echo enhancement ;;
  esac
}

# Reads bullet lines on stdin, prints the PR number from the trailing "/pull/N)" link of each.
bullet_pr_number() {
  strip_comments | sed -En 's|.*/pull/([0-9]+)\).*|\1|p'
}

# file_has_pr FILE N -> success when a bullet for PR N is already in FILE.
# (grep without -q: with -q an early exit can SIGPIPE the upstream stage under pipefail.)
file_has_pr() {
  grep -E '^\* ' "$1" 2>/dev/null | bullet_pr_number | grep -x "$2" > /dev/null
}

# Remove every {/* ... */} comment from a line (comment bodies never contain braces).
strip_comments() {
  sed -E 's/ *\{\/\*[^{}]*\*\/\}//g'
}

# ---- turning PR details into a bullet (no model involved) ----

# Imperative verb -> past tense for the verbs PR titles in this repo actually start with.
# Prints nothing for any other word (noun phrases, "don't ...", etc.), and a word that is
# usually a noun in titles (log, cache, record, default, batch, ...) is deliberately NOT listed:
# leaving a title as written is better than conjugating a noun. "report" is the one ambiguous
# word kept, because the repo's own titles use it as a verb ("report 5G for ...").
past_tense() {
  case "$1" in
    keep) echo Kept; return ;;
    send) echo Sent; return ;;
    write) echo Wrote; return ;;
    build) echo Built; return ;;
    make) echo Made; return ;;
    set) echo Set; return ;;
    read) echo Read; return ;;
    skip | stop | drop | wrap | defer)
      echo "$(ucfirst "$1")${1: -1}ed"; return ;;
    add | fix | update | remove | improve | enable | disable | handle | prevent | avoid | resolve | \
      restore | reduce | upgrade | replace | rename | ensure | allow | respect | report | guard | flush | \
      raise | synchronize | confine | derive | register | install | harden | expose | introduce | \
      deprecate | detect | collect | migrate | simplify | optimize | validate | normalize | include | \
      exclude | apply | rework | relax | tighten | extend | correct | create | delete | generate | \
      parse | fetch | retry | use | move | persist | capture | throttle | bump | isolate | align | \
      offload | reuse | consolidate | unify) ;;
    *) return 0 ;;
  esac
  case "$1" in
    *e) echo "$(ucfirst "$1")d" ;;
    *[!aeiou]y) echo "$(ucfirst "${1%y}")ied" ;;
    *) echo "$(ucfirst "$1")ed" ;;
  esac
}

# Upper-case the first character of a bullet.
ucfirst() {
  local first=${1:0:1}
  case $first in [a-z]) first=$(printf '%s' "$first" | tr 'a-z' 'A-Z') ;; esac
  printf '%s%s' "$first" "${1:1}"
}

# Make sure a bullet ends like a sentence.
ensure_period() {
  case "$1" in
    *[.!?]) printf '%s' "$1" ;;
    *) printf '%s.' "$1" ;;
  esac
}

# Remove noise from a title: Jira keys ("(NR-625270)", "[NR-1]", "NR-1:", "Nr 382848") and "[skip ci]".
strip_title_noise() {
  oneline "$(printf '%s' "$1" | sed -E 's/\[skip ci\]//Ig; s/[[(][Nn][Rr][- ][0-9]+[])]//g; s/(^|[^[:alnum:]])[Nn][Rr][- ][0-9]+:?/\1/g')"
}

# Conventional Commits title -> bullet text. Only the leading verb is touched. Prints nothing when
# nothing is left after the noise is removed (the caller then falls back to the raw title).
#   "fix: restore alpha in colors (NR-1)"  -> "Restored alpha in colors."
#   "feat(gradle): raise AGP ceiling"      -> "Raised AGP ceiling."
#   "Fix react native sourcemap endpoint"  -> "Fixed react native sourcemap endpoint."
#   "fix: don't start replay"              -> "Don't start replay."   (unknown verb: left as written)
title_to_bullet() {
  local t rest word past
  t=$(strip_title_noise "$1")
  [ -n "$t" ] || return 0
  rest=$(printf '%s' "$t" | sed -E "s/^($CC_TYPES)( *\([^)]*\))? *!? *: *//I")
  [ -n "$rest" ] || rest=$t
  word=$(printf '%s' "$rest" | awk '{ print tolower($1) }')
  past=$(past_tense "$word")
  if [ -n "$past" ]; then
    rest="$past$(printf '%s' "$rest" | sed -E 's/^[^ ]*//')"
  else
    rest=$(ucfirst "$rest")
  fi
  ensure_period "$rest"
}

# Conventional Commit types that are not user-facing. An unlabelled PR of one of these types is
# left out of the release notes; a release-note:* label always includes it.
is_internal_title() {
  local lower
  lower=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
  case "$lower" in
    docs:* | docs\(* | test:* | test\(* | ci:* | ci\(* | chore:* | chore\(* | build:* | build\(* | style:* | style\(*) return 0 ;;
    *) return 1 ;;
  esac
}

# Pull an author-written release note out of a PR description. Two forms are recognised:
#   a heading:  "## Release note" (any level, "note" or "notes") followed by a paragraph
#   one line:   "Release note: Fixed ..."
# HTML comments (PR template hints) are ignored. Prints nothing when there is no usable note.
# Placeholder answers (n/a, none, tbd, "-") count as no note.
extract_release_note() {
  local text
  text=$(release_note_lines "$1" | paste -sd ' ' -)
  text=$(oneline "$(printf '%s' "$text" | sed -E 's/^[-*][ ]+//')")
  is_placeholder_note "$text" && return 0
  printf '%s' "$text"
}

# The release note as one item per line: a paragraph of two or more lines that are all list items
# ("- " or "* ") gives one item per line, its marker stripped; anything else gives the single item
# extract_release_note returns. Placeholder items are dropped. Prints nothing when there is no note.
extract_release_note_items() {
  local lines count l item
  lines=$(release_note_lines "$1")
  count=$(printf '%s\n' "$lines" | grep -c . || true)
  if [ "$count" -ge 2 ] && ! printf '%s\n' "$lines" | grep -qvE '^[-*][ ]+'; then
    while IFS= read -r l; do
      item=$(oneline "$(printf '%s' "$l" | sed -E 's/^[-*][ ]+//')")
      is_placeholder_note "$item" || printf '%s\n' "$item"
    done <<< "$lines"
  else
    item=$(extract_release_note "$1")
    [ -z "$item" ] || printf '%s\n' "$item"
  fi
}

# Placeholder answers (n/a, none, tbd, "-", not applicable, or nothing) are not a release note.
is_placeholder_note() { # text
  case $(printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[.]+$//') in
    '' | n/a | na | none | tbd | - | 'not applicable') return 0 ;;
  esac
  return 1
}

# The lines of a PR description's release-note paragraph, trimmed, one per line, in the forms
# extract_release_note documents (HTML comments removed). Prints nothing when there is none.
release_note_lines() {
  printf '%s\n' "$1" | tr -d '\r' | awk '
    BEGIN { incomment = 0; state = 0; n = 0 }
    {
      line = $0
      out = ""
      while (length(line) > 0) {
        if (incomment) {
          i = index(line, "-->")
          if (i == 0) { line = ""; break }
          line = substr(line, i + 3); incomment = 0
        } else {
          i = index(line, "<!--")
          if (i == 0) { out = out line; line = ""; break }
          out = out substr(line, 1, i - 1); line = substr(line, i + 4); incomment = 1
        }
      }
      low = tolower(out)
      if (state == 2) next
      if (state == 1) {
        if (out ~ /^[ \t]*$/) { if (n > 0) state = 2; next }
        if (out ~ /^[ \t]*#/) { state = 2; next }
        gsub(/^[ \t]+|[ \t]+$/, "", out)
        para[++n] = out
        next
      }
      if (low ~ /^[ \t]*#+[ \t]*release notes?[ \t]*:?[ \t]*$/) { state = 1; next }
      if (low ~ /^[ \t]*release notes?[ \t]*:[ \t]*[^ \t]/) {
        sub(/^[^:]*:[ \t]*/, "", out); gsub(/[ \t]+$/, "", out); para[++n] = out; state = 2; next
      }
    }
    END { for (i = 1; i <= n; i++) print para[i] }'
}

# Shorten to at most N characters, cutting at a word boundary; drops a trailing full stop.
# Counts bytes (see the LC_ALL=C note at the top); iconv -c then drops a multibyte character the
# cut may have split, so the result is always valid UTF-8.
short_form() { # text max
  local s cut
  s=$(printf '%s' "$1" | sed -E 's/[.]+$//')
  if [ "${#s}" -gt "$2" ]; then
    cut=${s:0:$2}
    case "${s:$2:1}" in
      ' ') ;;
      *) case "$cut" in *' '*) cut=${cut% *} ;; esac ;;
    esac
    s=$(printf '%s' "$cut" | iconv -f UTF-8 -t UTF-8 -c 2> /dev/null)
  fi
  printf '%s' "$s"
}

# The summary of a bullet's plain text (see plain_short) for the frontmatter lists, at most
# SUMMARY_MAX bytes: the whole text, else its first sentence, else what comes before its first
# ": ", else a word-boundary cut followed by an ellipsis. A sentence ends at . ! or ? followed by a
# space and a capital letter, or at the end of the text, so "7.8.4", "e.g." and "::" never end one.
# A trailing full stop is dropped, as short_form does.
SUMMARY_MAX=200
note_summary() { # text
  local s first head
  s=$(printf '%s' "$1" | sed -E 's/[.]+$//')
  if [ "${#s}" -le "$SUMMARY_MAX" ]; then printf '%s' "$s"; return 0; fi
  first=$(printf '%s' "$s" | sed -E 's/([.!?]) [A-Z].*$/\1/; s/[.]+$//')
  if [ "$first" != "$s" ] && [ "${#first}" -le "$SUMMARY_MAX" ]; then printf '%s' "$first"; return 0; fi
  case $s in *": "*) head=${s%%": "*} ;; *) head="" ;; esac
  if [ -n "$head" ] && [ "${#head}" -le "$SUMMARY_MAX" ]; then printf '%s' "$head"; return 0; fi
  printf '%s\342\200\246' "$(short_form "$s" "$SUMMARY_MAX")"
}

# The frontmatter summary of one bullet line of a next file: its text without the "* ", the
# {/* */} comments, the trailing PR link and the MDX escapes, through plain_short and note_summary.
bullet_summary() { # line
  note_summary "$(plain_short "$(printf '%s' "$1" | strip_comments |
    sed -E 's/^\* //; s/ \(\[#[0-9]+\]\([^)]*\)\)$//; s/&(lt|gt|#123|#125);//g')")"
}

# skip_reason TITLE BODY HEAD_BRANCH [IS_FORK]
# Prints why a PR can never appear in the release notes and returns 0; returns 1 when it can.
# Shared by process-pr.sh (after merge) and check-pr.sh (on the open PR) so they cannot disagree.
skip_reason() {
  # Head-branch names are only meaningful for PRs from this repository: a fork contributor's PR from
  # their own "main" is an ordinary change, not a sync PR. The 4th argument is "true" for fork PRs.
  if [ "${4:-false}" != true ]; then
    case "$3" in
      release/*) echo "release branch"; return 0 ;;
      release-please--*) echo "release PR"; return 0 ;;
      main | develop) echo "bridge or revert PR"; return 0 ;; # a sync/back-merge PR, not a change
    esac
  fi
  if printf '%s' "$1" | grep -qiE '^chore\(release\)|^chore: (release|update gradle\.properties)'; then
    echo "release chore"; return 0
  fi
  if printf '%s' "$1" | grep -qiE "^(back ?port|revert|release v|merge (branch|pull request|remote|main|develop|release))"; then
    echo "bridge or revert PR"; return 0
  fi
  case "$2" in *'[skip-docs]'*) echo "[skip-docs]"; return 0 ;; esac
  return 1
}

# is_internal_pr TITLE HEAD_BRANCH: not user-facing by default (docs/test/ci/chore/build/style
# title type, or a Dependabot branch). A release-note:* label always includes the PR anyway.
is_internal_pr() {
  case "$2" in dependabot/*) return 0 ;; esac
  is_internal_title "$1"
}
