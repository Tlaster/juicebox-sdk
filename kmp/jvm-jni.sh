#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)

cd "${REPO_DIR}"

cargo build -p juicebox_sdk_jni --release "$@"

OUT_DIR="artifacts/jni-host"
mkdir -p "${OUT_DIR}"

case "$(uname -s)" in
  Darwin)
    LIB_NAME="libjuicebox_sdk_jni.dylib"
    ;;
  Linux)
    LIB_NAME="libjuicebox_sdk_jni.so"
    ;;
  MINGW* | MSYS* | CYGWIN*)
    LIB_NAME="juicebox_sdk_jni.dll"
    ;;
  *)
    echo "unsupported host OS: $(uname -s)" >&2
    exit 1
    ;;
esac

cp "target/release/${LIB_NAME}" "${OUT_DIR}/${LIB_NAME}"
echo "Wrote ${OUT_DIR}/${LIB_NAME}"
