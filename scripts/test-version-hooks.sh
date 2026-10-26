#!/usr/bin/env bash
# Exercises the versioning hooks end to end in throwaway repositories (nothing touches this repo):
#   1. fast-forward pull of a normal commit      -> no new commit, VERSION untouched
#   2. fast-forward pull of an existing merge    -> no new commit, VERSION untouched
#   3. real local merge without conflicts        -> merge rebuilt with highest(parents) + 1
# Exits non-zero on the first failed expectation.
set -euo pipefail

SOURCE_ROOT="$(git rev-parse --show-toplevel)"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

# Ignore the developer's own git configuration so results are reproducible.
export GIT_CONFIG_GLOBAL="$SANDBOX/gitconfig" GIT_CONFIG_NOSYSTEM=1
git config --global user.name "Version Hook Test"
git config --global user.email "version-hook-test@example.invalid"
git config --global init.defaultBranch main
git config --global pull.rebase false

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_equal() {
  local description="$1" expected="$2" actual="$3"
  [ "$expected" = "$actual" ] || fail "${description}: expected '${expected}', got '${actual}'"
  echo "ok: ${description}"
}

# Commits a new file with a valid slug message; the post-commit hook bumps VERSION.
commit_file() {
  local file_name="$1"
  echo "$file_name" >"$file_name"
  git add "$file_name"
  git commit --quiet -m "DTOX(test)/add_${file_name}"
}

# Clones the sandbox origin into the given directory and enables the hooks there.
clone_with_hooks() {
  git clone --quiet "$SANDBOX/origin.git" "$1"
  (cd "$1" && scripts/setup.sh >/dev/null)
}

git init --quiet --bare "$SANDBOX/origin.git"
mkdir "$SANDBOX/seed"
cp -R "$SOURCE_ROOT/scripts" "$SOURCE_ROOT/.githooks" "$SOURCE_ROOT/.gitattributes" "$SANDBOX/seed/"
echo "1.0.0" >"$SANDBOX/seed/VERSION"
echo "Version <!-- version -->1.0.0<!-- /version -->" >"$SANDBOX/seed/README.md"
(
  cd "$SANDBOX/seed"
  git init --quiet
  scripts/setup.sh >/dev/null
  git add -A
  git commit --quiet -m "DTOX(test)/seed_repository"
  git remote add origin "$SANDBOX/origin.git"
  git push --quiet -u origin main
)

clone_with_hooks "$SANDBOX/developer"
clone_with_hooks "$SANDBOX/teammate"

# Scenario 1: the teammate publishes a normal commit; the developer fast-forwards to it.
(cd "$SANDBOX/teammate" && commit_file one && git push --quiet)
cd "$SANDBOX/developer"
git pull --quiet
assert_equal "fast-forward to a normal commit keeps origin/main" \
  "$(git rev-parse origin/main)" "$(git rev-parse HEAD)"

# Scenario 3 (on the teammate): a real local merge gets highest(parents) + 1.
cd "$SANDBOX/teammate"
git pull --quiet
git switch --quiet -c local_feature
commit_file local_feature_change
git switch --quiet main
commit_file local_main_change
git merge --quiet --no-ff --no-edit local_feature
expected_merge_version="$(
  source scripts/lib.sh
  increment_fix_version "$(highest_version "$(version_at_ref HEAD^1)" "$(version_at_ref HEAD^2)")"
)"
assert_equal "local merge keeps two parents" "2" "$(($(git rev-list --parents -n 1 HEAD | wc -w) - 1))"
assert_equal "local merge bumps to highest(parents) + 1" "$expected_merge_version" "$(<VERSION)"

# Scenario 2: a PR merged on GitHub (no hooks run, so VERSION is just the branch's) is published
# and the developer fast-forwards to it.
git switch --quiet -c pull_request
commit_file pull_request_change
git switch --quiet main
DTOX_SKIP_VERSION_HOOK=1 git merge --quiet --no-ff --no-edit pull_request
git push --quiet
cd "$SANDBOX/developer"
git pull --quiet
assert_equal "fast-forward to a merge commit keeps origin/main" \
  "$(git rev-parse origin/main)" "$(git rev-parse HEAD)"
assert_equal "fast-forward to a merge commit keeps VERSION" \
  "$(git show origin/main:VERSION)" "$(<VERSION)"
assert_equal "fast-forward leaves the work tree clean" "" "$(git status --porcelain)"

echo "All version hook checks passed."
