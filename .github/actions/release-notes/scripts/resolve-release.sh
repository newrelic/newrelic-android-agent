#!/usr/bin/env bash
# Everything the release-notes generator needs from the agent repo's git history, for one release.
# Usage: resolve-release.sh --prefix P --version V --skeleton PATH --out DIR [--changelog PATH] [--date D]
#   Run inside the agent repo (full history and tags). V may be given bare (1.9.3) or as the tag
#   (v1.9.3, or 7.8.4 when P is empty).
# Prints name=value lines: version (1.9.3), tag (v1.9.3), prev_tag (v1.9.2), date, slug (193),
# dashed (1-9-3). The date is --date when given, else the date of the tagged commit, so a re-run on
# another day produces the same note.
# Writes into DIR, from the tagged commit:
#   tail.mdx      what follows the end marker in the skeleton (taken from the working tree when the
#                 tag predates the skeleton, with a notice on stderr)
#   CHANGELOG.md  the --changelog file (only with --changelog; a missing file is an error)
#   package.json  when the tag has one (download-link.sh needs it for {npmPackage})
# Exit 1 when the tag or a previous release tag is missing; exit 2 on bad arguments.
set -euo pipefail
dir=$(cd "$(dirname "$0")" && pwd)
. "$dir/lib.sh"

prefix="" version="" skeleton="" out="" changelog="" date=""
while [ "$#" -gt 0 ]; do
  [ "$#" -ge 2 ] || { echo "$1 needs a value" >&2; exit 2; }
  case "$1" in
    --prefix) prefix=$2 ;; --version) version=$2 ;; --skeleton) skeleton=$2 ;;
    --out) out=$2 ;; --changelog) changelog=$2 ;; --date) date=$2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift 2
done
for v in version skeleton out; do [ -n "${!v}" ] || { echo "--$v is required" >&2; exit 2; }; done
[[ $prefix =~ ^[A-Za-z]*$ ]] || { echo "--prefix may only hold letters: $prefix" >&2; exit 2; }
[ -d "$out" ] || { echo "--out must be an existing directory: $out" >&2; exit 2; }

version=${version#"$prefix"}
version=${version#v}
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "'$version' is not an X.Y.Z release version" >&2; exit 2; }
[ -z "$date" ] || [[ $date =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "--date must look like 2026-10-20: $date" >&2; exit 2; }
tag=$prefix$version

git rev-parse -q --verify "refs/tags/$tag^{commit}" > /dev/null || { echo "tag $tag does not exist in this repository" >&2; exit 1; }
prev=$(git tag --list | "$dir/previous-tag.sh" --prefix "$prefix" "$version") \
  || { echo "no release tag below $tag (release tags look like ${prefix}X.Y.Z)" >&2; exit 1; }
[ -n "$date" ] || date=$(git log -1 --format=%cs "$tag")

if git cat-file -e "$tag:$skeleton" 2> /dev/null; then
  git show "$tag:$skeleton" > "$out/skeleton.mdx"
else
  echo "notice: $skeleton is not in $tag; using the working-tree copy for the note's tail" >&2
  cp "$skeleton" "$out/skeleton.mdx"
fi
skeleton_tail "$out/skeleton.mdx" > "$out/tail.mdx"
rm -f "$out/skeleton.mdx"

if [ -n "$changelog" ]; then
  git show "$tag:$changelog" > "$out/CHANGELOG.md" 2> /dev/null || { echo "$changelog is not in $tag" >&2; exit 1; }
fi
if git cat-file -e "$tag:package.json" 2> /dev/null; then git show "$tag:package.json" > "$out/package.json"; fi

cat << OUT
version=$version
tag=$tag
prev_tag=$prev
date=$date
slug=${version//./}
dashed=${version//./-}
OUT
