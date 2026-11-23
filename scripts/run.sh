#!/usr/bin/env bash
# Build, install over the existing app, and launch on the Verbatim emulator.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="$PWD/.tools/jdk/zulu-17.jdk/Contents/Home"
export ANDROID_HOME="$PWD/.tools/sdk" ANDROID_SDK_ROOT="$PWD/.tools/sdk"
export GRADLE_USER_HOME="$PWD/.tools/gradle" ANDROID_USER_HOME="$PWD/.tools/android-user"
adb="$ANDROID_HOME/platform-tools/adb"
if [[ ! -x "$adb" || ! -x "$JAVA_HOME/bin/java" ]]; then
    echo 'Missing the local Android SDK or JDK under .tools/.' >&2
    exit 1
fi

# Pin the serial so plugging in a phone or starting another emulator cannot redirect the install.
serial=''
while read -r device state _; do
    if [[ "$device" == emulator-* && "$state" == device && "$("$adb" -s "$device" shell getprop ro.boot.qemu.avd_name | tr -d '\r')" == Verbatim ]]; then
        serial="$device"
        break
    fi
done < <("$adb" devices)
if [[ -z "$serial" || "$("$adb" -s "$serial" shell getprop sys.boot_completed | tr -d '\r')" != 1 ]]; then
    echo 'Start scripts/emulator.sh, wait for the Android home screen, then run this again.' >&2
    exit 1
fi
./gradlew :app:assembleDebug
"$adb" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
exec "$adb" -s "$serial" shell am start -W -n app.verbatim.android/app.verbatim.MainActivity
