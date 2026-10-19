#!/usr/bin/env bash
# Print the publish date (YYYY-MM-DD) of the GitHub release for one version's tag, for agents whose
# note is dated by when a person published the release (Android) rather than by the tagged commit.
# Usage: published-date.sh --prefix P --version V      (V bare or as the tag, as in resolve-release.sh)
# Env: GITHUB_REPOSITORY (owner/name), GH_TOKEN for gh.
# Prints nothing (exit 0) when there is no published release for the tag (none, a draft, or the API
# would not say): the caller then dates the note from the tag. Exit 2 on bad arguments.
set -euo pipefail
export LC_ALL=C # ASCII patterns only; see lib.sh

prefix="" version="" have_prefix=0
while [ "$#" -gt 0 ]; do
  [ "$#" -ge 2 ] || { echo "$1 needs a value" >&2; exit 2; }
  case "$1" in
    --prefix) prefix=$2; have_prefix=1 ;; --version) version=$2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift 2
done
[ "$have_prefix" = 1 ] || { echo "--prefix is required (it may be empty)" >&2; exit 2; }
[[ $prefix =~ ^[A-Za-z]*$ ]] || { echo "--prefix may only hold letters: $prefix" >&2; exit 2; }
version=${version#"$prefix"}
version=${version#v}
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "'$version' is not an X.Y.Z release version" >&2; exit 2; }
repo=${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}

at=$(gh api "repos/$repo/releases/tags/$prefix$version" --jq '.published_at // ""' 2> /dev/null) || at=""
case $at in
  [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T*) printf '%s\n' "${at%%T*}" ;;
esac
