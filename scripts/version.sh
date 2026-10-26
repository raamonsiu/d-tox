#!/usr/bin/env bash
# Version management. VERSION is the single source of truth (major.minor.fix).
#
#   bump              post-commit: fix+1 for a normal commit.
#   merged            merge commits: rebuild the commit with highest parent version + 1.
#   sync              write VERSION into the README marker.
#   check <baseRef>   CI: VERSION must be greater than at <baseRef> and match the README.
#
# Minor/major bumps: edit VERSION by hand in the commit; the hook then keeps it as is.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

README_MARKER_REGEX='(<!-- version -->)[^<]*(<!-- /version -->)'

# Prints the version currently written in the README marker (empty if none).
readme_version() {
  grep -oE '<!-- version -->[^<]*<!-- /version -->' README.md 2>/dev/null |
    sed -E 's/<!-- version -->([^<]*)<!-- \/version -->/\1/' | head -n 1
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

# True while rebasing, cherry-picking or reverting; the hook must not interfere there.
history_rewrite_in_progress() {
  local git_dir
  git_dir="$(git rev-parse --git-dir)"
  [ -d "$git_dir/rebase-merge" ] || [ -d "$git_dir/rebase-apply" ] ||
    [ -f "$git_dir/CHERRY_PICK_HEAD" ] || [ -f "$git_dir/REVERT_HEAD" ]
}

# True when HEAD was not created here but reached by a fast-forward (`git pull`/`git merge` to a
# merge commit that already exists, e.g. a PR merged on GitHub) or is already published on a remote.
# Topology alone cannot tell these apart from a local merge, and rewriting them diverges from remote.
head_is_existing_commit() {
  git reflog -1 --format=%gs HEAD 2>/dev/null | grep -q 'Fast-forward' ||
    [ -n "$(git branch -r --contains HEAD 2>/dev/null)" ]
}

# Folds VERSION and README into the commit that was just created.
amend_head_with_version_files() {
  if ! git diff --quiet -- VERSION README.md; then
    git add VERSION README.md
    DTOX_SKIP_VERSION_HOOK=1 git commit --amend --no-edit --no-verify --quiet
    echo "[version] $(<VERSION)"
  fi
}

# Aborts (without failing the commit) if the user has unstaged edits in the version files.
refuse_when_version_files_dirty() {
  if ! git diff --quiet -- VERSION README.md; then
    echo "[version] VERSION/README.md have unstaged changes; run 'scripts/version.sh sync' and amend manually." >&2
    exit 0
  fi
}

command_bump() {
  history_rewrite_in_progress && exit 0
  local parent_count
  parent_count="$(($(git rev-list --parents -n 1 HEAD | wc -w) - 1))"
  [ "$parent_count" -ge 2 ] && command_merged
  refuse_when_version_files_dirty
  if [ "$parent_count" -eq 0 ] || git diff-tree --no-commit-id --name-only -r HEAD | grep -qx 'VERSION'; then
    write_version_files "$(<VERSION)"
  else
    write_version_files "$(increment_fix_version "$(version_at_ref HEAD^)")"
  fi
  amend_head_with_version_files
}

# Merge commits (post-merge for clean merges, post-commit for conflicted ones): amending is refused
# while MERGE_HEAD exists, so the commit is rebuilt with plumbing: same parents and message, new tree.
command_merged() {
  local parents parent_arguments=() highest="0.0.0" parent version_after tree new_commit
  mapfile -t parents < <(git rev-list --parents -n 1 HEAD | tr ' ' '\n' | tail -n +2)
  [ "${#parents[@]}" -ge 2 ] || exit 0
  head_is_existing_commit && exit 0
  for parent in "${parents[@]}"; do
    highest="$(highest_version "$highest" "$(version_at_ref "$parent")")"
    parent_arguments+=(-p "$parent")
  done
  version_after="$(increment_fix_version "$highest")"
  [ "$(<VERSION)" = "$version_after" ] && exit 0
  write_version_files "$version_after"
  git add VERSION README.md
  tree="$(git write-tree)"
  new_commit="$(git log -1 --format=%B | GIT_AUTHOR_NAME="$(git log -1 --format=%an)" \
    GIT_AUTHOR_EMAIL="$(git log -1 --format=%ae)" GIT_AUTHOR_DATE="$(git log -1 --format=%aI)" \
    git commit-tree "$tree" "${parent_arguments[@]}")"
  git update-ref -m "version: merge -> ${version_after}" HEAD "$new_commit"
  echo "[version] merge -> ${version_after}"
}

command_check() {
  local base_ref="$1" current base_version readme_current
  current="$(<VERSION)"
  base_version="$(version_at_ref "$base_ref")"
  readme_current="$(readme_version)"
  if [ "$current" != "$(highest_version "$current" "${base_version:-0.0.0}")" ] ||
    [ "$current" = "${base_version:-0.0.0}" ]; then
    echo "VERSION ($current) must be greater than ${base_ref} (${base_version})." >&2
    return 1
  fi
  if [ "$readme_current" != "$current" ]; then
    echo "README version ($readme_current) does not match VERSION ($current)." >&2
    return 1
  fi
  echo "Version OK: ${base_version} -> ${current}"
}

cd "$(repository_root)"
case "${1:-}" in
  bump) command_bump ;;
  merged) command_merged ;;
  sync) write_version_files "$(<VERSION)" ;;
  check) command_check "${2:?base ref required}" ;;
  *)
    sed -n '2,12p' "$0"
    exit 2
    ;;
esac
