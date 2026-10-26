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

# Prints the version incremented by type: major resets minor and fix, minor resets fix.
increment_version() {
  local major minor fix
  IFS='.' read -r major minor fix <<<"$1"
  case "$2" in
    major) echo "$((major + 1)).0.0" ;;
    minor) echo "${major}.$((minor + 1)).0" ;;
    fix) echo "${major}.${minor}.$((fix + 1))" ;;
    *)
      echo "Unknown version bump type: $2 (expected major, minor or fix)" >&2
      return 1
      ;;
  esac
}
