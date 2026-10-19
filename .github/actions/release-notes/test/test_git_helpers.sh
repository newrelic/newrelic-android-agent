#!/usr/bin/env bash
# ensure-history.sh and push-with-retry.sh against local repositories.
. "$(dirname "$0")/helpers.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.com GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.com
q() { "$@" > /dev/null 2>&1; }

git init -q "$tmp/src"
for i in 1 2 3; do q git -C "$tmp/src" commit --allow-empty -m "c$i"; q git -C "$tmp/src" tag "v1.0.$i"; done
q git clone --depth 1 "file://$tmp/src" "$tmp/shallow"
q git -C "$tmp/shallow" tag -d v1.0.3
(cd "$tmp/shallow" && "$SCRIPTS/ensure-history.sh")
assert_eq "a shallow clone is unshallowed" false "$(git -C "$tmp/shallow" rev-parse --is-shallow-repository)"
assert_eq "every tag is fetched" "v1.0.1 v1.0.2 v1.0.3" "$(git -C "$tmp/shallow" tag --list | tr '\n' ' ' | sed 's/ $//')"
(cd "$tmp/shallow" && "$SCRIPTS/ensure-history.sh"); assert_eq "a full clone is left as it is" 0 "$?"

# push-with-retry.sh: a rejected push is rebased onto the remote and retried.
git init -q --bare "$tmp/remote.git"
q git clone "$tmp/remote.git" "$tmp/a"; q git -C "$tmp/a" commit --allow-empty -m base; q git -C "$tmp/a" push origin HEAD:refs/heads/next
q git clone --branch next "$tmp/remote.git" "$tmp/b"
q git -C "$tmp/a" commit --allow-empty -m "from a"; q git -C "$tmp/a" push origin HEAD:refs/heads/next
q git -C "$tmp/b" commit --allow-empty -m "from b"
(cd "$tmp/b" && "$SCRIPTS/push-with-retry.sh" next > /dev/null 2>&1); assert_eq "a rejected push is retried after a rebase" 0 "$?"
assert_eq "both commits are on the branch" "from b from a base" "$(git -C "$tmp/remote.git" log --format=%s next | tr '\n' ' ' | sed 's/ $//')"
(cd "$tmp/b" && "$SCRIPTS/push-with-retry.sh" > /dev/null 2>&1); assert_eq "no branch exits 2" 2 "$?"

finish
