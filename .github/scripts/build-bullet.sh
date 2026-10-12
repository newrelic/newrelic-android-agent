#!/usr/bin/env bash
# Build the release-note bullet for one merged PR from its own details. No model is involved.
#
# Env in:  PR_TITLE (required), PR_BODY, HINT_SECTION (feature|enhancement|bug|security,
#          authoritative when set, from the PR's release-note:* label)
# Stdout:  one JSON object {"bullet","short","section"}
#
# Text: an author-written "## Release note" section or "Release note:" line in the PR description
#       wins; otherwise the title is rewritten (type prefix and Jira key removed, leading verb in
#       past tense, capitalised, full stop). Section: HINT_SECTION, else the Conventional Commit type.
# The result is MDX-escaped. A human still edits every bullet on the docs PR.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

: "${PR_TITLE:?PR_TITLE is required}"
HINT=${HINT_SECTION:-}
if [ -n "$HINT" ] && ! is_valid_section "$HINT"; then HINT=""; fi

note=$(extract_release_note "${PR_BODY:-}")
if [ -n "$note" ]; then
  text=$(ensure_period "$(ucfirst "$note")")
else
  text=$(title_to_bullet "$PR_TITLE")
fi
[ -n "$text" ] || text=$(ensure_period "$(oneline "$PR_TITLE")")

section=${HINT:-$(section_from_title "$PR_TITLE")}
short=$(short_form "$(plain_short "$text")" 120)

jq -nc --arg bullet "$(mdx_escape "$text")" --arg short "$short" --arg section "$section" \
  '{bullet: $bullet, short: $short, section: $section}'
