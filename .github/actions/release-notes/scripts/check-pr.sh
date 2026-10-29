#!/usr/bin/env bash
# PR-time release-notes check: does this open PR have what the release-notes automation needs,
# and what will it turn into? Prints a Markdown report on stdout.
# Env:  PR_TITLE (required), PR_BODY, PR_HEAD (head branch), PR_LABELS (one label per line),
#       PR_FORK ("true" when the PR comes from a fork)
# Exit: 0 passed (suggestions allowed), 1 the PR needs fixing (missing or conflicting label),
#       2 the check itself could not run (nothing is written to stdout in that case, so a
#       half-built report can never be posted as if it were a result).
#
# Fails only for what changes where a bullet lands. Title style, title length and a missing
# "Release note" section are suggestions, because the automation copes with all three.
set -euo pipefail
dir=$(cd "$(dirname "$0")" && pwd)
. "$dir/lib.sh"

[ -n "${PR_TITLE:-}" ] || { echo "check-pr.sh: PR_TITLE is required" >&2; exit 2; }

# Hold the report back until the check has finished; only exit codes 0 and 1 release it.
buf=$(mktemp)
exec 3>&1 > "$buf"
finish() {
  local code=$?
  exec >&3
  if [ "$code" -le 1 ]; then cat "$buf"; fi
  rm -f "$buf"
  exit "$code"
}
trap finish EXIT
# Any unexpected failure is an internal error (exit 2), never mistaken for "needs a label" (exit 1).
trap 'exit 2' ERR
body=${PR_BODY:-}
head=${PR_HEAD:-}
fork=${PR_FORK:-false}
labels=${PR_LABELS:-}

errors="" warnings=""
err() { errors="$errors- $1"$'\n'; }
warn() { warnings="$warnings- $1"$'\n'; }

LABEL_LIST='`release-note:feature`, `release-note:enhancement`, `release-note:bug` or `release-note:security`'

# The preview is rendered as a GitHub comment, where an @mention notifies people, #123 writes a
# back-reference on another issue and links/images render. Defuse those in the preview only; the
# real bullet is untouched. Long text is capped (a comment is limited to 65,536 characters).
preview_text() {
  local zw t=$1
  zw=$(printf '\342\200\213')
  if [ "${#t}" -gt 500 ]; then t="$(short_form "$t" 500) ..."; fi
  printf '%s' "$t" | sed -E 's/\[/\\[/g; s/\]/\\]/g' | sed -E "s/@/@${zw}/g; s/(^|[^&])#([0-9])/\\1#${zw}\\2/g"
}

echo '<!-- release-notes-check -->'

if reason=$(skip_reason "$PR_TITLE" "$body" "$head" "$fork"); then
  echo '### Release notes check: passed'
  echo
  echo "This PR is not included in the release notes ($reason)."
  exit 0
fi

rc=0
hint=$(printf '%s\n' "$labels" | "$dir/classify-pr.sh") || rc=$?
internal=0
is_internal_pr "$PR_TITLE" "$head" && internal=1

case "$rc" in
  0) ;;
  10)
    if [ "$internal" = 1 ]; then
      hint=""
    else
      hint=""
      err "Add exactly one label: $LABEL_LIST. The label decides which section of the release notes this PR appears in. To leave this PR out of the release notes, put \`[skip-docs]\` in its description instead."
    fi
    ;;
  11)
    hint=""
    err "This PR has more than one \`release-note:*\` label. Keep exactly one."
    ;;
  *)
    echo "check-pr.sh: label classification failed (exit $rc)" >&2
    exit 2
    ;;
esac

if ! printf '%s' "$PR_TITLE" | grep -qiE "^($CC_TYPES)(\([^)]*\))?!?: "; then
  warn "The title is not in Conventional Commits form (\`fix: guard against null filename\`). The bullet is built from the title, so use \`feat:\`, \`fix:\`, \`security:\` etc. where you can."
fi
title_len=$(char_length "$PR_TITLE")
if [ "$title_len" -gt 70 ]; then
  warn "The title is $title_len characters; it becomes the squash-merge commit message, so 70 or fewer reads better."
fi

note=$(extract_release_note "$body")
if [ -z "$note" ] && { [ "$internal" = 0 ] || [ -n "$hint" ]; }; then
  warn "No \`## Release note\` section, so the bullet will be built from the title. Add a heading \`## Release note\` followed by one sentence for app developers (what changed, or what was broken) to control the wording. Write \`N/A\` to keep the title."
fi

if [ -z "$errors" ]; then echo '### Release notes check: passed'; else echo '### Release notes check: needs attention'; fi
echo

if [ "$rc" = 10 ] && [ "$internal" = 1 ]; then
  echo "Left out of the release notes: \`docs\`, \`test\`, \`ci\`, \`chore\`, \`build\` and \`style\` PRs and Dependabot PRs are not user-facing. Add a \`release-note:*\` label to include it."
else
  built=$(PR_TITLE="$PR_TITLE" PR_BODY="${body:0:20000}" HINT_SECTION="$hint" "$dir/build-bullet.sh") \
    || { echo "check-pr.sh: could not build the preview" >&2; exit 2; }
  # One JSON line per bullet (a release note written as a list gives several), all in one section.
  section=$(head -1 <<<"$built" | jq -r '.section')
  if [ -n "$hint" ]; then origin="from its label"; else origin="guessed from the title; a label would make it certain"; fi
  echo "This PR will appear under **$(section_heading "$section")** ($origin) as:"
  sep=""
  while IFS= read -r b; do
    printf '%s\n> %s\n' "${sep:-}" "$(preview_text "$(jq -r '.bullet' <<<"$b")")"
    sep=">"
  done <<<"$built"
fi

if [ -n "$errors" ]; then
  echo
  echo '**Fix before merging**'
  echo
  printf '%s' "$errors"
fi
if [ -n "$warnings" ]; then
  echo
  echo '**Suggestions**'
  echo
  printf '%s' "$warnings"
fi
echo
echo '_Release notes are reviewed and edited by a person before they are published._'

if [ -z "$errors" ]; then exit 0; else exit 1; fi
