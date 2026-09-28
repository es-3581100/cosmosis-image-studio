#!/usr/bin/env bash
set -euo pipefail

MODEL_NAME="u2netp-320x320-float32-1.0"
EXPECTED_SHA="79e8757f7c5342c4f2b0dab4c66c4bc128b077c9541db5c6a5b363ff75c8b54a"
URL="https://mlmodels.openrndr.org/${MODEL_NAME}.pb"
DEST="${1:-${XDG_DATA_HOME:-$HOME/.local/share}/cosmosis/models/${MODEL_NAME}.pb}"

mkdir -p "$(dirname "$DEST")"

verify_file() {
  local path="$1"
  local found
  found="$(sha256sum "$path" | awk '{print $1}')"
  if [[ "$found" != "$EXPECTED_SHA" ]]; then
    echo "hash mismatch for $path: $found != $EXPECTED_SHA" >&2
    return 1
  fi
}

if [[ -f "$DEST" ]]; then
  verify_file "$DEST"
  printf '%s\n' "$DEST"
  exit 0
fi

tmp="${DEST}.tmp.$$"
trap 'rm -f "$tmp"' EXIT
curl --fail --location --proto '=https' --tlsv1.2 "$URL" --output "$tmp"
verify_file "$tmp"
mv "$tmp" "$DEST"
trap - EXIT
printf '%s\n' "$DEST"
