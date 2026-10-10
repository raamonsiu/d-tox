#!/usr/bin/env bash
# Git merge driver for VERSION: keeps the highest version. Args: %O %A %B (base, ours, theirs).
set -euo pipefail
source "$(dirname "$0")/lib.sh"
ours_file="$2"
theirs_file="$3"
highest_version "$(tr -d '[:space:]' <"$ours_file")" "$(tr -d '[:space:]' <"$theirs_file")" >"$ours_file"
