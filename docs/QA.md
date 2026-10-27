# QA record — Lazy Player v0.1.1

## Verified — 2026-10-27

- Source committed to the public repository `Kotaru27/Lazy-player` with package `com.lazy.player`.
- GitHub Actions run [40454507448](https://github.com/Kotaru27/Lazy-player/actions/runs/40454507448) completed **successfully** at commit `736cc00e88f7f8bc53e7901106a9436c986d39e9`; Android compilation and `:app:testDebugUnitTest` and `:app:assembleDebug` all ran successfully.
- Debug artifact downloaded and archive integrity checked; unpacked APK SHA-256: `a64956c2aab9014b5c0b9c89e873b05fa8c27d64f3ad44870ba81de451edc416`.
- AndroidManifest.xml inspected: local music permissions, MediaSessionService, and **no INTERNET permission**.
- Kotlin screen and service source inspected; MediaStore scans occur off the UI thread; the player uses Media3 session for background operation.
- Static colour-token contrast calculated: main text vs base ≈18.24:1; secondary text vs base ≈8.86:1.
- False-empty state during library scan and queue-index drift from missing queued items patched; both compiled successfully at the commit above.
- The previous first Android build also succeeded at commit `8c515c9`, after repairing incompatible Gradle Kotlin options, a non-published Compose BOM, and a Material3 opt-in.

## Verification still required

- An actual installation and audio playback on physical Android hardware, including MP3/FLAC, lock-screen media controls, audio focus, Bluetooth, headset unplugging, and background restrictions.
- Measured 60/90/120 Hz jank traces or GPU profiling. The full-screen artwork blur was reduced to 62% of height, but frame-time improvement has **not** been measured.
- Screenshot quality and accessibility verification across different phones, Android versions, large fonts, long titles, missing cover images and landscape orientation.
- A stable signing identity is needed before distributing seamless upgrades. Current CI debug APKs are not production-signed.
- Permission revocation during playback and large MediaStore libraries are not device-tested.

## Visual QA execution

- First emulator run [40453227842](https://github.com/Kotaru27/Lazy-player/actions/runs/40453227842) did not reach the app because emulator external storage was not mounted. It does **not** establish an application bug or app UI pass.
- Corrected emulator test harness in [40454323121](https://github.com/Kotaru27/Lazy-player/actions/runs/40454323121), using a single Bash test script and a virtual SD card. Its screenshots must be inspected before any visual-release claim.

## Conclusion

**Build verified; real-device, rendered-UI and premium-motion certification remain conditional.** A source review cannot prove iOS-like optical liquid-glass refraction or a perfectly elegant app. See `docs/LIQUID_GLASS_AUDIT.md` for actionable design findings.
