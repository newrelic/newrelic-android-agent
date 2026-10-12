#!/usr/bin/env bash
# Print the unique PR numbers (ascending, one per line) found in GitHub generated release notes
# read from stdin. Matches ".../pull/<n>" links.
set -euo pipefail
export LC_ALL=C # ASCII patterns only; see lib.sh
{ grep -oE '/pull/[0-9]+' || true; } | sed 's|/pull/||' | sort -un
