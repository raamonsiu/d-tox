#!/usr/bin/env bash
# Shared helpers for D-TOX scripts. Source this file; do not execute it.

# Single pinned Prettier version so hooks and CI always agree.
PRETTIER_VERSION="3.3.3"

# Slug: DTOX[-ticketNumber](type)/short_snake_case_description
SLUG_TYPES="feat|fix|perf|refactor|style|docs|test|build|ci|chore"
SLUG_REGEX="^DTOX(-[0-9]+)?\((${SLUG_TYPES})\)/[a-z0-9]+(_[a-z0-9]+){0,5}$"
SLUG_MAX_LENGTH=72

# Prints the repository root. Fails outside a git work tree.
repository_root() {
  git rev-parse --show-toplevel
}

# Prints "ok" when the given text is a valid slug, otherwise a reason on stderr and exit 1.
validate_slug() {
  local candidate="$1"
  if [ "${#candidate}" -gt "$SLUG_MAX_LENGTH" ]; then
    echo "Slug too long (${#candidate} > ${SLUG_MAX_LENGTH}): $candidate" >&2
    return 1
  fi
  if ! [[ "$candidate" =~ $SLUG_REGEX ]]; then
    echo "Invalid slug: $candidate" >&2
    echo "Expected: DTOX-<ticket>(<type>)/short_description  e.g. DTOX-42(feat)/add_epub_parser" >&2
    echo "Types: ${SLUG_TYPES//|/, }. Ticket optional. Description: lowercase a-z0-9 and _, max 6 words." >&2
    return 1
  fi
}

# Prints the higher of two major.minor.fix versions.
highest_version() {
  printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n 1
}

# Prints version with fix + 1.
increment_fix_version() {
  local major minor fix
  IFS='.' read -r major minor fix <<<"$1"
  echo "${major}.${minor}.$((fix + 1))"
}

# Prints the VERSION stored at a git ref, or 0.0.0 if absent.
version_at_ref() {
  git show "$1:VERSION" 2>/dev/null | tr -d '[:space:]' || true
}
