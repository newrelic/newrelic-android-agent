#!/usr/bin/env bash
# Mark one release's docs-website PR, which generate opens as a draft, ready for review once a person
# has checked the note, and say on the PR who did. People without write access to docs-website can't
# do this themselves; the PR's author (the bot) can.
# Usage: mark-ready.sh --repo OWNER/NAME --branch-prefix P --prefix T --version V [--actor LOGIN]
#   --branch-prefix  the release branch prefix from config.sh (react-native_release_notes_)
#   --prefix         the tag prefix (v, or empty); V may be bare (1.9.3) or the tag (v1.9.3)
#   --actor          the person who checked the note, named in the comment
# Env: GH_TOKEN for gh, the token that opened the PR. GITHUB_SERVER_URL, GITHUB_REPOSITORY and
#      GITHUB_RUN_ID, when all set, link the run from the comment.
# Prints one line with the PR's URL. Exit 0 when the PR is ready (now or already); 1 when there is
# no open PR for the release or gh fails; 2 on bad arguments.
set -euo pipefail
export LC_ALL=C # ASCII patterns only; see lib.sh

repo="" branch_prefix="" prefix="" version="" actor="" have_prefix=0
while [ "$#" -gt 0 ]; do
  [ "$#" -ge 2 ] || { echo "$1 needs a value" >&2; exit 2; }
  case "$1" in
    --repo) repo=$2 ;; --branch-prefix) branch_prefix=$2 ;; --version) version=$2 ;;
    --prefix) prefix=$2; have_prefix=1 ;; --actor) actor=$2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift 2
done
[[ $repo =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || { echo "--repo must be owner/name, not '$repo'" >&2; exit 2; }
[[ $branch_prefix =~ ^[A-Za-z0-9._-]+$ ]] || { echo "--branch-prefix is not a branch name prefix: '$branch_prefix'" >&2; exit 2; }
[ "$have_prefix" = 1 ] || { echo "--prefix is required (it may be empty)" >&2; exit 2; }
[[ $prefix =~ ^[A-Za-z]*$ ]] || { echo "--prefix may only hold letters: $prefix" >&2; exit 2; }
version=${version#"$prefix"}
version=${version#v}
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "'$version' is not an X.Y.Z release version" >&2; exit 2; }
branch="$branch_prefix${version//./-}"

pr=$(gh pr list --repo "$repo" --head "$branch" --state open --json number,isDraft,url \
  --jq '(.[0] // empty) | "\(.number) \(.isDraft) \(.url)"') || {
  echo "Could not look up the docs PR for $branch on $repo" >&2; exit 1
}
if [ -z "$pr" ]; then
  echo "No open PR on $repo for $branch. Has generate run for $version, or was the PR already merged or closed?" >&2
  exit 1
fi
read -r number draft url <<< "$pr"

if [ "$draft" != true ]; then
  echo "Already ready for review: $url"
  exit 0
fi
gh pr ready "$number" --repo "$repo" > /dev/null || { echo "Could not mark $url ready for review" >&2; exit 1; }

body="Marked ready for review"
[ -z "$actor" ] || body="$body by @$actor"
if [ -n "${GITHUB_SERVER_URL:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ] && [ -n "${GITHUB_RUN_ID:-}" ]; then
  body="$body from $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
fi
# The PR is ready either way; the comment only records who checked the note.
gh pr comment "$number" --repo "$repo" --body "$body." > /dev/null ||
  echo "::warning::$url is ready for review, but the comment saying who checked it could not be posted." >&2
echo "Marked ready for review: $url"
