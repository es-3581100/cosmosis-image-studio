#!/usr/bin/env bash
set -euo pipefail

MODEL_NAME="FALSR-A-1.0"
EXPECTED_SHA="639cd2ea510990fa58855a7a15bd1ea0d8756b6e6ffe7a980d0427f61f2fb4a1"
URL="https://mlmodels.openrndr.org/${MODEL_NAME}.pb"
DEST="${1:-${XDG_DATA_HOME:-$HOME/.local/share}/cosmosis/models/${MODEL_NAME}.pb}"

mkdir -p "$(dirname "$DEST")"

verify() {
  local found
  found="$(sha256sum "$DEST" | awk '{print $1}')"
  if [[ "$found" != "$EXPECTED_SHA" ]]; then
    echo "hash mismatch: $found != $EXPECTED_SHA" >&2
    return 1
  fi
}

if [[ -f "$DEST" ]]; then
  verify
  printf '%s\n' "$DEST"
  exit 0
fi

tmp="${DEST}.tmp.$$"
trap 'rm -f "$tmp"' EXIT
curl --fail --location --proto '=https' --tlsv1.2 "$URL" --output "$tmp"
mv "$tmp" "$DEST"
verify
printf '%s\n' "$DEST"
