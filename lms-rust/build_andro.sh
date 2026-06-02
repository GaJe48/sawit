#!/usr/bin/env bash
set -e

echo "🚀 Build library dengan cargo-ndk untuk x86_64 dan arm64-v8a..."
cargo ndk -t x86_64 -t arm64-v8a build --release

echo "📦 Generate Kotlin bindings dengan local uniffi-bindgen..."
cargo run --bin uniffi-bindgen generate ./target/x86_64-linux-android/release/liblms_rust.so \
    --language kotlin \
    --out-dir ../app/src/main/kotlin \
    --no-format

echo "📂 Menyiapkan folder jniLibs dan memindahkan .so..."
mkdir -p ../app/src/main/jniLibs/x86_64
cp ./target/x86_64-linux-android/release/liblms_rust.so ../app/src/main/jniLibs/x86_64/

mkdir -p ../app/src/main/jniLibs/arm64-v8a
cp ./target/aarch64-linux-android/release/liblms_rust.so ../app/src/main/jniLibs/arm64-v8a/

echo "✅ Selesai! Library lms_rust (x86_64 & arm64-v8a) siap dipakai di Android Studio."
