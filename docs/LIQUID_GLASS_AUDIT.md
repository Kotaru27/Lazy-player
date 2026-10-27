# Lazy Player — Liquid Glass and Playback Quality Audit

Date: 27 October 2026  
Project: `Kotaru27/Lazy-player` · Package: `com.lazy.player` · Version: `0.1.1`

## What has been verified

- GitHub Actions Android build and JVM unit-test job **succeeded** for commit `736cc00e88f7f8bc53e7901106a9436c986d39e9` (run `40454507448`).
- The job produced a debug APK; the exported archive passed ZIP integrity validation. SHA-256 of the unpacked APK: `a64956c2aab9014b5c0b9c89e873b05fa8c27d64f3ad44870ba81de451edc416`.
- Main text on the base `#0C0A14` passes static palette contrast calculations: `#F8F5FF` ≈18.24:1, `#B2ABBF` ≈8.86:1, `#D3BFFF` ≈11.85:1. These are *token ratios*, not automated screenshots or a guarantee of all composite layers.
- Screens and source were reviewed as **one model using Android engineering, visual design and critical QA perspectives**, not independent autonomous workers.

## Changes made

1. Migrated obsolete Kotlin Gradle compiler settings and selected a resolvable Compose BOM; fixed an experimental Material 3 API opt-in.
2. Tightened the glass material in `ui/Glass.kt` to a directional shaded body, restrained inner wash, and a light-catching rim. This keeps the app more restrained than indiscriminate glass-on-everything.
3. Improved album artwork fidelity (`ARGB_8888`) and bounded decode caching (24 MiB) in `ui/Artwork.kt`.
4. Reduced the blurred full-screen artwork backdrop to a 62%-height top region rather than a whole-screen blur. This reduces potential GPU work but **has not been frame-time benchmarked**.
5. Made Now Playing scrollable with width-adaptive transport controls; enlarged primary icon and tab surfaces; made selected bottom navigation visibly distinct.
6. Fixed transient false-empty messaging during the first MediaStore scan and preserved original Media3 queue indexes when removed files are omitted from the displayed queue.
7. Preserved original Media3 playback service, local-only permission model and no-INTERNET manifest.

## Critical findings — remaining

| Priority | Finding | Evidence | Required validation / corrective action |
|---|---|---|---|
| P1 | Glass is an elegant **glass-inspired approximation**, not per-surface live backdrop refraction. | `ui/Glass.kt` uses layered gradients and border; Compose `Modifier.blur` applies to artwork content. | Review actual screenshots; only add selective backdrop/refraction if there is a clear visual benefit and acceptable GPU cost. |
| P1 | No physical-device playback and background lifecycle qualification. | Only cloud JVM tests and APK assembly are verified. | Test MP3/FLAC, pause/resume, headset and Bluetooth, lockscreen, noisy route, Android background restrictions on a phone. |
| P1 | Refresh-rate smoothness is not measured. | No 60/90/120-Hz frame timelines captured. | Run Android frame timing and Macrobenchmark/jank checks on representative devices before declaring motion smooth. |
| P2 | Some metadata and microcopy text remains 11–12sp. | `ui/LazyPlayerScreen.kt`, Now Playing time labels and utility overlines. | Inspect at 130% and 200% system text sizes; increase if unreadable. |
| P2 | Queue sheet's solid dark treatment is not identical to glass controls. | `ModalBottomSheet(containerColor = #211D2C)`. | Check screenshot; tune translucent sheet and rim only if it remains legible over artwork. |
| P2 | Album-art fallback uses a generic equalizer glyph. | `ui/Artwork.kt` no-art state. | Consider a more distinct fallback after reviewing screenshots with missing art. |
| P2 | Signature is a fresh CI debug key, not a stable production identity. | Debug signing is from Android's default Gradle debug build on ephemeral CI. | Before distributing upgrades, introduce securely managed signing; never publish a private keystore. |

## Verification boundaries

**Passed:** Kotlin/Android Gradle assembly and JVM unit tests; APK archive integrity; static source inspection; static colour contrast calculation.  
**Not verified on a device:** Audio output quality, notifications, Bluetooth, storage permission revocation while playing, offline performance, measured frame drops, real file-library scaling, visual fidelity across phone sizes.  
**Current assessment:** A useful Android debug build, but **not a visually certified or production-signed release**. The look must be judged from rendered UI rather than inspiration or source code alone.

## Links

- Source: <https://github.com/Kotaru27/Lazy-player>
- Verified build: <https://github.com/Kotaru27/Lazy-player/actions/runs/40454507448>
- Visual audit workflow: <https://github.com/Kotaru27/Lazy-player/actions/runs/40454323121>