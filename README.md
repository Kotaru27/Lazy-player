# Lazy Player — minimalist Android music player

**Version:** 0.1.1 build candidate · **Package:** `com.lazy.player` · **Android:** 8.0+ (API 26+).

Lazy Player is an original offline local-audio player. It takes *product functionality* inspiration from Poweramp and *aesthetic direction* from the glassy Mornye/SpotiFLAC UI. No source code, assets, or packages from the user-supplied Poweramp APK are included. This is **not** a Poweramp modification.

## In this first implementation

- Reads local audio through Android MediaStore (MP3, FLAC and other phone-decoder-supported formats).
- Audio permission by Android version, automatic refresh on resume, explicit library Refresh action.
- Songs, Albums, Artists, and persistent Liked songs; title/artist/album search.
- Full-screen glass-inspired player with artwork glow, seek, next/previous, shuffle, repeat, favourite, Up Next queue and compact mini-player.
- Background playback through AndroidX Media3 ExoPlayer + MediaSessionService, suitable for lock-screen and hardware headset controls.
- No Internet permission, account, analytics or advertising SDK.

## Design language

Inky plum-black background, album-art ambience, warm-white text, subtle lavender accent, softly outlined translucent surfaces. Glass is built from translucent lighting and a blurred copy of album artwork—no expensive per-frame refraction shader. Playback operates independently of Compose.

## Build without a local Android SDK

The project has a `.github/workflows/android.yml` workflow. See `docs/CLOUD_BUILD.md` for a no-install browser-only build path. In a GitHub repository, committing the project to `main` triggers a cloud build. The **lazy-player-debug-apk** artifact will appear under that Actions run **if compilation and tests succeed**. The workflow is present, but **no cloud build has been run here**. The ZIP itself is source, **not an installable APK**. It intentionally excludes any proprietary APK binary.

## Scope boundaries / next milestone

Not yet implemented: graphic/parametric EQ, ReplayGain, gapless/crossfade tuning, playlist creation/import/export, folder browsing, metadata editing, streaming or online download providers, file-format-specific decoding guarantees, Android Auto browse library, accessibility/device-performance certification.

Queue persistence across service process death and favourites across Android MediaStore database reindexing are also not guaranteed in v0.1.

## Architecture

- `data/MusicLibrary.kt` — local on-device MediaStore scan
- `data/AudioTrack.kt` — safe media item mapping
- `data/Favorites.kt` — preferences persistence
- `playback/PlaybackService.kt` — media session, focus, noisy-audio handling, background playback
- `MainActivity.kt` — permissions, controller bridge, UI-facing state
- `ui/Artwork.kt` — bounded/async embedded artwork decoding
- `ui/Glass.kt` — design surface tokens
- `ui/LazyPlayerScreen.kt` — library / search / mini-player / full player / queue

## Acceptance test matrix (to run on physical Android phone)

1. Grant/deny/revoke and regrant audio permission; leave and resume.
2. Open a library containing 0, 1, 1000+ songs and test filtering.
3. Play MP3 and FLAC locally; lock screen and change tracks with headset.
4. Leave the app and return; confirm playback continues.
5. Seek near the end, shuffle, repeat one, repeat all, and skip previous.
6. Favourite a track, kill and relaunch the app; check persistence.
7. Open queue, choose an earlier song; verify the right media ID is playing.
8. Test missing album art, huge cover art, missing tags, and Bluetooth disconnection.
9. Compare portrait screens and scrolling on 60Hz/90Hz/120Hz devices.

See `docs/QA.md` for checks actually performed versus still pending.
