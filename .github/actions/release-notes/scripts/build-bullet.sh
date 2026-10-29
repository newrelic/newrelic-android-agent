#!/usr/bin/env bash
# Build the release-note bullet for one merged PR from its own details. No model is involved.
#
# Env in:  PR_TITLE (required), PR_BODY, HINT_SECTION (feature|enhancement|bug|security,
#          authoritative when set, from the PR's release-note:* label)
# Stdout:  one JSON object {"bullet","short","section"} per bullet, one per line
#
# Text: an author-written "## Release note" section or "Release note:" line in the PR description
#       wins; a section written as a list ("- " or "* " on every line, two or more lines) gives one
#       bullet per item. Otherwise the title is rewritten (type prefix and Jira key removed, leading
#       verb in past tense, capitalised, full stop). Section: HINT_SECTION, else the Conventional
#       Commit type; every bullet of a PR gets the same one.
# The result is MDX-escaped. A human still edits every bullet on the docs PR.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

: "${PR_TITLE:?PR_TITLE is required}"
command -v jq > /dev/null || { echo "jq is required" >&2; exit 1; }
[ -n "$(oneline "$PR_TITLE")" ] || { echo "PR_TITLE is blank" >&2; exit 1; }
HINT=${HINT_SECTION:-}
if [ -n "$HINT" ] && ! is_valid_section "$HINT"; then HINT=""; fi

section=${HINT:-$(section_from_title "$PR_TITLE")}

emit() { # text
  jq -nc --arg bullet "$(defuse_comments "$(mdx_escape "$1")")" --arg short "$(note_summary "$(plain_short "$1")")" \
    --arg section "$section" '{bullet: $bullet, short: $short, section: $section}'
}

items=$(extract_release_note_items "${PR_BODY:-}")
if [ -n "$items" ]; then
  while IFS= read -r item; do emit "$(ensure_period "$(ucfirst "$item")")"; done <<< "$items"
else
  text=$(title_to_bullet "$PR_TITLE")
  [ -n "$text" ] || text=$(ensure_period "$(oneline "$PR_TITLE")")
  emit "$text"
fi
