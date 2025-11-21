#!/bin/bash
#
# Image Generator Wrapper
# Usage: ./scripts/generate-images.sh --prompts <file> --reference-images <files...> --output <dir>
#

if [ -z "$OPENROUTER_API_KEY" ] && [ -f ".env" ]; then
  export $(grep -v '^#' .env | xargs)
fi

if [ -z "$OPENROUTER_API_KEY" ]; then
  echo "Warning: OPENROUTER_API_KEY not found in environment. Application may fail if not provided elsewhere."
fi

clojure -M:image-gen "$@"

