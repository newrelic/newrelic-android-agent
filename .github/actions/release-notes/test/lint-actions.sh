#!/usr/bin/env bash
# Lint the run: blocks of the three action.yml files, which actionlint does not cover:
# - no ${{ }} expression inside a run: block (values reach the shell through env: only, so PR text
#   can never be spliced into a script);
# - every block is bash and passes shellcheck.
# Needs ruby (for YAML) and shellcheck. Exit 1 on any finding.
set -euo pipefail
dir=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
status=0
for action in "$dir"/*/action.yml; do
  name=$(basename "$(dirname "$action")")
  ruby -ryaml -e '
    y = YAML.load_file(ARGV[0])
    abort "#{ARGV[0]}: not a composite action" unless y.dig("runs", "using") == "composite"
    y["runs"]["steps"].each_with_index do |s, i|
      next unless s["run"]
      abort "#{ARGV[0]}: step \"#{s["name"]}\" is not shell: bash" unless s["shell"] == "bash"
      File.write(File.join(ARGV[1], format("%02d.sh", i)), "#!/usr/bin/env bash\n# #{s["name"]}\n" + s["run"])
    end' "$action" "$tmp" || { status=1; continue; }
  for f in "$tmp"/*.sh; do
    if grep -n '\${{' "$f"; then echo "$name/action.yml: \${{ }} inside a run: block ($(sed -n 2p "$f"))"; status=1; fi
    shellcheck --severity=warning --shell=bash "$f" || { echo "^ in $name/action.yml: $(sed -n 2p "$f")"; status=1; }
  done
  rm -f "$tmp"/*.sh
done
[ "$status" = 0 ] && echo "action run: blocks OK"
exit "$status"
