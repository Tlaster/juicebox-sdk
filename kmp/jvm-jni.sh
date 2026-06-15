#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)

cd "${REPO_DIR}"

cargo build -p juicebox_sdk_jni --release "$@"

TARGET=""
EXPECT_TARGET_VALUE=0
for arg in "$@"; do
  if [[ "${EXPECT_TARGET_VALUE}" == "1" ]]; then
    TARGET="${arg}"
    EXPECT_TARGET_VALUE=0
    continue
  fi

  case "${arg}" in
    --target)
      EXPECT_TARGET_VALUE=1
      ;;
    --target=*)
      TARGET="${arg#--target=}"
      ;;
  esac
done

if [[ -n "${TARGET}" ]]; then
  TARGET_DIR="${CARGO_TARGET_DIR:-target}/${TARGET}/release"
  case "${TARGET}" in
    aarch64-apple-darwin)
      OS_NAME="macos"
      ARCH_NAME="aarch64"
      LIB_NAME="libjuicebox_sdk_jni.dylib"
      ;;
    x86_64-apple-darwin)
      OS_NAME="macos"
      ARCH_NAME="x86_64"
      LIB_NAME="libjuicebox_sdk_jni.dylib"
      ;;
    aarch64-unknown-linux-gnu)
      OS_NAME="linux"
      ARCH_NAME="aarch64"
      LIB_NAME="libjuicebox_sdk_jni.so"
      ;;
    x86_64-unknown-linux-gnu)
      OS_NAME="linux"
      ARCH_NAME="x86_64"
      LIB_NAME="libjuicebox_sdk_jni.so"
      ;;
    aarch64-pc-windows-msvc | aarch64-pc-windows-gnu)
      OS_NAME="windows"
      ARCH_NAME="aarch64"
      LIB_NAME="juicebox_sdk_jni.dll"
      ;;
    x86_64-pc-windows-msvc | x86_64-pc-windows-gnu)
      OS_NAME="windows"
      ARCH_NAME="x86_64"
      LIB_NAME="juicebox_sdk_jni.dll"
      ;;
    *)
      echo "unsupported JVM JNI target: ${TARGET}" >&2
      exit 1
      ;;
  esac
else
  TARGET_DIR="${CARGO_TARGET_DIR:-target}/release"
  ARCH=$(uname -m)
  case "${ARCH}" in
    arm64 | aarch64)
      ARCH_NAME="aarch64"
      ;;
    x86_64 | amd64)
      ARCH_NAME="x86_64"
      ;;
    *)
      echo "unsupported host architecture: ${ARCH}" >&2
      exit 1
      ;;
  esac

  case "$(uname -s)" in
    Darwin)
      OS_NAME="macos"
      LIB_NAME="libjuicebox_sdk_jni.dylib"
      ;;
    Linux)
      OS_NAME="linux"
      LIB_NAME="libjuicebox_sdk_jni.so"
      ;;
    MINGW* | MSYS* | CYGWIN*)
      OS_NAME="windows"
      LIB_NAME="juicebox_sdk_jni.dll"
      ;;
    *)
      echo "unsupported host OS: $(uname -s)" >&2
      exit 1
      ;;
  esac
fi

RESOURCE_DIR="artifacts/jni-host/juicebox/native/${OS_NAME}-${ARCH_NAME}"
mkdir -p "${RESOURCE_DIR}"

cp "${TARGET_DIR}/${LIB_NAME}" "${RESOURCE_DIR}/${LIB_NAME}"

# Keep the old flat location useful for local development with -Djava.library.path.
mkdir -p "artifacts/jni-host"
cp "${TARGET_DIR}/${LIB_NAME}" "artifacts/jni-host/${LIB_NAME}"

echo "Wrote ${RESOURCE_DIR}/${LIB_NAME}"
