#!/usr/bin/env bash
# One-time local setup: enables the versioned git hooks and the merge drivers.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
cd "$(repository_root)"

chmod +x scripts/*.sh .githooks/*
git config core.hooksPath .githooks
git config merge.dtox-version.name "Keep highest VERSION"
git config merge.dtox-version.driver "scripts/merge-driver-version.sh %O %A %B"
git config merge.dtox-readme.name "README merge ignoring version marker"
git config merge.dtox-readme.driver "scripts/merge-driver-readme.sh %O %A %B"
echo "D-TOX git hooks and merge drivers enabled."
