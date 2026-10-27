# QA record — Lazy Player v0.1.1

## Completed within this response

- Uploaded Poweramp APK inspected at ZIP metadata level (3825 entries, approximately 21.9 MiB; `META-INF/ZACKMODZ.RSA` present). **Signature authenticity/security NOT established**. No binary or resources imported from it.
- Source-level review: MediaStore query, audio permission split, no Internet permission, ExoPlayer service, MediaSession connection, app refresh action, persistent favourites, 7 pure track-filter checks.
- Kotlin `TrackFilters.kt` compiled and executed with `kotlinc` 1.9.0: **7/7 runtime checks passed**.
- Android manifest parsed as valid XML.
- Seven static project invariants checked: offline/no Internet, media permissions, media service, refresh wiring and local scan.
- Kotlin source delimiter balance checked for all authored `.kt` files. This is *not* Android compilation.

## Not run / not verified

- Gradle/Android dependency resolution and APK assembly: no Android SDK/Gradle runner configured in current execution environment.
- Android emulator/device functional test, audio quality, lock-screen notifications, headsets, Bluetooth, Android 8–16 behavior, smoothness at 60/100/120Hz.
- Real cross-device MediaStore query; folder edge cases, metadata fallback, play/queue interactions under permission revocation.
- GitHub Actions cloud workflow: specified in source but not triggered (dedicated repository is now available; new build run not yet verified).

## Critical reviewer notes

- A local source code archive is not evidence of an installable app. Cloud build and device acceptance tests are release gates.
- Layered glass is visually inspired by the reference, not a literal implementation of iOS Liquid Glass / pixel-accurate refraction.
- No guarantee of codec playback on a given phone; ExoPlayer relies on Android supported decoders for many formats.
- The bundled Poweramp APK signature label is unusual and should not be treated as a verified official release.

## Build candidate review — 2026-10-27

- Updated Android Gradle plugin to `8.13.2` for compatibility with Kotlin `2.3.0`.
- Added explicit `androidx.compose.animation:animation` dependency used by `AnimatedVisibility`.
- Pure Kotlin filter file compiled with installed Kotlin compiler 1.9; seven manual runtime smoke assertions passed.
- See `docs/CLOUD_BUILD.md` for the GitHub Actions build path.
- Android application compilation **NOT RUN**: cloud container lacks Gradle/Android SDK, downloads failed, and the dedicated GitHub repository exists, but no successful Android build is yet confirmed. This is a build **candidate**, not an APK or verified release.
