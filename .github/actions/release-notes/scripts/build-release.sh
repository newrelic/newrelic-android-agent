#!/usr/bin/env bash
# Build one release's notes: reconcile the bullets accumulated on the docs-website next branch, every
# PR merged between the previous release tag and this one, and (optionally) the version's CHANGELOG
# entries, then write the release .mdx and the text of the docs-website PR.
# Usage: build-release.sh --subject S --version V --tag T --prev-tag P --date D --next FILE
#                         --out FILE --work DIR [--tail-file FILE] [--changelog FILE]
#                         [--download-link URL] [--fallback-text TEXT]
#   --next  the accumulated next-release file (or the skeleton when there is none); not modified
#   --work  an existing directory for intermediate files. On success it holds:
#             pr-body.md       the docs-website PR description
#             release-prs.txt  the release's PR numbers, one per line (pruned from the next branch)
# Env: GITHUB_REPOSITORY (owner/name), GH_TOKEN for gh (the run's token: this reads PR text).
#      GITHUB_SERVER_URL and GITHUB_RUN_ID, when set, link the run from the PR text.
#      RELEASE_NOTES_RETRY_DELAY: seconds between API retries (default 5; the tests use 0).
# A PR is never dropped without a trace: one left out is listed in the PR text, and one whose bullet
# could not be built is listed as missing. Prints a log on stdout (with ::warning:: annotations).
# Exit 0 done; 1 the release cannot be built (no PRs and no CHANGELOG, an API failure); 2 bad arguments.
set -euo pipefail
dir=$(cd "$(dirname "$0")" && pwd)
. "$dir/lib.sh"

subject="" version="" tag="" prev_tag="" date="" next="" out="" work="" tail_file="" changelog="" link=""
fallback="Updated version."
while [ "$#" -gt 0 ]; do
  [ "$#" -ge 2 ] || { echo "$1 needs a value" >&2; exit 2; }
  case "$1" in
    --subject) subject=$2 ;; --version) version=$2 ;; --tag) tag=$2 ;; --prev-tag) prev_tag=$2 ;;
    --date) date=$2 ;; --next) next=$2 ;; --out) out=$2 ;; --work) work=$2 ;;
    --tail-file) tail_file=$2 ;; --changelog) changelog=$2 ;; --download-link) link=$2 ;;
    --fallback-text) fallback=$2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift 2
done
for v in subject version tag prev_tag date next out work; do
  [ -n "${!v}" ] || { echo "--${v//_/-} is required" >&2; exit 2; }
done
[ -f "$next" ] || { echo "missing file: $next" >&2; exit 2; }
[ -d "$work" ] || { echo "--work must be an existing directory: $work" >&2; exit 2; }
[ -z "$changelog" ] || [ -r "$changelog" ] || { echo "cannot read $changelog" >&2; exit 2; }
fallback=$(oneline "$fallback")
[ -n "$fallback" ] || { echo "--fallback-text must not be blank" >&2; exit 2; }
repo=${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}

src="$work/release-src.mdx"
cp "$next" "$work/next.mdx"
: > "$work/failed-prs.txt"; : > "$work/skipped-prs.txt"; : > "$work/changelog.txt"; : > "$work/flags.txt"

# Source B: PRs merged between the two tags. The API fails now and then, by an error or by an
# empty answer (real generated notes are never empty), so it is tried three times.
for attempt in 1 2 3; do
  if gh api -X POST "repos/$repo/releases/generate-notes" \
    -f tag_name="$tag" -f previous_tag_name="$prev_tag" --jq .body > "$work/generated-notes.txt" \
    && grep -q '[^[:space:]]' "$work/generated-notes.txt"; then
    break
  fi
  if [ "$attempt" = 3 ]; then
    echo "::error::Could not list the PRs between $prev_tag and $tag (releases/generate-notes failed 3 times)"
    exit 1
  fi
  echo "releases/generate-notes failed (attempt $attempt); retrying."
  sleep $((attempt * ${RELEASE_NOTES_RETRY_DELAY:-5}))
done
"$dir/extract-pr-numbers.sh" < "$work/generated-notes.txt" > "$work/release-prs.txt"
prs=$(tr '\n' ' ' < "$work/release-prs.txt" | sed 's/ $//')
if [ -z "$prs" ]; then
  if [ -z "$changelog" ]; then echo "::error::No PRs found between $prev_tag and $tag"; exit 1; fi
  echo "::warning::No PRs found between $prev_tag and $tag; using the CHANGELOG only."
fi
echo "Release $tag (previous release $prev_tag). PRs in the release: ${prs:-none}"

# Build a bullet for every release PR that has none yet (fork PRs, or any the per-PR run missed).
for pr in $prs; do
  if status=$("$dir/process-pr.sh" "$pr" "$work/next.mdx"); then
    echo "$status"
    case "$status" in
      *"already in next file") ;;
      skipped*) printf '%s\n' "${status#skipped }" >> "$work/skipped-prs.txt" ;;
    esac
  else
    echo "::warning::Could not build a bullet for PR #$pr"
    echo "#$pr" >> "$work/failed-prs.txt"
  fi
done

# Keep only this release's bullets, then add the CHANGELOG entries no bullet already covers.
# shellcheck disable=SC2086 # $prs is a space-separated list of PR numbers
"$dir/filter-bullets.sh" "$work/next.mdx" keep $prs > "$src"
if [ -n "$changelog" ]; then
  "$dir/changelog-bullets.sh" "$version" "$changelog" "$src" > "$work/changelog.txt"
  cat "$work/changelog.txt"
fi

generate() {
  "$dir/generate-docs.sh" --next "$src" --subject "$subject" --version "$version" --date "$date" \
    --out "$out" --flags-out "$work/flags.txt" \
    ${link:+--download-link "$link"} ${tail_file:+--tail-file "$tail_file"}
}
used_fallback=0 rc=0
generate || rc=$?
if [ "$rc" = 3 ]; then
  # Still open the PR, for a person to fill in.
  echo "::warning::No release-note bullets for $version; using \"$fallback\""
  text=$(defuse_comments "$(mdx_escape "$(ensure_period "$fallback")")")
  "$dir/append-bullet.sh" --no-link "$src" enhancement "* $text {/* short: $(short_form "$(plain_short "$text")" 120) */}"
  used_fallback=1
  generate
elif [ "$rc" != 0 ]; then
  exit "$rc"
fi

# The docs-website PR description. "#107" there would link to docs-website's own #107, so every PR
# reference is qualified with this repository.
{
  echo "Release notes for **$subject $version**, generated from the PRs merged between \`$prev_tag\` and \`$tag\` in $repo."
  echo
  if [ -n "$changelog" ]; then from=", plus the version's CHANGELOG entries"; else from=""; fi
  echo "**A human edit pass is required before this merges.** Bullets were generated from each PR's title, its \`release-note:*\` label and any \`Release note\` text in its description$from; titles are developer-facing, so most bullets need rewording."
  echo
  echo "Check: every bullet is user-facing and specific, bug bullets say what was broken, each bullet sits in the right section, and the frontmatter arrays mirror the body."
  if [ -z "$prs" ]; then
    echo
    echo "### No PRs found"
    echo "GitHub listed no PRs between \`$prev_tag\` and \`$tag\`, so this note comes from the CHANGELOG only. Bullets still on the next branch were not included; add any that belong to this release by hand."
  fi
  if [ "$used_fallback" = 1 ]; then
    echo
    echo "### No bullets"
    echo "No PR or CHANGELOG entry produced a bullet, so the note only says \"$(ensure_period "$fallback")\". Replace it with what changed."
  fi
  if [ -s "$work/flags.txt" ]; then
    echo
    echo "### Bullets that need extra attention"
    while read -r pr flag; do
      case "$pr" in '#'[0-9]*) pr="$repo$pr" ;; esac
      case "$flag" in
        unlabelled) echo "- $pr had no \`release-note:*\` label; the section was guessed from the PR title." ;;
        conflicting-labels) echo "- $pr had more than one \`release-note:*\` label; the section was guessed from the PR title." ;;
        *) echo "- $pr: $flag" ;;
      esac
    done < "$work/flags.txt"
  fi
  if grep -q '^added' "$work/changelog.txt"; then
    echo
    echo "### From the CHANGELOG"
    echo "These entries matched no PR bullet and were added as written. Check that none rewords a PR bullet above it:"
    sed -n 's/^added \([a-z]*\): /- (\1) /p' "$work/changelog.txt"
  fi
  if [ -s "$work/skipped-prs.txt" ]; then
    echo
    echo "### PRs left out"
    echo "These PRs are in the release but have no bullet. Add one by hand if any of them is user-facing:"
    sed "s|^#|- $repo#|" "$work/skipped-prs.txt"
  fi
  if [ -s "$work/failed-prs.txt" ]; then
    echo
    echo "### Missing bullets"
    echo "A bullet could not be built for these PRs. Add them by hand if they are user-facing:"
    sed "s|^|- $repo|" "$work/failed-prs.txt"
  fi
  echo
  run=""
  [ -z "${GITHUB_RUN_ID:-}" ] || run=" in [run $GITHUB_RUN_ID](${GITHUB_SERVER_URL:-https://github.com}/$repo/actions/runs/$GITHUB_RUN_ID)"
  echo "Generated by the shared release-notes automation for $repo$run."
} > "$work/pr-body.md"
echo "Wrote $out and the docs PR text."
