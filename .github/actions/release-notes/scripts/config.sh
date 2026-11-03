#!/usr/bin/env bash
# Load and validate an agent repo's release-notes config and its skeleton, then print the settings
# the actions use as name=value lines (safe to append to $GITHUB_OUTPUT: no value holds a newline).
# Usage: config.sh CONFIG_FILE      (run from the agent repo's root; config paths are relative to it)
# Exit 0 and prints the settings; exit 2 and prints every problem found, each naming its field.
# The fields are described in the README next to this folder.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

[ "$#" -eq 1 ] || { echo "usage: $0 CONFIG_FILE" >&2; exit 2; }
config=$1
[ -r "$config" ] || { echo "$config: cannot read the release-notes config" >&2; exit 2; }
jq -e 'type == "object"' "$config" > /dev/null 2>&1 || { echo "$config: not a JSON object" >&2; exit 2; }

REQUIRED="subject docsFolder filePrefix nextBranch tagPrefix skeleton"
OPTIONAL="downloadLink changelog fallbackText"
BOOLEAN="prLinks labelledOnly readyForReview" # optional, true or false

errors=""
bad() { errors="$errors$config: $1"$'\n'; }
has() { jq -e --arg k "$1" 'has($k)' "$config" > /dev/null; }
get() { jq -r --arg k "$1" '.[$k] // ""' "$config"; }
flag() { jq -r --arg k "$1" --argjson d "$2" 'if has($k) then .[$k] else $d end' "$config"; } # KEY DEFAULT

while IFS= read -r k; do
  [ -n "$k" ] && bad "unknown field \"$k\" (known: $REQUIRED $OPTIONAL $BOOLEAN)"
done <<< "$(jq -r --arg known "$REQUIRED $OPTIONAL $BOOLEAN" '($known | split(" ")) as $ok | keys[] | select(. as $k | $ok | index([$k]) | not)' "$config")"
while IFS= read -r k; do
  [ -n "$k" ] && bad "\"$k\" must be a single-line string"
done <<< "$(jq -r --arg bools "$BOOLEAN" '($bools | split(" ")) as $b | to_entries[] | select(.key as $k | $b | index([$k]) | not)
  | select((.value | type) != "string" or (.value | explode | any(. < 32 or . == 127))) | .key' "$config")"
for k in $BOOLEAN; do
  if has "$k" && [ "$(jq -r --arg k "$k" '.[$k] | type' "$config")" != boolean ]; then bad "\"$k\" must be true or false"; fi
done
for k in $REQUIRED; do has "$k" || bad "missing required field \"$k\""; done
[ -z "$errors" ] || { printf '%s' "$errors" >&2; exit 2; }

subject=$(get subject) docs_folder=$(get docsFolder) file_prefix=$(get filePrefix)
next_branch=$(get nextBranch) tag_prefix=$(get tagPrefix) skeleton=$(get skeleton)
download_link=$(get downloadLink) changelog=$(get changelog) fallback_text=$(get fallbackText)
has fallbackText || fallback_text="Updated version."
pr_links=$(flag prLinks true) labelled_only=$(flag labelledOnly false) ready_for_review=$(flag readyForReview false)

name_re='^[A-Za-z0-9-]+$'
path_re='^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$'
[[ $subject =~ $SUBJECT_RE ]] || bad "\"subject\" may only hold letters, digits, spaces and . + -"
[[ $docs_folder =~ $name_re ]] || bad "\"docsFolder\" may only hold A-Z a-z 0-9 and -"
[[ $file_prefix =~ $name_re ]] || bad "\"filePrefix\" may only hold A-Z a-z 0-9 and -"
if [[ $next_branch =~ $name_re ]]; then
  case $next_branch in ?*-next) ;; *) bad "\"nextBranch\" must end in -next" ;; esac
else
  bad "\"nextBranch\" may only hold A-Z a-z 0-9 and -"
fi
[[ $tag_prefix =~ ^[A-Za-z]*$ ]] || bad "\"tagPrefix\" may only hold letters"
has changelog && [ -z "$changelog" ] && bad "\"changelog\" must not be empty; leave the field out instead"
for k in skeleton changelog; do
  v=$(get "$k")
  [ -n "$v" ] || continue
  if ! [[ $v =~ $path_re ]] || [[ /$v/ == */../* || /$v/ == */./* ]]; then
    bad "\"$k\" must be a relative path inside the repo"
  fi
done
case $skeleton in *.mdx) ;; *) bad "\"skeleton\" must be an .mdx file" ;; esac
if has downloadLink; then
  case $download_link in https://?*) ;; *) bad "\"downloadLink\" must start with https://" ;; esac
  case $download_link in *[[:space:]\'\"\<\>\\]*) bad "\"downloadLink\" must not contain spaces, quotes, < > or \\" ;; esac
  rest=$download_link
  for p in '{version}' '{tag}' '{npmPackage}'; do rest=${rest//"$p"/}; done
  case $rest in *[{}]*) bad "\"downloadLink\" may only use the placeholders {version}, {tag} and {npmPackage}" ;; esac
fi
if has fallbackText && [ -z "$(oneline "$fallback_text")" ]; then bad "\"fallbackText\" must not be blank"; fi

# The skeleton is the start of every next-release file, so it must carry every marker.
if [ -z "$errors" ]; then
  if [ ! -f "$skeleton" ]; then
    bad "skeleton $skeleton does not exist"
  else
    got=$(tr -d '\r' < "$skeleton" | sed -n 's/^subject: //p' | head -1)
    [ "$got" = "$subject" ] || bad "skeleton $skeleton has \"subject: $got\"; the config says \"$subject\""
    for s in $SECTIONS; do
      tr -d '\r' < "$skeleton" | grep -qxF "$(section_marker "$s")" || bad "skeleton $skeleton is missing $(section_marker "$s")"
    done
    tr -d '\r' < "$skeleton" | grep -qxF "$END_MARKER" || bad "skeleton $skeleton is missing $END_MARKER"
  fi
fi
[ -z "$errors" ] || { printf '%s' "$errors" >&2; exit 2; }

cat << OUT
subject=$subject
docs_dir=$DOCS_ROOT/$docs_folder
docs_base=$DOCS_BASE
file_prefix=$file_prefix
next_branch=$next_branch
release_branch_prefix=${next_branch%-next}_release_notes_
tag_prefix=$tag_prefix
skeleton=$skeleton
skeleton_name=$(basename "$skeleton")
download_link=$download_link
changelog=$changelog
fallback_text=$(oneline "$fallback_text")
pr_links=$pr_links
labelled_only=$labelled_only
ready_for_review=$ready_for_review
OUT
