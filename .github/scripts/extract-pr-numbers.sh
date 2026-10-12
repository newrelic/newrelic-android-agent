#!/usr/bin/env bash
# Print the unique PR numbers (ascending, one per line) found in GitHub generated release notes
# read from stdin. Matches ".../pull/<n>" links.
set -euo pipefail
{ grep -oE '/pull/[0-9]+' || true; } | sed 's|/pull/||' | sort -un
