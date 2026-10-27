# Lazy Player Preview: app identity and signing policy

## Status — 2026-10-27

- Baseline branch: `redesign/music-glass-v05`; no merge to `main` or public GitHub Release.
- Application ID: **`com.lazy.player.preview`**, unchanged from v0.5.0. **Do not change this ID again** for future preview updates.
- v0.5.1-preview: `versionCode=2` (v0.5.0-preview had `versionCode=1`).
- The new visual identity uses vector `ic_lazy_player_foreground`, adaptive launcher icon, Android 13 themed monochrome icon, and the original legacy vector fallback.
- Build: GitHub Actions review workflow `40475202310` passed JVM unit tests and APK compilation; Android UI emulator run `40474898426` passed 4 tests at normal size and 4 at 135% text / compact display, producing actual screenshots and a short interaction recording.
- APK signing for private download was performed **after** the reproducible GitHub build, using the private certificate in the owner's ChatGPT Library. Never use the raw Actions `app-debug.apk` as a seamless update package.
- Private signing material: personal Library `/Lazy Player/Signing/LazyPlayerPreview-v1.p12` and corresponding `password.txt`. **Not present in the public repository.**
- Signing alias: `lazy-player-preview`.
- Expected signing certificate SHA-256: `8b329cb559e2ff4e39d3106cd8993af0463ceef7da8adf6fbaece6b4da0f58c4`.
- Final signed v0.5.1 APK SHA-256: `348b72be36a9268941afe77d90fbdafa8609347401f0949cd479e746a85c5708`. APK signature v2 and v3 verified by Android SDK apksigner; archive CRC checks pass.

## Upgrade limitation and one-time migration

Android checks both **package name and signing certificate** for an in-place upgrade. The older v0.5.0 APK was generated using an ephemeral debug key (certificate `4550e1cb50b43b9f37d33865daed11a811785aaae7681442c5a659ab25ee904e`); the private key was not archived, and it cannot be reconstructed from an APK. **Therefore v0.5.1 cannot install over the old v0.5.0 debug build.** This cannot be fixed by renaming the APK or changing only the version.

If an old v0.5.0 preview is already installed: use Android Settings to uninstall **only Lazy Player Preview** once, then install this private-signed v0.5.1. This will remove that preview's app-specific favourites and selected-folder permission; your original music files and the other Lazy Player packages are unaffected. Future v0.5.2+ builds must have a higher `versionCode` and be signed with the **same private key** to update v0.5.1 without creating another app.

The private keystore is a personal QA key, not a Play Store production release credential. Do not commit or publish the keystore or password.

## How to deliver future updates

1. Increase `versionCode`, retain `applicationId="com.lazy.player.preview"`.
2. Compile and run tests in GitHub Actions on the review branch.
3. Download the verified build artifact and Android SDK `apksigner.jar` from the cloud job.
4. Retrieve the same keystore/password from the owner's personal ChatGPT Library, sign the APK in a private cloud environment and verify signature, app ID, version, SHA-256.
5. Deliver privately to user; never upload a debug APK directly to Releases as an update-capable package.
6. The system Update intent can proceed normally **only** when the installed preview uses the same new certificate.

## Outstanding checks

- Real-device FLAC + USB-C DAC playback unchanged by this redesign but not physically revalidated.
- Live optical backdrop refraction was not added and should not be claimed.
- Material smoothness and frame timings have not been measured in an optimized release-mode build; screenshots and instrumentation success are not a FPS benchmark.
- Android 8 through 16, cutouts, OEM font overrides and multiple DAC dongles need physical validation.
