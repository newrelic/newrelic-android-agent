#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
f="$tmp/next.mdx"
u=https://github.com/newrelic/newrelic-android-agent/pull
cat > "$f" <<EOT
## Bug fixes

* One ([#1]($u/1))
* Two ([#2]($u/2))
* Hand written, no link

{/* release-note:end */}
EOT

assert_eq "keep 1 keeps #1 and hand-written bullet" \
  "$(printf '%s\n' '## Bug fixes' '' "* One ([#1]($u/1))" '* Hand written, no link' '' '{/* release-note:end */}')" \
  "$("$SCRIPTS/filter-bullets.sh" "$f" keep 1)"
assert_eq "drop 1 leaves only #2" \
  "$(printf '%s\n' '## Bug fixes' '' "* Two ([#2]($u/2))" '' '{/* release-note:end */}')" \
  "$("$SCRIPTS/filter-bullets.sh" "$f" drop 1)"
assert_eq "keep with no PRs keeps only unlinked bullets" \
  "$(printf '%s\n' '## Bug fixes' '' '* Hand written, no link' '' '{/* release-note:end */}')" \
  "$("$SCRIPTS/filter-bullets.sh" "$f" keep)"
"$SCRIPTS/filter-bullets.sh" "$f" sideways 2>/dev/null; assert_eq "bad mode exits 2" 2 "$?"

finish
