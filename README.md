# Workout Companion

Workout Companion 1.6 is a local-first Android workout tracker with a Wear OS
companion. It supports strength and duration-based programs, quick set logging,
progression, timers, and immutable workout history without requiring an account.

## Features

- `WEIGHT_REPS`, `REPS`, and `DURATION` exercise tracking.
- Double progression, including fixed rep ranges such as 8–8.
- Advanced None, Minimal, Standard, Heavy, and Custom warm-up schemes with
  percentage or fixed loads and configurable kg/lb rounding.
- Per-set targets, supersets, AMRAP, drop, extra, failed, and skipped sets.
- Rest and duration timers with persisted lifecycle and notification handling.
- Read-only workout history and per-exercise progress.
- Kilogram and pound display with canonical local storage.
- Portable Program export/import and full local backup/restore.
- Wear OS active-workout companion with phone-authoritative state.
- Optional, write-only Health Connect sync for finalized workouts.

The implementation details and behavior contracts are maintained in [SPEC.md](SPEC.md).

## Project Structure

- `app`: Android phone app.
- `wear`: Wear OS companion app.
- `wear-protocol`: Shared phone-watch protocol models and codecs.

## Requirements

- Android Studio with Android SDK 36 installed.
- JDK 17 or newer.
- Android 8.0/API 26 or newer for the phone app.
- A Wear OS emulator or device for companion testing.

## Build and Test

On Windows, run all JVM unit tests:

```powershell
.\gradlew.bat test
```

Run all phone instrumentation tests on a connected Android device or emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Build phone and Wear debug and release variants:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :wear:assembleDebug :wear:assembleRelease
```

Release signing uses these environment variables:

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The GitHub Release workflow also expects `ANDROID_KEYSTORE_BASE64` as a repository
secret. Pull requests and pushes to `master` run unit tests and phone/Wear debug
builds in CI. Instrumentation tests remain a required local pre-release check.

## 1.6 Release Checklist

- Provide a public privacy policy before publishing.
- Complete the Play Console Health/Fitness declaration.
- Ensure the `WRITE_EXERCISE` disclosure matches the in-app Health Connect rationale.
- Configure and verify release signing.
- Run all instrumentation tests on a real or emulated Android device.
- Manually smoke-test Health Connect writes with Health Connect Toolbox.
- Manually smoke-test active workouts on a Wear emulator or device.

## Data and Privacy

Workout data remains in the local Room database unless the user explicitly exports
a backup or Program file, or enables write-only Health Connect synchronization.
The app has no account system, ads, or analytics.

## Status

The repository is the Workout Companion 1.6 release-candidate codebase. Feature work
for 1.6 is complete; release readiness depends on automated verification, release
signing, and the manual checks above.
