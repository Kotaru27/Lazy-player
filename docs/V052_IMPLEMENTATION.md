# v0.5.2 opal redesign — source-level implementation record

Based on the user-provided `Lazy_Player_v051_Redesign_Plan(1).md`.

Baseline protected: `be7d41df6783c137909577c161319a5c9812722d`.
Work branch: `redesign/opal-v052`. No merge to main and no public GitHub Release.

## Implemented in source
- Neutral charcoal, softened glass strokes, lighter dock wash, one illuminated dock lens and removal of the redundant progress divider.
- Songs heading: All songs/Favourites menu; folder/search remain header actions. Favourites preserved in existing persistent store.
- Song-row actions moved from repeated hearts to an accessible overflow menu; full-player favourite heart retained.
- Two-column albums with responsive list at narrow width or high font scale. Artist rows report album and song counts.
- 70dp minimum song-row height, 50dp artwork, clearer status icon and 48dp overflow hit area.
- Missing-cover identity uses album/artist initials with restrained mark; cached artwork sizes retained.
- Now Playing artwork sizing uses available screen height/font scale instead of character count; backdrop layer fades to zero before boundary; more consistent type scale.
- Dock gestures clamp in dp-converted pixels rather than raw physical pixels.
- 120-track/long-title/Telugu Android test fixture added; extends baseline UI tests.

## Explicitly not certified
- This is still a **bounded artwork-frosted opal fallback**, not genuine sampled live backdrop glass.
- Shared artwork-geometry transition and adjacent cover-following swipe are not yet implemented. These require separate prototype/test gates.
- Custom bundled type family awaits licence/specimen/fallback validation.
- Audio engine, Media3, USB DAC detector, SAF scanning, signing identity untouched.
- No measured optimized frame times, physical FLAC/DAC certification, or approval to merge.

## Material prototype decision
The Kyant0 Apache-2.0 backdrop 2.0.1 artifact currently depends on Kotlin 2.4.10 and Compose 1.12; the app uses Kotlin 2.3/Compose BOM 2026.06.01. Do not silently force the newer dependency into the player. A minimal compatibility/raster benchmark is needed before calling it live liquid glass.

App ID remains `com.lazy.player.preview`; versionCode 3 / 0.5.2-preview. Reuse the original private signing certificate for all upgrade-track deliveries, never use raw CI debug-signed APK as update.
