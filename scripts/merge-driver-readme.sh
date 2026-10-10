#!/usr/bin/env bash
# Git merge driver for README.md: three-way merge ignoring the version marker, then restores
# the highest version. Real README conflicts are still reported. Args: %O %A %B.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
base_file="$1"
ours_file="$2"
theirs_file="$3"

extract_version() {
  grep -oE '<!-- version -->[^<]*<!-- /version -->' "$1" | head -n 1 |
    sed -E 's/<!-- version -->([^<]*)<!-- \/version -->/\1/'
}
neutralize_version() {
  sed -i.bak -E 's#(<!-- version -->)[^<]*(<!-- /version -->)#\1__VERSION__\2#' "$1"
  rm -f "$1.bak"
}

merged_version="$(highest_version "$(extract_version "$ours_file")" "$(extract_version "$theirs_file")")"
neutralize_version "$base_file"
neutralize_version "$ours_file"
neutralize_version "$theirs_file"

merge_status=0
git merge-file "$ours_file" "$base_file" "$theirs_file" || merge_status=$?
sed -i.bak "s#__VERSION__#${merged_version}#" "$ours_file"
rm -f "$ours_file.bak"
exit "$((merge_status > 0 ? 1 : 0))"
