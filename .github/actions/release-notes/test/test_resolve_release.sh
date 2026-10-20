#!/usr/bin/env bash
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
repo="$tmp/repo"; out="$tmp/out"; mkdir -p "$repo" "$out"
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.com GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.com
g() { git -C "$repo" "$@" > /dev/null; }
commit() { # date message
  GIT_AUTHOR_DATE="$1T12:00:00Z" GIT_COMMITTER_DATE="$1T12:00:00Z" g commit -q --allow-empty -m "$2"
}
g init -q
commit 2026-09-01 one; g tag v1.9.1; g tag 1.9.0; g tag nightly-2026-09-02
printf '## 1.9.2\n- Two.\n' > "$repo/CHANGELOG.md"; printf '{"name":"pkg"}' > "$repo/package.json"
g add .; commit 2026-09-14 two; g tag v1.9.2; g tag 1.9.5
cp "$AGENTS/android/android-agent-next.mdx" "$repo/next.mdx"; g add .; commit 2026-10-01 skeleton; g tag v1.9.3
commit 2026-10-02 later

rr() { (cd "$repo" && "$SCRIPTS/resolve-release.sh" "$@" 2>"$tmp/err"); }
res=$(rr --prefix v --version 1.9.3 --skeleton next.mdx --out "$out" --changelog CHANGELOG.md)
assert_eq "prints the release facts" "version=1.9.3
tag=v1.9.3
prev_tag=v1.9.2
date=2026-10-01
slug=193
dashed=1-9-3" "$res"
assert_eq "tail is what follows the end marker, from the tag" "$(sed -n '/release-note:end/,$p' "$AGENTS/android/android-agent-next.mdx" | sed 1d)" "$(cat "$out/tail.mdx")"
assert_eq "CHANGELOG is taken from the tag" "## 1.9.2
- Two." "$(cat "$out/CHANGELOG.md")"
assert_eq "package.json is taken from the tag" '{"name":"pkg"}' "$(cat "$out/package.json")"

assert_contains "a tag-shaped version is accepted" "$(rr --prefix v --version v1.9.3 --skeleton next.mdx --out "$out")" "tag=v1.9.3"
assert_contains "--date overrides the commit date" "$(rr --prefix v --version 1.9.3 --skeleton next.mdx --out "$out" --date 2026-10-09)" "date=2026-10-09"
assert_contains "nightly and bare tags are not the previous release" "$(rr --prefix v --version 1.9.2 --skeleton next.mdx --out "$out")" "prev_tag=v1.9.1"

# A tag from before the skeleton was committed falls back to the working tree, with a notice.
printf 'working tree tail\n' > "$tmp/tail-src"; { cat "$SKELETON"; cat "$tmp/tail-src"; } > "$repo/next.mdx"
rr --prefix v --version 1.9.2 --skeleton next.mdx --out "$out" > /dev/null
assert_eq "an old tag uses the working-tree skeleton's tail" "working tree tail" "$(cat "$out/tail.mdx")"
assert_contains "and says so" "$(cat "$tmp/err")" "is not in v1.9.2"
git -C "$repo" checkout -q -- next.mdx

# Empty prefix (Android, .NET MAUI): only bare tags count.
res=$(rr --prefix '' --version 1.9.5 --skeleton next.mdx --out "$out")
assert_contains "--prefix '' resolves a bare tag" "$res" "tag=1.9.5"
assert_contains "--prefix '' ignores v tags for the previous release" "$res" "prev_tag=1.9.0"
rr --prefix '' --version 1.9.0 --skeleton next.mdx --out "$out" > /dev/null; assert_eq "--prefix '': no bare tag below 1.9.0 exits 1" 1 "$?"

rr --prefix v --version 1.9.9 --skeleton next.mdx --out "$out" > /dev/null; assert_eq "a missing tag exits 1" 1 "$?"
assert_contains "a missing tag is named" "$(cat "$tmp/err")" "tag v1.9.9 does not exist"
rr --prefix v --version 1.9.1 --skeleton next.mdx --out "$out" > /dev/null; assert_eq "no previous release exits 1" 1 "$?"
rr --prefix v --version 1.9.1 --skeleton next.mdx --out "$out" --changelog CHANGELOG.md > /dev/null; assert_eq "a CHANGELOG missing at the tag exits 1" 1 "$?"
rr --prefix v --version 1.9 --skeleton next.mdx --out "$out" > /dev/null; assert_eq "a bad version exits 2" 2 "$?"
rr --prefix v --version nightly-2026-09-02 --skeleton next.mdx --out "$out" > /dev/null; assert_eq "a nightly tag exits 2" 2 "$?"
rr --prefix v --version 1.9.3 --skeleton next.mdx --out "$out" --date 2026-10 > /dev/null; assert_eq "a bad date exits 2" 2 "$?"
rr --prefix v1 --version 1.9.3 --skeleton next.mdx --out "$out" > /dev/null; assert_eq "a bad prefix exits 2" 2 "$?"
rr --prefix v --version 1.9.3 --skeleton next.mdx --out "$tmp/nope" > /dev/null; assert_eq "a missing --out directory exits 2" 2 "$?"
rr --prefix v --version 1.9.3 --out "$out" > /dev/null; assert_eq "a missing --skeleton exits 2" 2 "$?"

finish
