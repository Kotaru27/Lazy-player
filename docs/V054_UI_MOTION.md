# Lazy Player v0.5.4: identity and motion pass

Baseline: `9e9ed8b74adb043529d0f8dd5e616d6fa261d9b0` (v0.5.3). Review branch: `redesign/identity-motion-v054`.

## Actual changes
- App-wide Compose text style explicitly uses Android SansSerif and preserves Android multilingual fallback. No unlicensed font files or fonts were imported.
- Songs / Albums / Artists now display collection-relevant counts while their Play all / Shuffle actions remain based on the filtered song queue.
- Missing-cover artwork uses a subtle record-groove motif instead of first-letter strings; full-screen fallback can show the album label.
- Album images crossfade; during asynchronous album-art decode, the last cover is retained until the new result completes. No new network loading, no audio engine changes.
- Collection content fades from 82% to 100% opacity on tab/filter changes without simultaneously rendering two full lazy lists.
- Now Playing artwork gets a restrained cover-only scale transition on open/close. This is **not** a precise shared-element geometry transform.
- Interpolation duration for sampled playback progress grows to 525 ms from 440 ms, avoiding the 60-ms end-of-step gap at nominal 500-ms samples. This is a code-level pacing improvement; actual frame metrics are not yet known.

## Critical QA requirements
- Compile/JVM tests with Gradle on the review branch; run Android Compose instrumentation at normal, 135%, and 200% text scales.
- Recheck 120-track synthetic library, album/artist counts, rapid tabs, gesture boundaries, now player open/close, and fallback on missing art.
- Compare actual screenshots and runtime traces. A passed instrumented test does NOT certify smoothness.
- Keep the same `com.lazy.player.preview` ID. v0.5.4-preview versionCode 5 must use the exact existing owner signing certificate to update the v0.5.3 release.
- Do not silently publish to main or overwrite existing immutable release assets.

## Not completed in this scoped upgrade
- Live backdrop capture/refraction and exact shared-element mini-player geometry still need controlled compatibility tests.
- Physical USB DAC/FLAC output and high-refresh real-device frame profiling remain out of cloud emulator coverage.
