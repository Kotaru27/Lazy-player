# Lazy Player — Android cloud build

Repository: https://github.com/Kotaru27/Lazy-player

The workflow `.github/workflows/android.yml` builds and unit-tests this Android app with GitHub Actions, then publishes `lazy-player-debug-apk` containing `app-debug.apk` as a run artifact. No Android software is required on the user's device to run the cloud build.

1. Open the repository **Actions** tab and select **Lazy Player Android Debug APK**.
2. Inspect the newest run from branch `main`. Green unit-tests and assembly steps are required before using an APK.
3. Download artifact **lazy-player-debug-apk** from a successful run. The APK is inside the artifact ZIP.
4. Install only on a personal/test Android phone and manually verify media permissions, local MP3/FLAC playback, background playback, lock-screen controls, queue and favourites.

Debug APKs are not Play Store releases. A source archive is not an APK. A successful compilation is not evidence that playback works on every Android device.
