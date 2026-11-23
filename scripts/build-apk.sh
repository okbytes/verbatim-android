#!/usr/bin/env bash
# Build a signed, minified, sideloadable release APK under dist/.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -d "$PWD/.tools/jdk/zulu-17.jdk/Contents/Home" ]]; then
    export JAVA_HOME="$PWD/.tools/jdk/zulu-17.jdk/Contents/Home"
fi
if [[ -d "$PWD/.tools/sdk" ]]; then
    export ANDROID_HOME="$PWD/.tools/sdk"
    export ANDROID_SDK_ROOT="$PWD/.tools/sdk"
fi
if [[ -d "$PWD/.tools/gradle" ]]; then
    export GRADLE_USER_HOME="$PWD/.tools/gradle"
fi

signing_dir="$PWD/.tools/signing"
keystore="$signing_dir/verbatim-release.jks"
props="$signing_dir/keystore.properties"

if [[ ! -f "$keystore" || ! -f "$props" ]]; then
    mkdir -p "$signing_dir"
    chmod 700 "$signing_dir"
    pass="$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)"
    keytool="$JAVA_HOME/bin/keytool"
    [[ -x "$keytool" ]] || keytool="keytool"
    "$keytool" -genkeypair \
        -v \
        -keystore "$keystore" \
        -storetype PKCS12 \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -alias verbatim \
        -storepass "$pass" \
        -keypass "$pass" \
        -dname "CN=Verbatim, O=Verbatim" >/dev/null
    cat >"$props" <<EOF
storeFile=.tools/signing/verbatim-release.jks
storePassword=$pass
keyAlias=verbatim
keyPassword=$pass
EOF
    chmod 600 "$keystore" "$props"
    echo "Created new release signing keystore at $keystore"
    echo "Back up .tools/signing/ so future APK updates can install over existing installs."
fi

./gradlew testDebugUnitTest lintDebug assembleRelease

version="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
version="${version:-0.1}"
mkdir -p dist
out="dist/Verbatim-v${version}.apk"
cp app/build/outputs/apk/release/app-release.apk "$out"

apksigner="$(find "${ANDROID_HOME:-/nonexistent}/build-tools" -maxdepth 2 -name apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
if [[ -n "$apksigner" && -x "$apksigner" ]]; then
    "$apksigner" verify "$out"
fi

size="$(du -h "$out" | awk '{print $1}')"
sha="$(shasum -a 256 "$out" | awk '{print $1}')"
echo ""
echo "Built signed sideloadable APK:"
echo "  File:   $PWD/$out ($size)"
echo "  SHA256: $sha"
