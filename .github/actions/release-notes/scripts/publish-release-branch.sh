#!/usr/bin/env bash
# Put one release's docs-website note on its release branch and open (or update) the docs PR: a draft,
# or ready for review with --ready-for-review (readyForReview). An existing PR keeps its state.
# The branch goes to the head repo of the release's open PR when there is one, else to the bot's fork
# of docs-repo when FORK_PAT is set, else to docs-repo itself. The PR is always on docs-repo.
# Usage: publish-release-branch.sh --docs-repo OWNER/NAME --docs-base BRANCH --docs-dir DIR --file NAME
#          --source PATH --branch BRANCH --subject S --version V --body-file PATH [--overwrite]
#          [--ready-for-review] [--dry-run]
# Env: BOT_PAT    the docs-repo token: gh calls on docs-repo and pushes to it (not needed for --dry-run)
#      FORK_PAT   optional: the token of the account whose fork <login>/<docs-repo name> holds the
#                 branches; used to look the fork up, sync its base branch, and push to it
#      BOT_EMAIL  email for the bot's commit (not needed for --dry-run)
#      GH_TOKEN   for --dry-run, the read-only token for docs-repo lookups (default: BOT_PAT)
#      GIT_BASE   where repos are cloned from and pushed to (default https://github.com; tests use
#                 file://DIR). Pushes authenticate through gh's credential helper (gh auth setup-git).
# Prints target=<owner/name> first. Exit 0 done (a dry run only reports); 1 refused or failed;
# 2 bad arguments.
set -euo pipefail
export LC_ALL=C # ASCII patterns only; see lib.sh

docs_repo="" docs_base="" docs_dir="" file="" source="" branch="" subject="" version="" body_file=""
overwrite=false dry_run=false ready=false
while [ "$#" -gt 0 ]; do
  case "$1" in
    --overwrite) overwrite=true; shift; continue ;;
    --dry-run) dry_run=true; shift; continue ;;
    --ready-for-review) ready=true; shift; continue ;;
  esac
  [ "$#" -ge 2 ] || { echo "$1 needs a value" >&2; exit 2; }
  case "$1" in
    --docs-repo) docs_repo=$2 ;; --docs-base) docs_base=$2 ;; --docs-dir) docs_dir=$2 ;;
    --file) file=$2 ;; --source) source=$2 ;; --branch) branch=$2 ;; --subject) subject=$2 ;;
    --version) version=$2 ;; --body-file) body_file=$2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift 2
done
repo_re='^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$'
ref_re='^[A-Za-z0-9._/-]+$'
[[ $docs_repo =~ $repo_re ]] || { echo "--docs-repo must be owner/name, not '$docs_repo'" >&2; exit 2; }
[[ $docs_base =~ $ref_re ]] || { echo "--docs-base is not a branch name: '$docs_base'" >&2; exit 2; }
[[ $branch =~ $ref_re ]] || { echo "--branch is not a branch name: '$branch'" >&2; exit 2; }
for arg in "docs-dir=$docs_dir" "file=$file" "source=$source" "subject=$subject" "version=$version" \
  "body-file=$body_file"; do
  [ -n "${arg#*=}" ] || { echo "--${arg%%=*} is required" >&2; exit 2; }
done
GIT_BASE=${GIT_BASE:-https://github.com}
if [ "$dry_run" = true ]; then
  read_token=${GH_TOKEN:-${BOT_PAT:-}}
else
  [ -n "${BOT_PAT:-}" ] || { echo "BOT_PAT is required" >&2; exit 2; }
  [ -n "${BOT_EMAIL:-}" ] || { echo "BOT_EMAIL is required" >&2; exit 2; }
  read_token=$BOT_PAT
fi

# Sets fork (empty without FORK_PAT), target (the repo that gets the branch), and pr_number, pr_head
# and pr_url when the release already has an open docs PR.
fork="" target="" pr_number="" pr_head="" pr_url=""
resolve_target() {
  local login parents line
  if [ -n "${FORK_PAT:-}" ]; then
    login=$(GH_TOKEN=$FORK_PAT gh api user --jq .login) ||
      { echo "Could not look up the account of the fork token (BOT_FORK_PAT)" >&2; return 1; }
    fork="$login/${docs_repo#*/}"
    parents=$(GH_TOKEN=$FORK_PAT gh api "repos/$fork" --jq '"\(.parent.full_name) \(.source.full_name)"' \
      2> /dev/null) || parents=""
    case " $parents " in
      *" $docs_repo "*) ;;
      *)
        echo "$fork is not a fork of $docs_repo. Fork $docs_repo into $login's account under the name" \
          "${docs_repo#*/}, or remove BOT_FORK_PAT." >&2
        return 1
        ;;
    esac
  fi
  line=$(GH_TOKEN=$read_token gh pr list --repo "$docs_repo" --head "$branch" --state open \
    --json number,url,headRepositoryOwner,headRepository \
    --jq '(.[0] // empty) | "\(.number) \(.headRepositoryOwner.login)/\(.headRepository.name) \(.url)"') ||
    { echo "Could not look up the docs PR for $branch on $docs_repo" >&2; return 1; }
  if [ -n "$line" ]; then
    read -r pr_number pr_head pr_url <<< "$line"
    # Update an open PR where its branch lives; anything else would leave a second branch or PR.
    if [ "$pr_head" != "$docs_repo" ] && [ "$pr_head" != "$fork" ]; then
      echo "$pr_url is on $pr_head; set BOT_FORK_PAT (fork-pat) to update it." >&2
      return 1
    fi
    target=$pr_head
  elif [ -n "$fork" ]; then
    target=$fork
  else
    target=$docs_repo
  fi
}
resolve_target || exit 1
echo "target=$target"
[ -z "$pr_number" ] || echo "existing PR #$pr_number: $pr_url"

if [ "$dry_run" = true ]; then
  kind_of_pr="a draft PR"
  [ "$ready" = false ] || kind_of_pr="a PR ready for review"
  plan="A real run would push $branch to $target and open $kind_of_pr on $docs_repo"
  [ -z "$pr_number" ] || plan="A real run would push $branch to $target and update $pr_url"
  echo "::notice::$plan."
  [ -z "${GITHUB_STEP_SUMMARY:-}" ] || printf '\n%s.\n' "$plan" >> "$GITHUB_STEP_SUMMARY"
  if GH_TOKEN=$read_token gh api "repos/$docs_repo/contents/$docs_dir/$file?ref=$docs_base" --silent 2> /dev/null; then
    echo "::notice::$file is already on $docs_repo $docs_base; a real run would stop there unless overwrite is true."
  fi
  if git ls-remote --exit-code --heads "$GIT_BASE/$target.git" "$branch" > /dev/null 2>&1; then
    echo "::notice::$branch already exists on $target; a real run would reuse it only if its content is identical."
  fi
  exit 0
fi

# The clone moves the working directory; keep the input files reachable.
source="$(cd "$(dirname "$source")" && pwd)/$(basename "$source")"
body_file="$(cd "$(dirname "$body_file")" && pwd)/$(basename "$body_file")"
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
if [ -n "$fork" ] && [ "$target" = "$fork" ]; then kind=fork push_token=$FORK_PAT; else kind=bot push_token=$BOT_PAT; fi

GH_TOKEN=$BOT_PAT git clone -q --depth 1 --branch "$docs_base" "$GIT_BASE/$docs_repo.git" "$work/docs"
cd "$work/docs"
if [ -e "$docs_dir/$file" ] && [ "$overwrite" != true ]; then
  echo "::error::$file already exists on $docs_repo $docs_base. Run again with overwrite=true to replace it." >&2
  exit 1
fi
mkdir -p "$docs_dir"
cp "$source" "$docs_dir/$file"
git config user.name newrelic-mobile-agent-bot
git config user.email "$BOT_EMAIL"
# The branch sits on docs-repo's base tip, so the fork's base must have that commit for the push to
# be accepted. Its Workflows permission covers upstream commits that change .github/workflows/.
if [ "$kind" = fork ]; then
  GH_TOKEN=$FORK_PAT gh api -X POST "repos/$fork/merge-upstream" -f branch="$docs_base" > /dev/null ||
    { echo "::error::Could not sync $fork $docs_base with $docs_repo (merge-upstream)" >&2; exit 1; }
fi
git remote add target "$GIT_BASE/$target.git"

# Never overwrite a release-notes branch a person may have edited: the docs PR is where the
# mandatory edit pass happens. Identical content is reused, so a re-run after a half-finished run
# is safe.
commit_and_push() { # push-args...
  git checkout -q -b "$branch"
  git add "$docs_dir/$file"
  git commit -q -m "docs: add $subject $version release notes"
  echo "push $target $branch with $kind token"
  GH_TOKEN=$push_token git push -q "$@" target "HEAD:refs/heads/$branch"
}
rc=0
GH_TOKEN=$push_token git ls-remote --exit-code --heads target "$branch" > /dev/null 2>&1 || rc=$?
case "$rc" in
  2) commit_and_push ;;
  0)
    GH_TOKEN=$push_token git fetch -q --depth 1 target "$branch"
    if git show "FETCH_HEAD:$docs_dir/$file" 2> /dev/null | cmp -s - "$docs_dir/$file"; then
      echo "$branch already holds identical notes; reusing it."
    elif [ "$overwrite" = true ]; then
      commit_and_push --force
    else
      echo "::error::$branch already exists on $target with different content (it may hold a person's edits)." \
        "Delete the branch and its PR to regenerate, or run again with overwrite=true to discard them." >&2
      exit 1
    fi
    ;;
  *) echo "::error::Could not check for $branch on $target (git ls-remote exit $rc)" >&2; exit 1 ;;
esac

if [ -n "$pr_number" ]; then
  GH_TOKEN=$BOT_PAT gh pr edit "$pr_number" --repo "$docs_repo" --body-file "$body_file" > /dev/null
  echo "updated #$pr_number"
  exit 0
fi
head_ref=$branch
[ "$target" = "$docs_repo" ] || head_ref="${target%%/*}:$branch"
draft=(--draft) create_body=$body_file
if [ "$ready" = true ]; then
  draft=() create_body="$work/body.md"
  { cat "$body_file"; printf '\n%s\n' "Opened ready for review: readyForReview is set for this agent."; } > "$create_body"
fi
create_pr() { # extra-args...
  # ${draft[@]+...}: bash 3.2 treats an empty array as unset under set -u.
  GH_TOKEN=$BOT_PAT gh pr create --repo "$docs_repo" --base "$docs_base" --head "$head_ref" ${draft[@]+"${draft[@]}"} \
    --title "$subject $version release notes" --label documentation --body-file "$create_body" "$@"
}
if ! url=$(create_pr 2> "$work/create.err"); then
  # Allowing maintainer edits needs push access to the head repo, which the org-scoped BOT_PAT
  # lacks on the bot's own fork. The team still edits on the fork; the docs team can suggest.
  grep -q "Fork collab" "$work/create.err" || { cat "$work/create.err" >&2; exit 1; }
  url=$(create_pr --no-maintainer-edit)
  echo "::notice::Opened without \"allow edits by maintainers\" (GitHub refused it for this token); the docs team can suggest changes instead."
fi
echo "opened $url"
