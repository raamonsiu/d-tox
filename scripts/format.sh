#!/usr/bin/env bash
# Applies Prettier (md, json, yml) to the whole repository. Kotlin is formatted by ktlint.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
cd "$(repository_root)"
npx --yes "prettier@${PRETTIER_VERSION}" --write .
