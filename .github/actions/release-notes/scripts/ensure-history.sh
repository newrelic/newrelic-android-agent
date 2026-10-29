#!/usr/bin/env bash
# Make sure the agent repo checkout has its full history and every tag: the already-released check
# and the release build need both, and actions/checkout fetches neither by default. The agent repos
# are public, so the fetch needs no credentials (callers check out with persist-credentials: false).
# Usage: ensure-history.sh      (run inside the checkout)
set -euo pipefail
if [ "$(git rev-parse --is-shallow-repository)" = true ]; then
  git fetch --quiet --unshallow --tags origin
else
  git fetch --quiet --tags origin
fi
