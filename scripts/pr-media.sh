#!/usr/bin/env bash
# Captures what a change looks like on the device and publishes it for the PR description.
# The GitHub API cannot attach files to a PR body, so the media go to the orphan branch
# "pr-media" (media only, no code, no CI) under DTOX-<issue>/ and are linked by commit.
#
#   screenshot <file.png>          capture the device screen
#   record-start                   start recording the device screen (at most 3 minutes)
#   record-stop <file.gif>         stop the recording and convert it to a GIF (needs ffmpeg)
#   publish <file>...              upload to pr-media and print the Markdown to paste in the PR
#
# The issue number comes from the current slug branch; override it with ISSUE=<number>.
# Choose the device with ANDROID_SERIAL=<serial>. Requires: adb, git, gh; ffmpeg to record.
set -euo pipefail
MEDIA_BRANCH="pr-media"
DEVICE_RECORDING="/sdcard/dtox-pr-media.mp4"
RECORDING_LIMIT_SECONDS=180
GIF_FILTER="fps=10,scale=360:-1:flags=lanczos,split[frames][copy];[copy]palettegen[palette];[frames][palette]paletteuse"
PUBLISH_ATTEMPTS=3
MEDIA_NAME_REGEX='^[a-z0-9][a-z0-9_.-]*\.(png|gif)$'

# Prints the adb to use: ANDROID_HOME's platform-tools when set, else adb on PATH.
adb_command() {
  local sdk_directory="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -n "$sdk_directory" ] && [ -x "$sdk_directory/platform-tools/adb" ]; then
    echo "$sdk_directory/platform-tools/adb"
  else
    echo adb
  fi
}

command_screenshot() {
  local output="$1"
  "$(adb_command)" exec-out screencap -p >"$output"
  echo "Saved $output"
}

command_record_start() {
  # nohup keeps screenrecord alive after this adb shell session ends.
  "$(adb_command)" shell "nohup screenrecord --time-limit $RECORDING_LIMIT_SECONDS \
    $DEVICE_RECORDING >/dev/null 2>&1 &"
  echo "Recording. Stop it with: $0 record-stop <file.gif>"
}

command_record_stop() {
  local output="$1" adb local_video
  command -v ffmpeg >/dev/null 2>&1 || { echo "ffmpeg is needed to convert to GIF." >&2; exit 1; }
  adb="$(adb_command)"
  "$adb" shell pkill -INT screenrecord || { echo "No recording in progress." >&2; exit 1; }
  # screenrecord writes the end of the file after SIGINT; wait until it exits.
  while "$adb" shell pidof screenrecord >/dev/null; do sleep 1; done
  local_video="$(mktemp --suffix=.mp4)"
  "$adb" pull -q "$DEVICE_RECORDING" "$local_video"
  "$adb" shell rm "$DEVICE_RECORDING"
  ffmpeg -loglevel error -y -i "$local_video" -vf "$GIF_FILTER" -loop 0 "$output"
  rm "$local_video"
  echo "Saved $output"
}

# Prints the issue number: ISSUE, or the ticket in the current slug branch.
issue_number() {
  local branch
  if [ -n "${ISSUE:-}" ]; then echo "$ISSUE"; return; fi
  branch="$(git branch --show-current)"
  [[ "$branch" =~ ^DTOX-([0-9]+)\( ]] || { echo "Not on a DTOX-<n> branch; set ISSUE." >&2; exit 1; }
  echo "${BASH_REMATCH[1]}"
}

# Builds a commit on top of the remote media branch with the files added and prints its hash.
# Uses a throwaway index, so the working tree, the index and the current branch stay untouched.
media_commit() {
  local directory="$1"; shift
  local parent media_file blob
  local -a parent_option=()
  GIT_INDEX_FILE="$(mktemp -u)"
  export GIT_INDEX_FILE
  trap 'rm -f "$GIT_INDEX_FILE"' RETURN
  if git ls-remote --exit-code --heads origin "$MEDIA_BRANCH" >/dev/null; then
    git fetch --quiet origin "+refs/heads/$MEDIA_BRANCH:refs/remotes/origin/$MEDIA_BRANCH"
    parent="$(git rev-parse "refs/remotes/origin/$MEDIA_BRANCH")"
    git read-tree "$parent"
    parent_option=(-p "$parent")
  else
    git read-tree --empty
  fi
  for media_file in "$@"; do
    blob="$(git hash-object -w "$media_file")"
    git update-index --add --cacheinfo "100644,$blob,$directory/$(basename "$media_file")"
  done
  git commit-tree "$(git write-tree)" "${parent_option[@]}" -m "${directory}(docs)/add_pr_media"
}

command_publish() {
  local issue directory repository commit media_file attempt
  for media_file in "$@"; do
    [ -f "$media_file" ] || { echo "No such file: $media_file" >&2; exit 1; }
    [[ "$(basename "$media_file")" =~ $MEDIA_NAME_REGEX ]] ||
      { echo "Use lowercase a-z0-9_.- names ending in .png or .gif: $media_file" >&2; exit 1; }
  done
  issue="$(issue_number)"
  directory="DTOX-${issue}"
  repository="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
  # Another agent may publish at the same time: rebuild on the new tip and retry.
  for attempt in $(seq "$PUBLISH_ATTEMPTS"); do
    commit="$(media_commit "$directory" "$@")"
    git push --quiet origin "$commit:refs/heads/$MEDIA_BRANCH" && break
    [ "$attempt" -eq "$PUBLISH_ATTEMPTS" ] && { echo "Could not push to $MEDIA_BRANCH." >&2; exit 1; }
  done
  for media_file in "$@"; do
    media_file="$(basename "$media_file")"
    echo "![${media_file%.*}](https://raw.githubusercontent.com/${repository}/${commit}/${directory}/${media_file})"
  done
}

case "${1:-}" in
  screenshot) command_screenshot "${2:?output file required}" ;;
  record-start) command_record_start ;;
  record-stop) command_record_stop "${2:?output file required}" ;;
  publish) shift; [ "$#" -gt 0 ] || { echo "files required" >&2; exit 2; }; command_publish "$@" ;;
  *) sed -n '2,12p' "$0"; exit 2 ;;
esac
