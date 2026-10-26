#!/usr/bin/env bash
# Every agent's sample config and skeleton (agents/<agent>/) run through the release build on the
# same fixed inputs, compared with expected/<agent>.mdx. Any behaviour change shows up as a diff
# for every agent. After an intended change: UPDATE_EXPECTED=1 bash test/test_agents.sh, then read
# the diff of expected/ before committing it.
. "$(dirname "$0")/helpers.sh"
. "$SCRIPTS/lib.sh"
export GITHUB_REPOSITORY=newrelic/example-agent RELEASE_NOTES_RETRY_DELAY=0
. "$TEST_DIR/fake-gh.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp" "$FAKE_GH"' EXIT
u=https://github.com/$GITHUB_REPOSITORY/pull
fake_notes 10 11 12
fake_pr 12 'fix: keep the session id across restarts' '' NR-12 '["release-note:bug"]' true

expect_name() { # agent file-name release-branch
  case $1 in
    android) echo "android-193.mdx android_release_notes_1-9-3" ;;
    react-native) echo "react-native-agent-193.mdx react-native_release_notes_1-9-3" ;;
    cordova) echo "cordova-agent-193.mdx cordova_release_notes_1-9-3" ;;
    capacitor) echo "capacitor-agent-193.mdx capacitor_release_notes_1-9-3" ;;
    flutter) echo "flutter-agent-193.mdx flutter_release_notes_1-9-3" ;;
    unity) echo "unity-agent-193.mdx unity_release_notes_1-9-3" ;;
    net-maui) echo "net-maui-agent-193.mdx net-maui_release_notes_1-9-3" ;;
  esac
}

for a in android react-native cordova capacitor flutter unity net-maui; do
  d="$AGENTS/$a"; w="$tmp/$a"; mkdir -p "$w"
  cfg=$(cd "$d" && "$SCRIPTS/config.sh" release-notes.json)
  val() { printf '%s\n' "$cfg" | sed -n "s/^$1=//p"; }
  prefix=$(val tag_prefix)
  assert_eq "$a: file and release-branch names" "$(expect_name "$a")" "$(val file_prefix)193.mdx $(val release_branch_prefix)1-9-3"

  # The next branch already holds two bullets; #12 (a fork PR) is only in the release.
  cp "$d/$(val skeleton)" "$w/accumulated.mdx"
  "$SCRIPTS/append-bullet.sh" "$w/accumulated.mdx" feature "* Added offline storage. ([#10]($u/10)) {/* short: Added offline storage */}"
  "$SCRIPTS/append-bullet.sh" "$w/accumulated.mdx" bug "* Fixed a crash on startup. ([#11]($u/11)) {/* short: Fixed a crash on startup */}"
  skeleton_tail "$d/$(val skeleton)" > "$w/tail.mdx"
  args=(--subject "$(val subject)" --version 1.9.3 --tag "${prefix}1.9.3" --prev-tag "${prefix}1.9.2"
    --date 2026-10-20 --next "$w/accumulated.mdx" --out "$w/out.mdx" --work "$w" --tail-file "$w/tail.mdx"
    --fallback-text "$(val fallback_text)")
  [ -z "$(val changelog)" ] || args+=(--changelog "$d/$(val changelog)")
  if [ -n "$(val download_link)" ]; then
    args+=(--download-link "$("$SCRIPTS/download-link.sh" "$(val download_link)" 1.9.3 "${prefix}1.9.3" "$d/package.json")")
  fi
  "$SCRIPTS/build-release.sh" "${args[@]}" > "$w/log.txt" 2>&1
  assert_eq "$a: the release builds" 0 "$?"

  if [ "${UPDATE_EXPECTED:-0}" = 1 ]; then cp "$w/out.mdx" "$TEST_DIR/expected/$a.mdx"; fi
  if diff -u "$TEST_DIR/expected/$a.mdx" "$w/out.mdx" > "$w/diff.txt"; then r=same; else r=different; cat "$w/diff.txt"; fi
  assert_eq "$a: matches expected/$a.mdx" same "$r"
done

finish
