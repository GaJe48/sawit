#!/usr/bin/env bash
set -e

PROFILE="debug"
CARGO_FLAG=""
COMPILE_AMD=false

for arg in "$@"; do
    case $arg in
        -r)
            PROFILE="release"
            CARGO_FLAG="--release"
            ;;
        -amd)
            COMPILE_AMD=true
            ;;
    esac
done

cargo install cargo-ndk
cargo install uniffi --features="cli"

CARGO_NDK_TARGETS=("-t" "arm64-v8a")
if [ "$COMPILE_AMD" = true ]; then
    CARGO_NDK_TARGETS+=("-t" "x86_64")
    echo "🚀 Build library dengan cargo-ndk untuk arm64-v8a DAN x86_64 (Mode: $PROFILE)..."
else
    echo "🚀 Build library dengan cargo-ndk untuk arm64-v8a (Mode: $PROFILE)..."
fi

cargo ndk "${CARGO_NDK_TARGETS[@]}" build $CARGO_FLAG

echo "📦 Generate Kotlin bindings dengan uniffi-bindgen..."
uniffi-bindgen generate "./target/aarch64-linux-android/$PROFILE/liblms_rust.so" \
    --language kotlin \
    --out-dir ../app/src/main/kotlin \
    --no-format

echo "📂 Menyiapkan folder jniLibs dan memindahkan .so..."

mkdir -p ../app/src/main/jniLibs/arm64-v8a
cp "./target/aarch64-linux-android/$PROFILE/liblms_rust.so" ../app/src/main/jniLibs/arm64-v8a/

if [ "$COMPILE_AMD" = true ]; then
    mkdir -p ../app/src/main/jniLibs/x86_64
    cp "./target/x86_64-linux-android/$PROFILE/liblms_rust.so" ../app/src/main/jniLibs/x86_64/
    echo "✅ Selesai! Library lms_rust (arm64-v8a & x86_64) siap dipakai di Android Studio."
else
    echo "✅ Selesai! Library lms_rust (arm64-v8a) siap dipakai di Android Studio."
fi