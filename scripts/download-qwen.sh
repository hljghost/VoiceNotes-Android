#!/usr/bin/env bash
# Download Qwen3.5-2B Q4_K_M GGUF from ModelScope into Models/LLM/qwen3.5-2b-q4-k-m/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/Models/LLM/qwen3.5-2b-q4-k-m"
mkdir -p "$DEST"

NAME="Qwen3.5-2B-Q4_K_M.gguf"
SHA="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"
BYTES="1280835840"
REPO="unsloth/Qwen3.5-2B-GGUF"
URL="https://www.modelscope.cn/models/${REPO}/resolve/master/${NAME}"
OUT="${DEST}/${NAME}"

echo "==> $NAME from ModelScope $REPO"
curl -L --fail --retry 3 -o "$OUT" "$URL"
got=$(shasum -a 256 "$OUT" | awk '{print $1}')
size=$(wc -c <"$OUT" | tr -d ' ')
if [[ "$got" != "$SHA" || "$size" != "$BYTES" ]]; then
  echo "SHA/size mismatch" >&2
  echo " expected $SHA ($BYTES)" >&2
  echo " got      $got ($size)" >&2
  exit 1
fi
echo "OK → $OUT"
echo "adb push into: .../files/VoiceNotes/Models/LLM/qwen3.5-2b-q4-k-m/"
