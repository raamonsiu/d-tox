#!/usr/bin/env bash
# Exercises scripts/version.sh in throwaway repositories (nothing touches this repo):
#   - next and bump-type for every label combination
#   - check: a PR that leaves the version alone passes; one that edits VERSION or the README
#     version fails, even when main moved on meanwhile
#   - publish: bumps on top of the latest origin/main, not the local checkout, with a valid slug,
#     and retries when the push is rejected because main moved
# Exits non-zero on the first failed expectation.
set -euo pipefail

SOURCE_ROOT="$(git rev-parse --show-toplevel)"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

# Ignore the developer's own git configuration so results are reproducible.
export GIT_CONFIG_GLOBAL="$SANDBOX/gitconfig" GIT_CONFIG_NOSYSTEM=1
git config --global user.name "Version Test"
git config --global user.email "version-test@example.invalid"
git config --global init.defaultBranch main

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_equal() {
  local description="$1" expected="$2" actual="$3"
  [ "$expected" = "$actual" ] || fail "${description}: expected '${expected}', got '${actual}'"
  echo "ok: ${description}"
}

assert_fails() {
  local description="$1"
  shift
  if "$@" >/dev/null 2>&1; then fail "${description}: expected a failure"; fi
  echo "ok: ${description}"
}

readme_version() {
  sed -E 's/.*<!-- version -->([^<]*)<!-- \/version -->.*/\1/' README.md
}

# Commits a change to a file with a valid slug message.
commit_change() {
  local file_name="$1" content="$2"
  echo "$content" >"$file_name"
  git add "$file_name"
  git commit --quiet -m "DTOX(test)/change_${file_name//[^a-z]/_}"
}

git init --quiet --bare "$SANDBOX/origin.git"
mkdir "$SANDBOX/seed"
cp -R "$SOURCE_ROOT/scripts" "$SANDBOX/seed/"
echo "1.4.7" >"$SANDBOX/seed/VERSION"
echo "Version <!-- version -->1.4.7<!-- /version -->" >"$SANDBOX/seed/README.md"
(
  cd "$SANDBOX/seed"
  git init --quiet
  git add -A
  git commit --quiet -m "DTOX(test)/seed_repository"
  git remote add origin "$SANDBOX/origin.git"
  git push --quiet -u origin main
)

cd "$SANDBOX/seed"
assert_equal "next fix" "1.4.8" "$(scripts/version.sh next fix)"
assert_equal "next minor resets fix" "1.5.0" "$(scripts/version.sh next minor)"
assert_equal "next major resets minor and fix" "2.0.0" "$(scripts/version.sh next major)"
assert_fails "next rejects an unknown type" scripts/version.sh next patch
assert_equal "no labels mean fix" "fix" "$(scripts/version.sh bump-type "")"
assert_equal "other labels are ignored" "fix" "$(scripts/version.sh bump-type "ready,version:fixme")"
assert_equal "minor label" "minor" "$(scripts/version.sh bump-type "ready,version:minor")"
assert_equal "major label" "major" "$(scripts/version.sh bump-type "version:major")"
assert_fails "two version labels are rejected" scripts/version.sh bump-type "version:minor,version:major"

git switch --quiet -c "DTOX-1(feat)/feature"
commit_change feature.txt "work"
scripts/version.sh check main >/dev/null || fail "check rejected a branch that leaves the version alone"
echo "ok: check accepts a branch that leaves the version alone"
commit_change README.md "Docs edit. Version <!-- version -->1.4.7<!-- /version -->"
scripts/version.sh check main >/dev/null || fail "check rejected a README edit outside the version"
echo "ok: check accepts README edits outside the version marker"

git switch --quiet main
commit_change VERSION "1.4.8"
scripts/version.sh sync && git commit --quiet -am "DTOX(chore)/bump_version_after_pr_1"
git switch --quiet "DTOX-1(feat)/feature"
scripts/version.sh check main >/dev/null || fail "check rejected a branch behind a newer main"
echo "ok: check accepts a branch while main moved on"

git switch --quiet -c "DTOX-2(feat)/edits_version" main
commit_change VERSION "9.9.9"
assert_fails "check rejects a branch that edits VERSION" scripts/version.sh check main
git switch --quiet -c "DTOX-3(feat)/edits_readme_version" main
commit_change README.md "Version <!-- version -->9.9.9<!-- /version -->"
assert_fails "check rejects a branch that edits the README version" scripts/version.sh check main

# publish from a stale clone: origin/main already has 1.4.9, the clone still sees 1.4.7.
git clone --quiet "$SANDBOX/origin.git" "$SANDBOX/runner"
(
  cd "$SANDBOX/seed"
  git switch --quiet main
  echo "1.4.9" >VERSION && scripts/version.sh sync
  git commit --quiet -am "DTOX(chore)/bump_version_after_pr_2"
  git push --quiet origin main
)
cd "$SANDBOX/runner"
scripts/version.sh publish 3 "ready" >/dev/null
assert_equal "publish bumps on top of the latest origin/main" "1.4.10" "$(git show origin/main:VERSION)"
git reset --quiet --hard origin/main
assert_equal "publish syncs the README" "1.4.10" "$(readme_version)"
subject="$(git log -1 --format=%s origin/main)"
scripts/validate-slug.sh "$subject" || fail "publish commit subject is not a slug: $subject"
echo "ok: publish commit subject is a valid slug ($subject)"

# The first push is rejected, as if another merge landed meanwhile; publish must retry.
cat >"$SANDBOX/origin.git/hooks/pre-receive" <<EOF
#!/usr/bin/env bash
[ -f "$SANDBOX/rejected-once" ] && exit 0
touch "$SANDBOX/rejected-once"
echo "simulated race: main moved" >&2
exit 1
EOF
chmod +x "$SANDBOX/origin.git/hooks/pre-receive"
scripts/version.sh publish 4 "version:minor" >/dev/null 2>&1
[ -f "$SANDBOX/rejected-once" ] || fail "the simulated rejection did not happen"
assert_equal "publish retries after a rejected push, minor label" "1.5.0" "$(git show origin/main:VERSION)"

echo "All version tests passed."
