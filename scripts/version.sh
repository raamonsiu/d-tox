#!/usr/bin/env bash
# Version management. VERSION is the single source of truth (major.minor.fix); only CI changes it,
# once per merged pull request, from the PR's "version:*" label (D-33).
#
#   bump-type <labels>        prints major, minor or fix from comma-separated PR labels
#                             (no version label = fix; more than one = error).
#   next <type>               prints VERSION incremented by major, minor or fix.
#   check <baseRef>           PR check: this branch does not change VERSION and the README version
#                             still matches it.
#   publish <pr> <labels>     CI after a merge: bump VERSION and README on origin/main, commit and
#                             push, retrying on top of newer merges.
#   sync                      write VERSION into the README marker.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

README_MARKER_REGEX='(<!-- version -->)[^<]*(<!-- /version -->)'
VERSION_LABEL_PREFIX="version:"
PUBLISH_ATTEMPTS=5
BOT_NAME="github-actions[bot]"
BOT_EMAIL="41898282+github-actions[bot]@users.noreply.github.com"

# Prints the version written in the README marker of the given text (empty if none).
readme_version_of() {
  grep -oE '<!-- version -->[^<]*<!-- /version -->' |
    sed -E 's/<!-- version -->([^<]*)<!-- \/version -->/\1/' | head -n 1
}

# Prints the README version marker at a git ref (empty if absent).
readme_version_at_ref() {
  git show "$1:README.md" 2>/dev/null | readme_version_of || true
}

# Writes the given version to VERSION and into the README marker.
write_version_files() {
  local new_version="$1"
  echo "$new_version" >VERSION
  if [ -f README.md ] && grep -qE '<!-- version -->' README.md; then
    sed -i.bak -E "s#${README_MARKER_REGEX}#\1${new_version}\2#" README.md
    rm -f README.md.bak
  fi
}

command_bump_type() {
  local labels="$1" label found=()
  IFS=',' read -r -a label_list <<<"$labels"
  for label in "${label_list[@]}"; do
    case "$label" in
      "${VERSION_LABEL_PREFIX}major" | "${VERSION_LABEL_PREFIX}minor" | "${VERSION_LABEL_PREFIX}fix")
        found+=("${label#"$VERSION_LABEL_PREFIX"}")
        ;;
    esac
  done
  if [ "${#found[@]}" -gt 1 ]; then
    echo "Use at most one version label, found: ${found[*]}" >&2
    return 1
  fi
  echo "${found[0]:-fix}"
}

command_next() {
  increment_version "$(<VERSION)" "$1"
}

command_check() {
  local base_ref="$1" merge_base
  merge_base="$(git merge-base "$base_ref" HEAD)"
  if ! git diff --quiet "$merge_base" HEAD -- VERSION; then
    echo "This branch changes VERSION. CI bumps it after merging;" >&2
    echo "ask for minor/major with a 'version:minor' or 'version:major' label instead." >&2
    return 1
  fi
  if [ "$(readme_version_at_ref HEAD)" != "$(<VERSION)" ]; then
    echo "README version ($(readme_version_at_ref HEAD)) does not match VERSION ($(<VERSION));" >&2
    echo "do not edit the README version marker, CI updates it after merging." >&2
    return 1
  fi
  echo "Version OK: unchanged ($(<VERSION)); CI bumps it after the merge."
}

command_publish() {
  local pull_request="$1" labels="$2" bump_type new_version attempt
  bump_type="$(command_bump_type "$labels")"
  git config user.name "$BOT_NAME"
  git config user.email "$BOT_EMAIL"
  for attempt in $(seq 1 "$PUBLISH_ATTEMPTS"); do
    git fetch --quiet origin main
    git reset --quiet --hard origin/main
    new_version="$(command_next "$bump_type")"
    write_version_files "$new_version"
    git add VERSION README.md
    git commit --quiet -m "DTOX(chore)/bump_version_after_pr_${pull_request}" \
      -m "Version ${new_version} (${bump_type}) after #${pull_request}."
    if git push --quiet origin HEAD:main; then
      echo "Published ${new_version} (${bump_type}) after #${pull_request}."
      return 0
    fi
    echo "main moved on (attempt ${attempt}); retrying on top of it." >&2
    sleep "$attempt"
  done
  echo "Could not push the version bump after ${PUBLISH_ATTEMPTS} attempts." >&2
  return 1
}

cd "$(repository_root)"
case "${1:-}" in
  bump-type) command_bump_type "${2-}" ;;
  next) command_next "${2:?type required: major, minor or fix}" ;;
  check) command_check "${2:?base ref required}" ;;
  publish) command_publish "${2:?pull request number required}" "${3-}" ;;
  sync) write_version_files "$(<VERSION)" ;;
  *)
    sed -n '2,12p' "$0"
    exit 2
    ;;
esac
