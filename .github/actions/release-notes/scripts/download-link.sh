#!/usr/bin/env bash
# Render a config downloadLink template for one release.
# Usage: download-link.sh TEMPLATE VERSION TAG [PACKAGE_JSON]
#   {version} -> VERSION (1.9.3), {tag} -> TAG (v1.9.3), {npmPackage} -> "name" in PACKAGE_JSON
#   download-link.sh 'https://www.npmjs.com/package/{npmPackage}/v/{version}' 1.9.3 v1.9.3 package.json
#     -> https://www.npmjs.com/package/newrelic-react-native-agent/v/1.9.3
# PACKAGE_JSON is only read when the template uses {npmPackage}; it must then exist and name the
# package. Exit 2 when it does not, or when any {...} is left unresolved.
set -euo pipefail
export LC_ALL=C # ASCII patterns only; see lib.sh

[ "$#" -ge 3 ] && [ "$#" -le 4 ] || { echo "usage: $0 TEMPLATE VERSION TAG [PACKAGE_JSON]" >&2; exit 2; }
url=$1 version=$2 tag=$3 package_json=${4:-}
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "VERSION must look like 1.9.3: $version" >&2; exit 2; }
[[ $tag =~ ^[A-Za-z]*[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "TAG must look like v1.9.3 or 1.9.3: $tag" >&2; exit 2; }

case $url in
  *'{npmPackage}'*)
    [ -n "$package_json" ] && [ -r "$package_json" ] \
      || { echo "downloadLink uses {npmPackage}, but there is no package.json at $tag" >&2; exit 2; }
    name=$(jq -r '.name // empty' "$package_json" 2> /dev/null) || name=""
    # npm names: lower case, optionally @scope/; nothing that could break a URL or the YAML quote.
    [[ $name =~ ^(@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*$ ]] \
      || { echo "package.json at $tag has no usable \"name\" for {npmPackage}: '$name'" >&2; exit 2; }
    url=${url//'{npmPackage}'/"$name"}
    ;;
esac
url=${url//'{version}'/"$version"}
url=${url//'{tag}'/"$tag"}
case $url in *[{}]*) echo "unresolved placeholder in downloadLink: $url" >&2; exit 2 ;; esac
printf '%s\n' "$url"
