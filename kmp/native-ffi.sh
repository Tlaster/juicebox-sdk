#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

TARGETS=(
  aarch64-apple-ios
  x86_64-apple-ios
  aarch64-apple-ios-sim
  aarch64-apple-darwin
  x86_64-apple-darwin
  x86_64-unknown-linux-gnu
  aarch64-unknown-linux-gnu
)

if [[ $# -gt 0 ]]; then
  TARGETS=("$@")
fi

cd "${REPO_ROOT}"

for target in "${TARGETS[@]}"; do
  echo "building juicebox_sdk_ffi for ${target}"
  CARGO_BUILD_TARGET="${target}" swift/ffi.sh -r
done
