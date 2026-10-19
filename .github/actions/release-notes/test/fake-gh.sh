#!/usr/bin/env bash
# shellcheck shell=bash
# A stand-in for gh in the build-release.sh and per-agent tests. Source it to put "gh" on PATH:
#   generate-notes prints $FAKE_GH/notes.txt ($FAKE_GH/notes-fail makes it fail every time,
#   $FAKE_GH/notes-fail-once only the next time, $FAKE_GH/notes-blank-once answers the next call
#   with an empty body and exit 0),
#   "pr view N" prints $FAKE_GH/pr-N.json (no such file makes it fail).
# Every call is appended to $FAKE_GH/calls.log.
FAKE_GH=$(mktemp -d)
mkdir "$FAKE_GH/bin"
cat > "$FAKE_GH/bin/gh" << 'STUB'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_GH/calls.log"
case "$1 $2" in
  "api -X")
    [ ! -e "$FAKE_GH/notes-fail" ] || exit 1
    if [ -e "$FAKE_GH/notes-fail-once" ]; then rm "$FAKE_GH/notes-fail-once"; exit 1; fi
    if [ -e "$FAKE_GH/notes-blank-once" ]; then rm "$FAKE_GH/notes-blank-once"; echo; exit 0; fi
    cat "$FAKE_GH/notes.txt" ;;
  "pr view")
    [ -f "$FAKE_GH/pr-$3.json" ] || { echo "no such PR: $3" >&2; exit 1; }
    cat "$FAKE_GH/pr-$3.json" ;;
  *) echo "fake gh: unexpected call: $*" >&2; exit 1 ;;
esac
STUB
chmod +x "$FAKE_GH/bin/gh"
export FAKE_GH PATH="$FAKE_GH/bin:$PATH"

# fake_pr NUMBER TITLE BODY HEAD LABELS-JSON [FORK]: a merged PR that "gh pr view" returns.
fake_pr() {
  jq -n --argjson n "$1" --arg t "$2" --arg b "$3" --arg h "$4" --argjson l "$5" --argjson f "${6:-false}" \
    --arg repo "$GITHUB_REPOSITORY" \
    '{number:$n,title:$t,body:$b,headRefName:$h,isCrossRepository:$f,mergedAt:"2026-10-01T00:00:00Z",
      url:("https://github.com/"+$repo+"/pull/"+($n|tostring)),labels:($l|map({name:.}))}' > "$FAKE_GH/pr-$1.json"
}
# fake_notes NUMBER...: GitHub's generated release notes listing those PRs.
fake_notes() {
  { echo "## What's Changed"
    for n in "$@"; do echo "* change by @dev in https://github.com/$GITHUB_REPOSITORY/pull/$n"; done
    echo; echo "**Full Changelog**: https://github.com/$GITHUB_REPOSITORY/compare/a...b"; } > "$FAKE_GH/notes.txt"
}
