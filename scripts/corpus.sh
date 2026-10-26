#!/usr/bin/env bash
# Test book corpus described by app/src/test/resources/corpus/manifest.json.
#
#   fetch    (default) download the non-versioned files into downloads/ and verify every sha256
#   verify   only verify the files already present (missing downloads are reported, not fetched)
#
# Downloads are atomic (temporary file, checksum, rename) and paced, with an identifiable User-Agent
# (docs/05-content-sources.md). A checksum mismatch means the upstream file changed: re-check its
# license and content before updating the manifest. Requires: jq, curl, sha256sum.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

CORPUS_DIRECTORY="app/src/test/resources/corpus"
MANIFEST="${CORPUS_DIRECTORY}/manifest.json"
USER_AGENT="D-TOX test corpus (https://github.com/raamonsiu/d-tox)"
SECONDS_BETWEEN_DOWNLOADS=2

require_tools() {
  local tool
  for tool in jq curl sha256sum; do
    command -v "$tool" >/dev/null 2>&1 || { echo "Missing required tool: ${tool}" >&2; exit 1; }
  done
}

# True when the file exists and its sha256 matches the expected one.
checksum_matches() {
  local file_path="$1" expected_checksum="$2"
  [ -f "$file_path" ] && [ "$(sha256sum "$file_path" | cut -d' ' -f1)" = "$expected_checksum" ]
}

# Downloads url to file_path through a temporary file; keeps it only if the checksum matches.
download_verified() {
  local url="$1" file_path="$2" expected_checksum="$3" temporary_path
  mkdir -p "$(dirname "$file_path")"
  temporary_path="$(mktemp "${file_path}.partial.XXXXXX")"
  if ! curl --fail --silent --show-error --location --compressed --retry 3 --retry-delay 5 \
    --user-agent "$USER_AGENT" --output "$temporary_path" "$url"; then
    rm -f "$temporary_path"
    return 1
  fi
  if ! checksum_matches "$temporary_path" "$expected_checksum"; then
    echo "  sha256 mismatch (upstream changed?): $(sha256sum "$temporary_path" | cut -d' ' -f1)" >&2
    rm -f "$temporary_path"
    return 1
  fi
  sync "$temporary_path" 2>/dev/null || sync
  mv -f "$temporary_path" "$file_path"
}

# Processes every manifest entry; prints one status line each and returns 1 if any failed.
process_corpus() {
  local mode="$1" failures=0 identifier relative_path versioned expected_checksum url file_path
  # The url goes last: read collapses consecutive tabs, so an empty field must not sit in the middle.
  while IFS=$'\t' read -r identifier relative_path versioned expected_checksum url; do
    file_path="${CORPUS_DIRECTORY}/${relative_path}"
    if checksum_matches "$file_path" "$expected_checksum"; then
      echo "ok       ${identifier}"
    elif [ "$versioned" = "true" ] || [ "$mode" = "verify" ]; then
      echo "FAILED   ${identifier}: missing or wrong sha256 (${relative_path})" >&2
      failures=$((failures + 1))
    else
      echo "fetch    ${identifier}"
      if download_verified "$url" "$file_path" "$expected_checksum"; then
        sleep "$SECONDS_BETWEEN_DOWNLOADS"
      else
        echo "FAILED   ${identifier}: ${url}" >&2
        failures=$((failures + 1))
      fi
    fi
  done < <(jq -r '.files[] | [.id, .path, (.versioned | tostring), .sha256, (.url // "")] | @tsv' "$MANIFEST")
  [ "$failures" -eq 0 ] || { echo "${failures} corpus file(s) failed." >&2; return 1; }
  echo "Corpus OK."
}

require_tools
cd "$(repository_root)"
case "${1:-fetch}" in
  fetch | verify) process_corpus "${1:-fetch}" ;;
  *)
    sed -n '2,10p' "$0"
    exit 2
    ;;
esac
