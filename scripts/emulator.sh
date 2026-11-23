#!/usr/bin/env bash
# Start the local Verbatim emulator in this terminal.
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="$PWD/.tools/sdk" ANDROID_SDK_ROOT="$PWD/.tools/sdk"
export ANDROID_AVD_HOME="$PWD/.tools/avd"

if [[ ! -x "$ANDROID_HOME/emulator/emulator" || ! -f "$ANDROID_AVD_HOME/Verbatim.ini" ]]; then
    echo 'Missing the local emulator or Verbatim virtual device under .tools/.' >&2
    exit 1
fi
if [[ "$("$ANDROID_HOME/platform-tools/adb" -e shell getprop ro.boot.qemu.avd_name 2>/dev/null | tr -d '\r')" == Verbatim ]]; then
    echo 'Verbatim is already running. Use scripts/run.sh to open the app.'
    exit 0
fi
exec "$ANDROID_HOME/emulator/emulator" -avd Verbatim
