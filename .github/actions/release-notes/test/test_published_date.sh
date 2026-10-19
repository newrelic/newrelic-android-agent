#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
export GITHUB_REPOSITORY=newrelic/example-agent
. "$TEST_DIR/fake-gh.sh"
trap 'rm -rf "$FAKE_GH"' EXIT
pd() { "$SCRIPTS/published-date.sh" "$@" 2> /dev/null; }

printf '{"tag_name":"7.8.4","draft":false,"published_at":"2026-09-29T13:18:12Z"}' > "$FAKE_GH/release-7.8.4.json"
printf '{"tag_name":"v1.9.3","draft":false,"published_at":"2026-10-20T08:00:00Z"}' > "$FAKE_GH/release-v1.9.3.json"
printf '{"tag_name":"v1.9.4","draft":true,"published_at":null}' > "$FAKE_GH/release-v1.9.4.json"

assert_eq "a published release gives its publish date" 2026-09-29 "$(pd --prefix '' --version 7.8.4)"
assert_eq "the tag prefix is applied" 2026-10-20 "$(pd --prefix v --version 1.9.3)"
assert_eq "a version typed as its tag works" 2026-10-20 "$(pd --prefix v --version v1.9.3)"
assert_eq "a draft release gives nothing" "" "$(pd --prefix v --version 1.9.4)"
out=$(pd --prefix v --version 1.9.5); rc=$?
assert_eq "no release gives nothing" "" "$out"
assert_eq "no release still exits 0 (the caller falls back to the tag date)" 0 "$rc"
assert_contains "it asks for the release of the tag" "$(cat "$FAKE_GH/calls.log")" "api repos/newrelic/example-agent/releases/tags/v1.9.3"

pd --prefix v1 --version 1.9.3 > /dev/null; assert_eq "a bad prefix exits 2" 2 "$?"
pd --prefix v --version 1.9 > /dev/null; assert_eq "a bad version exits 2" 2 "$?"
pd --version 1.9.3 > /dev/null; assert_eq "a missing --prefix exits 2" 2 "$?"
(unset GITHUB_REPOSITORY; pd --prefix v --version 1.9.3 > /dev/null); assert_eq "no GITHUB_REPOSITORY fails" 1 "$?"

finish
