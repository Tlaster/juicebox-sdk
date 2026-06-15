# Juicebox SDK KMP

This module provides a Kotlin Multiplatform facade for the Juicebox SDK.

The public API lives in `xyz.juicebox.sdk.kmp` to avoid colliding with the
existing Android-only `xyz.juicebox.sdk` artifact. Android delegates to the
existing Android/JNI wrapper and packages the same JNI libraries from
`artifacts/jni`.

Supported targets today:

- Android: functional, backed by the existing Android SDK wrapper.
- JVM: functional via the existing Rust JNI bridge. Published artifacts bundle
  JNI libraries under `juicebox/native`; local builds can populate them with
  `./jvm-jni.sh` and still fall back to `-Djava.library.path=../artifacts/jni-host`.
- iOS/macOS: functional through Kotlin/Native cinterop over the existing Rust
  FFI bridge. Build the matching static libraries with `./native-ffi.sh`.
- wasmJs: functional through Kotlin/Wasm JS interop over the existing
  `juicebox-sdk` wasm-bindgen npm package.
- Linux: functional through Kotlin/Native cinterop over the existing Rust FFI
  bridge. Build the matching static libraries with `./native-ffi.sh`.

Build from this directory:

```sh
../android/gradlew assemble
```

Build Native FFI static libraries before linking Native apps:

```sh
./native-ffi.sh
```

You can also build one target at a time:

```sh
./native-ffi.sh aarch64-apple-darwin
```

The script writes `libjuicebox_sdk_ffi.a` into
`../artifacts/ffi/<cargo-target>`, matching the linker paths configured in
Gradle.

The wasm target imports the published npm package `juicebox-sdk`. Override the
npm version with `-PjuiceboxWasmVersion=<version>` when publishing a different
KMP version.

Example:

```kotlin
import xyz.juicebox.sdk.kmp.AuthToken
import xyz.juicebox.sdk.kmp.Client
import xyz.juicebox.sdk.kmp.Configuration
import xyz.juicebox.sdk.kmp.RealmId

val client = Client(
    configuration = Configuration(configurationJson),
    authTokens = tokenMap.mapKeys { (realmId, _) -> RealmId(realmId) }
        .mapValues { (_, token) -> AuthToken(token) },
)

val secret = client.recover(pin = "1234".encodeToByteArray())
```
