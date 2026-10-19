#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
dl() { "$SCRIPTS/download-link.sh" "$@" 2>&1; }
printf '{"name":"newrelic-react-native-agent","version":"1.9.3"}' > "$tmp/rn.json"
printf '{"name":"@newrelic/newrelic-capacitor-plugin"}' > "$tmp/cap.json"

assert_eq "react native: npm name and version" "https://www.npmjs.com/package/newrelic-react-native-agent/v/1.9.3" \
  "$(dl 'https://www.npmjs.com/package/{npmPackage}/v/{version}' 1.9.3 v1.9.3 "$tmp/rn.json")"
assert_eq "capacitor: a scoped npm name" "https://www.npmjs.com/package/@newrelic/newrelic-capacitor-plugin/v/1.6.13" \
  "$(dl 'https://www.npmjs.com/package/{npmPackage}/v/{version}' 1.6.13 v1.6.13 "$tmp/cap.json")"
assert_eq "unity: {tag} keeps the v" "https://github.com/newrelic/newrelic-unity-agent/releases/tag/v1.7.2" \
  "$(dl 'https://github.com/newrelic/newrelic-unity-agent/releases/tag/{tag}' 1.7.2 v1.7.2)"
assert_eq "flutter: {version} only" "https://pub.dev/packages/newrelic_mobile/versions/1.3.2" \
  "$(dl 'https://pub.dev/packages/newrelic_mobile/versions/{version}' 1.3.2 v1.3.2)"
assert_eq "android: no placeholder at all" "https://central.sonatype.com/artifact/com.newrelic.agent.android/android-agent" \
  "$(dl 'https://central.sonatype.com/artifact/com.newrelic.agent.android/android-agent' 7.8.4 7.8.4)"
assert_eq "a placeholder used twice is replaced twice" "https://x/1.0.0/1.0.0" "$(dl 'https://x/{version}/{version}' 1.0.0 1.0.0)"
assert_eq "package.json is not needed without {npmPackage}" "https://x/1.0.0" "$(dl 'https://x/{version}' 1.0.0 v1.0.0 "$tmp/missing.json")"

dl 'https://x/{npmPackage}' 1.0.0 v1.0.0 > /dev/null; assert_eq "{npmPackage} without a package.json exits 2" 2 "$?"
dl 'https://x/{npmPackage}' 1.0.0 v1.0.0 "$tmp/missing.json" > /dev/null; assert_eq "{npmPackage} with a missing package.json exits 2" 2 "$?"
printf '{"version":"1.0.0"}' > "$tmp/noname.json"
dl 'https://x/{npmPackage}' 1.0.0 v1.0.0 "$tmp/noname.json" > /dev/null; assert_eq "a package.json with no name exits 2" 2 "$?"
printf '{"name":"x'"'"'y"}' > "$tmp/quote.json"
dl 'https://x/{npmPackage}' 1.0.0 v1.0.0 "$tmp/quote.json" > /dev/null; assert_eq "a name with a quote exits 2" 2 "$?"
printf 'not json' > "$tmp/bad.json"
dl 'https://x/{npmPackage}' 1.0.0 v1.0.0 "$tmp/bad.json" > /dev/null; assert_eq "invalid package.json exits 2" 2 "$?"
out=$(dl 'https://x/{Version}' 1.0.0 v1.0.0); rc=$?
assert_eq "an unknown placeholder exits 2" 2 "$rc"
assert_contains "an unknown placeholder is named" "$out" "unresolved placeholder"
dl 'https://x/{version}' 1.0 v1.0 > /dev/null; assert_eq "a bad version exits 2" 2 "$?"
dl 'https://x/{tag}' 1.0.0 'v1.0.0&x' > /dev/null; assert_eq "a bad tag exits 2" 2 "$?"
dl 'https://x' 1.0.0 > /dev/null; assert_eq "too few arguments exits 2" 2 "$?"

finish
