#!/usr/bin/env bash
set -euo pipefail

COMMIT="bb6333e62b17a9a0fc12ef889bc3642428e6f4f4"
MODEL_PATH="orml-image-classifier/src/main/resources/tfmodels/v3-large-minimalistic_224_1.0_float.pb"
EXPECTED_BLOB="2e03a7f49b2bf46dc04349d8af9b669d2fca484e"
EXPECTED_SIZE="15923156"
URL="https://raw.githubusercontent.com/openrndr/orml/$COMMIT/$MODEL_PATH"
DEST="${1:-${XDG_DATA_HOME:-$HOME/.local/share}/cosmosis/models/v3-large-minimalistic_224_1.0_float.pb}"

mkdir -p "$(dirname "$DEST")"

verify() {
  local size found
  size="$(stat -c '%s' "$DEST")"
  if [[ "$size" != "$EXPECTED_SIZE" ]]; then
    echo "size mismatch: $size != $EXPECTED_SIZE" >&2
    return 1
  fi
  found="$(git hash-object "$DEST")"
  if [[ "$found" != "$EXPECTED_BLOB" ]]; then
    echo "Git blob mismatch: $found != $EXPECTED_BLOB" >&2
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
