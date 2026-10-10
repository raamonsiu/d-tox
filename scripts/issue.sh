#!/usr/bin/env bash
# Coordinates who implements which issue, using GitHub issues as the single source of truth.
# An issue is BUSY when it has an assignee, the "in-progress" label, or a remote branch/open PR
# named DTOX-<number>(...). Only free issues labeled "ready" can be claimed.
#
#   list                               free issues labeled "ready"
#   show <number>                      issue summary and whether it is free or busy
#   claim <number> <type> <description>  assign me, label in-progress, create and push the slug branch
#   release <number>                   remove my assignment and the in-progress label
#
# Requires: gh (authenticated) and an "origin" remote.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

READY_LABEL="ready"
IN_PROGRESS_LABEL="in-progress"

require_github_cli() {
  command -v gh >/dev/null 2>&1 || { echo "gh (GitHub CLI) is not installed." >&2; exit 1; }
  gh auth status >/dev/null 2>&1 || { echo "gh is not authenticated: run 'gh auth login'." >&2; exit 1; }
}

# Prints one line per reason the issue is busy; prints nothing when it is free.
busy_reasons() {
  local number="$1" assignees labels branches pull_requests
  assignees="$(gh issue view "$number" --json assignees --jq '[.assignees[].login] | join(", ")')"
  labels="$(gh issue view "$number" --json labels --jq '[.labels[].name] | join(",")')"
  branches="$(git ls-remote --heads origin "refs/heads/DTOX-${number}(*" | awk '{print $2}' | sed 's#refs/heads/##')"
  pull_requests="$(gh pr list --state open --limit 200 --json number,headRefName \
    --jq ".[] | select(.headRefName | startswith(\"DTOX-${number}(\")) | \"#\\(.number) \\(.headRefName)\"")"
  [ -n "$assignees" ] && echo "assigned to: ${assignees}"
  [[ ",${labels}," == *",${IN_PROGRESS_LABEL},"* ]] && echo "label: ${IN_PROGRESS_LABEL}"
  [ -n "$branches" ] && echo "remote branch: ${branches//$'\n'/, }"
  [ -n "$pull_requests" ] && echo "open PR: ${pull_requests//$'\n'/, }"
  return 0
}

has_label() {
  local number="$1" label="$2"
  gh issue view "$number" --json labels --jq '.labels[].name' | grep -qx "$label"
}

command_list() {
  gh issue list --state open --label "$READY_LABEL" --limit 100 \
    --json number,title,assignees,labels \
    --jq ".[] | select((.assignees | length) == 0)
          | select(all(.labels[].name; . != \"${IN_PROGRESS_LABEL}\"))
          | \"#\\(.number)\\t\\(.title)\""
}

command_show() {
  local number="$1" reasons
  gh issue view "$number"
  reasons="$(busy_reasons "$number")"
  if [ -n "$reasons" ]; then
    echo; echo "STATUS: BUSY"; echo "$reasons"
  else
    echo; echo "STATUS: FREE"
  fi
}

command_claim() {
  local number="$1" type="$2" description="$3" slug reasons assignee_count
  slug="DTOX-${number}(${type})/${description}"
  validate_slug "$slug"
  has_label "$number" "$READY_LABEL" || { echo "Issue #${number} is not labeled '${READY_LABEL}'." >&2; exit 1; }
  reasons="$(busy_reasons "$number")"
  if [ -n "$reasons" ]; then
    echo "Issue #${number} is busy, not claiming:" >&2
    echo "$reasons" >&2
    exit 1
  fi
  gh issue edit "$number" --add-assignee "@me" --add-label "$IN_PROGRESS_LABEL" >/dev/null
  assignee_count="$(gh issue view "$number" --json assignees --jq '.assignees | length')"
  if [ "$assignee_count" -gt 1 ]; then
    gh issue edit "$number" --remove-assignee "@me" --remove-label "$IN_PROGRESS_LABEL" >/dev/null
    echo "Someone claimed #${number} at the same time; released mine. Pick another issue." >&2
    exit 1
  fi
  git fetch --quiet origin main
  git switch -c "$slug" origin/main
  git push --quiet -u origin "$slug"
  echo "Claimed #${number}. Branch: ${slug}"
}

command_release() {
  local number="$1"
  gh issue edit "$number" --remove-assignee "@me" --remove-label "$IN_PROGRESS_LABEL" >/dev/null
  echo "Released #${number}. Delete your remote branch yourself if you abandon the work."
}

require_github_cli
cd "$(repository_root)"
case "${1:-}" in
  list) command_list ;;
  show) command_show "${2:?issue number required}" ;;
  claim) command_claim "${2:?issue number required}" "${3:?type required}" "${4:?description required}" ;;
  release) command_release "${2:?issue number required}" ;;
  *) sed -n '2,14p' "$0"; exit 2 ;;
esac
