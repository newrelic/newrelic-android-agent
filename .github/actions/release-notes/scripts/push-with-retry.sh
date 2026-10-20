#!/usr/bin/env bash
# Push HEAD to BRANCH on origin, rebasing onto the remote branch and retrying when the push is
# rejected (another run, or a person editing the draft PR, pushed first). Three attempts.
# Usage: push-with-retry.sh BRANCH      (run inside the docs-website clone)
set -euo pipefail
[ "$#" -eq 1 ] || { echo "usage: $0 BRANCH" >&2; exit 2; }
for attempt in 1 2 3; do
  git push origin "HEAD:refs/heads/$1" && exit 0
  [ "$attempt" -lt 3 ] || break
  echo "Push rejected (attempt $attempt); rebasing onto origin/$1."
  git pull --rebase origin "$1"
done
echo "::error::Could not push to $1 after 3 attempts"
exit 1
