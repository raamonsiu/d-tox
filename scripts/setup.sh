#!/usr/bin/env bash
# One-time local setup: enables the versioned git hooks.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
cd "$(repository_root)"

chmod +x scripts/*.sh .githooks/*
git config core.hooksPath .githooks
# Clones set up before D-33 still register the removed version merge drivers.
git config --remove-section merge.dtox-version 2>/dev/null || true
git config --remove-section merge.dtox-readme 2>/dev/null || true
echo "D-TOX git hooks enabled."
