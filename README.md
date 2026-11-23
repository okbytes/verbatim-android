# Verbatim for Android

Save text, recite it from memory, and see which words you missed or added. This is a native Kotlin app for Android 12 (API 31) and later. It has no account, network permission, or server.

## Try it

On the Mac with the local `.tools/` setup, run `./scripts/emulator.sh` from this folder. Leave it open. Once the Android home screen appears, run `./scripts/run.sh` from this folder in a second terminal. It builds and opens Verbatim without erasing your entries. After code changes, rerun only `./scripts/run.sh`.

On another machine, open this folder in Android Studio, select an Android 12+ device, and run **app**. For a command-line build, use JDK 17, Android SDK Platform 35 and Build Tools 35.0.0. Set `ANDROID_HOME` or specify `sdk.dir` in `local.properties`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Share a sideloadable APK

To build a signed, minified `.apk` that someone can download and install directly on an Android 12+ phone:

```sh
./scripts/build-apk.sh
```

This runs the unit tests and lint, signs a release build, verifies the signature, and writes `dist/Verbatim-v<version>.apk`.

- The first run generates a local release keystore under `~/.config/verbatim/signing/` (outside the repo). **Back up `~/.config/verbatim/signing/`** — future updates signed with the same key will install right over an existing copy without losing the user's saved entries (bump `versionCode` and `versionName` in `app/build.gradle.kts` for updates).
- Host the `.apk` anywhere your recipient can download it (for example a GitHub Release on a public repo, a web page, iCloud Drive, Google Drive, or Dropbox) and send them the link. On their Android phone, they tap the downloaded `.apk`, allow installing unknown apps for their browser or Files app if prompted, and tap **Install**.

## Use it

- **Add entry** saves a title and text. Mark it In Progress or schedule it daily or on one weekday using the segmented schedule picker. The Paste icon recognizes common copied Bible citations; ordinary text-field paste leaves the text alone.
- Tap an entry to read it, then **Recite** to recite the whole text from memory (if the entry is currently due, this counts as a review; otherwise it is practice and never changes a review date). Swipe or long-press any library row for quick actions (Recite/Preview, Edit, Duplicate, Delete); the entry screen also supports Copy, Duplicate, Share, Edit, and Delete.
- Tap a **Due** entry to review it without seeing its text first. Choose **Preview** if you want to read instead. Only **Finish review** on the result advances the schedule; backing out or previewing leaves the entry due.
- **Recite aloud** requires an on-device speech recognizer with an offline model for the phone’s language. Android 12 alone does not guarantee either. Verbatim checks for a local recognizer before asking for microphone access, never falls back to network recognition, and uses segmented/continuous listening so brief pauses don’t cut off a take before you tap Stop. The app never creates an audio file.
- Results preserve original punctuation and line breaks, highlight missed words in crimson, mark extra spoken words in toggleable amber underline, and dot unclear words matched via alternate readings (tap an unclear word to inspect what was heard). The grader doesn’t model homophones, number words, or recognition confidence.
- **Settings** controls title order and the daily reminder time. Allow notifications for reminders. Android may deliver the reminder within a short window after the selected time, especially in battery-saving modes.
- **Export**, **Replace**, and **Add** save and restore Android or iPhone Verbatim backup files, including review dates and the reminder time. Replace validates the file and asks before changing the library. **Add** skips existing titles and imports new entries unscheduled and not In Progress.

## Data and verification

The library lives in an app-private, atomically replaced file. The app displays an error rather than resetting an unreadable library. Export a backup before uninstalling or clearing app data. Keep the V1 reader when changing the format.

The unit suite checks Android backup round-trips, compatibility with earlier private Android libraries, review schedules, grading and paste behavior. On an API 34 emulator the port was also checked through the UI: adding a daily entry, persistence across relaunch and APK update, and the unsupported-speech path.

Before release, test real microphone recognition (including pauses, interruptions and long entries) and background notification delivery on a physical phone. The emulator smoke test does not cover either.
