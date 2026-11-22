#!/usr/bin/env bash
# Download SenseVoice Small int8 from ModelScope (mainland) into Models/ASR/sensevoice-small/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/Models/ASR/sensevoice-small"
mkdir -p "$DEST"

REPO="pengzhendong/sherpa-onnx-sense-voice-zh-en-ja-ko-yue"
BASE="https://www.modelscope.cn/models/${REPO}/resolve/master"

download() {
  local name="$1" sha="$2" bytes="$3"
  local url="${BASE}/${name}"
  local out="${DEST}/${name}"
  echo "==> $name"
  curl -L --fail --retry 3 -o "$out" "$url"
  local got
  got=$(shasum -a 256 "$out" | awk '{print $1}')
  local size
  size=$(wc -c <"$out" | tr -d ' ')
  if [[ "$got" != "$sha" || "$size" != "$bytes" ]]; then
    echo "SHA/size mismatch for $name" >&2
    echo " expected $sha ($bytes)" >&2
    echo " got      $got ($size)" >&2
    exit 1
  fi
  echo "OK $name"
}

download "model.int8.onnx" "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51" "239233841"
download "tokens.txt" "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc" "315894"
echo "Done → $DEST"
echo "Push into app files: Android/data/.../files/VoiceNotes/Models/ASR/sensevoice-small/"
