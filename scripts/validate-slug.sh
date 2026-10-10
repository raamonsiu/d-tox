#!/usr/bin/env bash
# Validates that each argument is a D-TOX slug (branch name, commit subject, PR title).
# Merge commits ("Merge ...") and the main branch are accepted.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

status=0
for candidate in "$@"; do
  case "$candidate" in
    main | Merge\ *) continue ;;
  esac
  validate_slug "$candidate" || status=1
done
exit "$status"
