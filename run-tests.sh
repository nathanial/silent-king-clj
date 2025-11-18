#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Detect platform and select the appropriate alias
if [[ "$OSTYPE" == "darwin"* ]]; then
  if [[ $(uname -m) == "arm64" ]]; then
    PLATFORM_ALIAS="macos-arm64"
  else
    PLATFORM_ALIAS="macos-x64"
  fi
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
  PLATFORM_ALIAS="linux-x64"
elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
  PLATFORM_ALIAS="windows-x64"
else
  echo "Unsupported platform: $OSTYPE"
  exit 1
fi

exec clj -M:$PLATFORM_ALIAS:test
