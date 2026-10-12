#!/usr/bin/env bash
# Build and append the release-note bullet for one merged PR.
# Usage: process-pr.sh PR_NUMBER NEXT_FILE
# Env:   GITHUB_REPOSITORY (owner/name), GH_TOKEN for gh,
#        COMMENT_ON_UNLABELLED=1 to ask for a label on the PR when it has none.
# Prints one status line: "appended #N -> section [flags]" or "skipped #N: reason".
set -euo pipefail
dir=$(cd "$(dirname "$0")" && pwd)
. "$dir/lib.sh"

[ "$#" -eq 2 ] || { echo "usage: $0 PR_NUMBER NEXT_FILE" >&2; exit 2; }
pr=$1 next=$2
repo=${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}

if file_has_pr "$next" "$pr"; then echo "skipped #$pr: already in next file"; exit 0; fi

data=$(gh pr view "$pr" --repo "$repo" --json number,title,body,url,headRefName,labels,mergedAt)
title=$(jq -r '.title' <<<"$data")
body=$(jq -r '.body // ""' <<<"$data")
url=$(jq -r '.url' <<<"$data")
head=$(jq -r '.headRefName' <<<"$data")

if [ "$(jq -r '.mergedAt // ""' <<<"$data")" = "" ]; then echo "skipped #$pr: not merged"; exit 0; fi
if reason=$(skip_reason "$title" "$body" "$head"); then echo "skipped #$pr: $reason"; exit 0; fi

flags=""
if hint=$(jq -r '.labels[].name' <<<"$data" | "$dir/classify-pr.sh"); then
  :
else
  hint=""; flags="unlabelled"
fi

if [ -z "$hint" ] && is_internal_pr "$title" "$head"; then
  echo "skipped #$pr: internal change (docs/test/ci/chore/build/style or Dependabot); add a release-note:* label to include it"
  exit 0
fi

built=$(PR_TITLE="$title" PR_BODY="$body" HINT_SECTION="$hint" "$dir/build-bullet.sh")
bullet=$(jq -r '.bullet' <<<"$built")
short=$(jq -r '.short' <<<"$built")
section=$(jq -r '.section' <<<"$built")

line="* ${bullet} ([#${pr}](${url})) {/* short: ${short} */}"
for f in $flags; do line="$line {/* flag: $f */}"; done

"$dir/append-bullet.sh" "$next" "$section" "$line"
echo "appended #$pr -> $section [${flags# }]"

if [ "${COMMENT_ON_UNLABELLED:-0}" = 1 ] && [ -n "$flags" ] && [ -z "$hint" ]; then
  gh pr comment "$pr" --repo "$repo" --body "This PR has no \`release-note:*\` label, so its section was guessed from the title and placed under **$(section_heading "$section")**. If that is wrong, add exactly one of \`release-note:feature\`, \`release-note:enhancement\`, \`release-note:bug\` or \`release-note:security\` and tell the releaser, or edit the draft docs PR. Add \`[skip-docs]\` to the PR description to exclude a PR from release notes." || true
fi
