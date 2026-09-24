#!/bin/bash
# Run once after cloning: ./scripts/install-hooks.sh
# Points Git at the tracked .githooks/ folder so hook updates apply without reinstalling.
set -e
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
chmod +x .githooks/* 2>/dev/null || true
git update-index --chmod=+x .githooks/pre-commit scripts/install-hooks.sh 2>/dev/null || true
echo "Hooks enabled from .githooks/ (release hygiene + documentation drift)."
