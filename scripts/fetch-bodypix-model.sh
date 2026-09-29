#!/usr/bin/env bash
set -euo pipefail

MODEL_NAME="bodypix-mobilenet-1.0"
EXPECTED_SHA="c64d6f3252217f9bd0ba790ac2a6ac8b45fc002767c97379cb2e8a3ce7317b56"
URL="https://mlmodels.openrndr.org/${MODEL_NAME}.pb"
DEST="${1:-${XDG_DATA_HOME:-$HOME/.local/share}/cosmosis/models/${MODEL_NAME}.pb}"

mkdir -p "$(dirname "$DEST")"

sha256_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$path" | awk '{print $1}'
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "$path" <<'PY'
import hashlib, sys
h=hashlib.sha256()
with open(sys.argv[1],"rb") as f:
    for block in iter(lambda:f.read(1024*1024),b""):
        h.update(block)
print(h.hexdigest())
PY
  else
    echo "no SHA-256 tool available (sha256sum, shasum, or python3 required)" >&2
    return 2
  fi
}

verify_file() {
  local path="$1"
  local found
  found="$(sha256_file "$path")"
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
