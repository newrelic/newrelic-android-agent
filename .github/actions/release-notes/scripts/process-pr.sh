#!/usr/bin/env bash
# Build and append the release-note bullet for one merged PR.
# Usage: process-pr.sh PR_NUMBER NEXT_FILE
# Env:   GITHUB_REPOSITORY (owner/name), GH_TOKEN for gh,
#        COMMENT_ON_UNLABELLED=1 to ask for a label on the PR when it has none or too many.
# A PR with no release-note:* label is flagged "unlabelled"; one with several is flagged
# "conflicting-labels". Either way it is included (section guessed from the title) unless it is an
# internal change with NO label at all.
# Prints one status line: "appended #N -> section [flags]" or "skipped #N: reason".
set -euo pipefail
dir=$(cd "$(dirname "$0")" && pwd)
. "$dir/lib.sh"

[ "$#" -eq 2 ] || { echo "usage: $0 PR_NUMBER NEXT_FILE" >&2; exit 2; }
pr=$1 next=$2
case $pr in '' | *[!0-9]*) echo "PR_NUMBER must be a number: $pr" >&2; exit 2 ;; esac
[ -f "$next" ] || { echo "missing file: $next" >&2; exit 2; }
repo=${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}

if file_has_pr "$next" "$pr"; then echo "skipped #$pr: already in next file"; exit 0; fi

data=$(gh pr view "$pr" --repo "$repo" --json number,title,body,url,headRefName,isCrossRepository,labels,mergedAt)
jq -e . > /dev/null 2>&1 <<<"$data" || { echo "gh returned no usable data for #$pr" >&2; exit 1; }
title=$(jq -r '.title' <<<"$data")
body=$(jq -r '.body // ""' <<<"$data")
url=$(jq -r '.url' <<<"$data")
head=$(jq -r '.headRefName' <<<"$data")
fork=$(jq -r '.isCrossRepository // false' <<<"$data")

if [ "$(jq -r '.mergedAt // ""' <<<"$data")" = "" ]; then echo "skipped #$pr: not merged"; exit 0; fi
if reason=$(skip_reason "$title" "$body" "$head" "$fork"); then echo "skipped #$pr: $reason"; exit 0; fi

flags="" rc=0
hint=$(jq -r '(.labels // [])[].name' <<<"$data" | "$dir/classify-pr.sh") || rc=$?
case "$rc" in
  0) ;;
  10) hint=""; flags="unlabelled" ;;
  11) hint=""; flags="conflicting-labels" ;;
  *) echo "label classification failed for #$pr (exit $rc)" >&2; exit 1 ;;
esac

# Only a PR with no label at all can be dropped as internal: any release-note:* label includes it.
if [ "$rc" = 10 ] && is_internal_pr "$title" "$head"; then
  echo "skipped #$pr: internal change (docs/test/ci/chore/build/style or Dependabot); add a release-note:* label to include it"
  exit 0
fi

# The skip rules above saw the whole body; the bullet needs only the top of it, and a huge
# environment string would exceed the 128 KiB per-variable limit on Linux.
built=$(PR_TITLE="$title" PR_BODY="${body:0:20000}" HINT_SECTION="$hint" "$dir/build-bullet.sh")
bullet=$(jq -r '.bullet' <<<"$built")
short=$(jq -r '.short' <<<"$built")
section=$(jq -r '.section' <<<"$built")

line="* ${bullet} ([#${pr}](${url})) {/* short: ${short} */}"
for f in $flags; do line="$line {/* flag: $f */}"; done

"$dir/append-bullet.sh" "$next" "$section" "$line"
echo "appended #$pr -> $section [${flags# }]"

if [ "${COMMENT_ON_UNLABELLED:-0}" = 1 ] && [ -n "$flags" ]; then
  case "$flags" in
    unlabelled) problem="This PR has no \`release-note:*\` label" ;;
    *) problem="This PR has more than one \`release-note:*\` label" ;;
  esac
  gh pr comment "$pr" --repo "$repo" --body "$problem, so its section was guessed from the title and placed under **$(section_heading "$section")**. If that is wrong, keep exactly one of \`release-note:feature\`, \`release-note:enhancement\`, \`release-note:bug\` or \`release-note:security\` and tell the releaser, or edit the draft docs PR (adding a label after merge does not change the bullet). Add \`[skip-docs]\` to the PR description to exclude a PR from release notes." || true
fi
