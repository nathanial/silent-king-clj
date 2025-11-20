#!/bin/bash

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$SCRIPT_DIR/.."

echo "Silent King - Starting..."

# Detect platform
OS="$(uname -s)"
ARCH="$(uname -m)"

echo "Detected OS: $OS"
echo "Detected Architecture: $ARCH"

# Determine the alias to use
if [[ "$OS" == "Darwin" ]]; then
    if [[ "$ARCH" == "arm64" ]]; then
        ALIAS="macos-arm64"
        echo "Running on macOS Apple Silicon"
    elif [[ "$ARCH" == "x86_64" ]]; then
        ALIAS="macos-x64"
        echo "Running on macOS Intel"
    else
        echo "Error: Unsupported macOS architecture: $ARCH"
        exit 1
    fi
elif [[ "$OS" == "Linux" ]]; then
    if [[ "$ARCH" == "x86_64" ]]; then
        ALIAS="linux-x64"
        echo "Running on Linux x64"
    else
        echo "Error: Unsupported Linux architecture: $ARCH"
        exit 1
    fi
elif [[ "$OS" == MINGW* ]] || [[ "$OS" == MSYS* ]] || [[ "$OS" == CYGWIN* ]]; then
    if [[ "$ARCH" == "x86_64" ]]; then
        ALIAS="windows-x64"
        echo "Running on Windows x64"
    else
        echo "Error: Unsupported Windows architecture: $ARCH"
        exit 1
    fi
else
    echo "Error: Unsupported operating system: $OS"
    exit 1
fi

# Run the application
echo "Launching with alias: $ALIAS"
echo ""
clojure -M:$ALIAS -m silent-king.core
