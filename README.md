# Workout Companion

Workout Companion is a local-first Android workout tracker with a Wear OS companion app. It is built for strength training programs where progression, rest timing, history snapshots, and quick set logging matter more than social feeds or analytics dashboards.

## Features

- Start from a curated exercise library of common gym movements, then rename, archive, or add custom exercises as needed.
- Build training programs with multiple training days.
- Configure each exercise with sets, target reps, rep range, working weight, increment, rest time, setup notes, per-set targets, and warm-up schemes.
- Duplicate training days and exercise configurations without sharing mutable progression state.
- Use the same exercise in multiple workouts with independent progression.
- Start the recommended workout and log prescribed sets with one tap.
- Log modified sets, failed sets, skipped sets, AMRAP sets, drop sets, and extra normal sets.
- Configure and perform supersets.
- Run persisted rest timers with in-app feedback, Android notification behavior, overtime display, and Wear OS haptics.
- Replace an exercise for the current workout only when equipment is unavailable.
- Review completed workouts in read-only history with calendar navigation and support for multiple workouts on the same day.
- Keep historical workout details as snapshots, so later program edits do not rewrite what was performed.
- Export and restore local backups.
- Use a Wear OS companion during active workouts for glanceable targets, set completion, rest status, and haptic feedback.

## Project Structure

- `app`: Android phone app.
- `wear`: Wear OS companion app.
- `wear-protocol`: Shared phone-watch protocol models.
- `SPEC.md`: Product and implementation specification.
- `.github/workflows/build-release-apk.yml`: Release APK build workflow.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Room
- Kotlin coroutines and Flow
- Android Gradle Plugin
- Wear OS Data Layer

## Requirements

- Android Studio with Android SDK 35 installed.
- JDK 17 or newer.
- Android device or emulator running Android 8.0 or newer.
- Wear OS device or emulator for the companion app.

## Build And Test

On Windows:

```powershell
.\gradlew.bat test :app:assembleDebug :wear:assembleDebug
```

Build the Android instrumentation test APK:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```

Build release APKs locally:

```powershell
.\gradlew.bat assembleRelease
```

Release signing is configured through environment variables:

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The GitHub release workflow expects matching repository secrets, including a base64-encoded keystore in `ANDROID_KEYSTORE_BASE64`.

## Data And Privacy

Workout Companion stores workout data locally in the app database. Backup and restore are explicit user actions. The app is designed around local workout tracking and does not include social sharing, cloud sync, ads, or analytics in the current version.

## Current Status

This repository represents the MVP version described in `SPEC.md`. The current focus is reliability: fast logging, correct progression, stable rest timing, Wear OS companion behavior, readable history, and immutable workout snapshots.
