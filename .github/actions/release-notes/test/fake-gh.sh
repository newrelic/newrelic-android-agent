#!/usr/bin/env bash
# shellcheck shell=bash
# A stand-in for gh in the build-release.sh and per-agent tests. Source it to put "gh" on PATH:
#   generate-notes prints $FAKE_GH/notes.txt ($FAKE_GH/notes-fail makes it fail every time,
#   $FAKE_GH/notes-fail-once only the next time, $FAKE_GH/notes-blank-once answers the next call
#   with an empty body and exit 0),
#   "pr view N" prints $FAKE_GH/pr-N.json through any --jq filter (no such file makes it fail),
#   "api repos/.../releases/tags/T" prints $FAKE_GH/release-T.json through its --jq filter,
#   "pr list" prints $FAKE_GH/pr-list.json (no file: no PRs) through its --jq filter,
#   "pr ready", "pr comment", "pr edit" and "auth setup-git" succeed silently,
#   "pr create" prints $FAKE_GH/pr-create-url (default .../pull/999); with $FAKE_GH/maintainer-fail
#   it fails like GitHub does unless --no-maintainer-edit is passed,
#   "api user" prints $FAKE_GH/user.json and "api repos/O/N" $FAKE_GH/repo-O-N.json, through --jq,
#   "api -X POST repos/O/N/merge-upstream" runs $FAKE_GH/merge-upstream.sh when there is one,
#   "api repos/O/N/contents/PATH" succeeds only when $FAKE_GH/on-base exists.
# $FAKE_GH/list-fail, ready-fail, comment-fail and merge-fail make the matching command fail.
# Every call is appended to $FAKE_GH/calls.log, and "$GH_TOKEN|$1 $2" to $FAKE_GH/tokens.log.
# The file given to --body-file is copied to $FAKE_GH/body.md.
FAKE_GH=$(mktemp -d)
mkdir "$FAKE_GH/bin"
cat > "$FAKE_GH/bin/gh" << 'STUB'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_GH/calls.log"
echo "${GH_TOKEN:-}|$1 $2" >> "$FAKE_GH/tokens.log"
prev=""; for a in "$@"; do [ "$prev" != --body-file ] || cp "$a" "$FAKE_GH/body.md"; prev=$a; done
jq_expr=""; prev=""; for a in "$@"; do [ "$prev" = --jq ] && jq_expr=$a; prev=$a; done
# out FILE: print FILE through the call's --jq filter, like gh does.
out() { if [ -n "$jq_expr" ]; then jq -r "$jq_expr" "$1"; else cat "$1"; fi; }
case "$*" in
  "api -X POST repos/"*/merge-upstream*)
    [ ! -e "$FAKE_GH/merge-fail" ] || { echo "gh: HTTP 409" >&2; exit 1; }
    [ ! -f "$FAKE_GH/merge-upstream.sh" ] || bash "$FAKE_GH/merge-upstream.sh"
    exit ;;
esac
case "$1 $2" in
  "api user")
    [ -f "$FAKE_GH/user.json" ] || { echo "gh: Bad credentials (HTTP 401)" >&2; exit 1; }
    out "$FAKE_GH/user.json" ;;
  "api -X")
    [ ! -e "$FAKE_GH/notes-fail" ] || exit 1
    if [ -e "$FAKE_GH/notes-fail-once" ]; then rm "$FAKE_GH/notes-fail-once"; exit 1; fi
    if [ -e "$FAKE_GH/notes-blank-once" ]; then rm "$FAKE_GH/notes-blank-once"; echo; exit 0; fi
    cat "$FAKE_GH/notes.txt" ;;
  "api repos/"*/releases/tags/*)
    # GET repos/OWNER/NAME/releases/tags/TAG: $FAKE_GH/release-TAG.json.
    tag=${2##*/releases/tags/}; f="$FAKE_GH/release-$tag.json"
    [ -f "$f" ] || { echo "gh: Not Found (HTTP 404)" >&2; exit 1; }
    out "$f" ;;
  "api repos/"*/contents/*)
    # GET repos/OWNER/NAME/contents/PATH: found only when $FAKE_GH/on-base exists.
    [ -e "$FAKE_GH/on-base" ] || { echo "gh: Not Found (HTTP 404)" >&2; exit 1; } ;;
  "api repos/"*)
    # GET repos/OWNER/NAME: $FAKE_GH/repo-OWNER-NAME.json.
    r=${2#repos/}; f="$FAKE_GH/repo-${r%%/*}-${r#*/}.json"
    [ -f "$f" ] || { echo "gh: Not Found (HTTP 404)" >&2; exit 1; }
    out "$f" ;;
  "pr view")
    [ -f "$FAKE_GH/pr-$3.json" ] || { echo "no such PR: $3" >&2; exit 1; }
    out "$FAKE_GH/pr-$3.json" ;;
  "pr list")
    [ ! -e "$FAKE_GH/list-fail" ] || { echo "gh: HTTP 502" >&2; exit 1; }
    f="$FAKE_GH/pr-list.json"; [ -f "$f" ] || { f="$FAKE_GH/no-prs.json"; echo '[]' > "$f"; }
    out "$f" ;;
  "pr ready" | "pr comment")
    [ ! -e "$FAKE_GH/$2-fail" ] || { echo "gh: HTTP 403" >&2; exit 1; } ;;
  "pr edit" | "auth setup-git") ;;
  "pr create")
    case " $* " in
      *" --no-maintainer-edit "*) ;;
      *) [ ! -e "$FAKE_GH/maintainer-fail" ] || {
           echo "pull request create failed: GraphQL: Fork collab can't be granted by someone without permission (createPullRequest)" >&2
           exit 1
         } ;;
    esac
    cat "$FAKE_GH/pr-create-url" 2> /dev/null || echo "https://github.com/newrelic/docs-website/pull/999" ;;
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
