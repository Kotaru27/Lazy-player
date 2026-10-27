# Lazy Player 0.5.3 — dock and animation audit

Baseline from screenshot: branch `redesign/opal-v052`, commit `d553c0809f9f5b7475bdf018a563761bb20fa2de`. Changes remain on `redesign/dock-motion-v053`.

## Review responsibilities (simulated perspectives, not separate agents)
- UI/UX: eliminate thick separator, reduce selected Albums slab, normalize dock controls and typography, keep one sharp navigation layer.
- Android: isolate 500ms position updates from Now Playing root, change seek progress drawing to a single canvas draw, replace per-pointer coroutine launches with graphics-layer gesture translation.
- QA: inspect animation callsites, run JVM and Compose UI tests including rapid navigation and playing/paused gestures, capture normal/135%/200% Android emulator screenshots.

## Inspected motion callsites and changes
1. **Dock selector**: previous `animateDpAsState` value was read in Modifier.offset; this now applies graphics-layer translation, using a critically damped spring. Rapid retargeting does not rebuild layouts.
2. **Mini-player seek progress**: previous animated fraction controlled a weighted child layout; now drawBehind paints a 1.5dp line inside the mini-player itself, with no separate horizontal dividing bar.
3. **Now Playing progress**: previously `positionState.value` was read in the Now Playing root; it now lives in PlayerTimeline only. This avoids invalidating the full artwork background and transport hierarchy every 500ms.
4. **Player open/close**: removed full-screen scale animation. Small slide + alpha transition keeps content stable, with zero-duration fallback for reduced motion.
5. **Artwork and swipe**: gestures used to schedule `Animatable.snapTo` coroutines on every pointer move. Displacement now changes directly on a graphics layer, and exactly one spring-reset coroutine runs on release. Existing horizontal intent, cancellation and Media3 previous/next semantics preserved.
6. **Album artwork**: cached 192px thumbnail and 640px full-screen variants remain unchanged. Dock no longer renders a second blurred album bitmap for every frame; a cached muted artwork tint still drives colour.
7. **Material3 bottom sheet, hover/ripple, seek thumb**: preserve native component animations and touch/accessibility behaviour; no custom conflicting animation layers added.
8. **Library view**: removing grouping from the filtered-track memoization key avoids reconstructing identical filtered lists when changing tabs. Album grouping memoization still occurs once per grouping selection.

## Limits and release gates
This is an optimized source change, **not a measured 60/90/120 Hz frame-time claim**. Hardware GPU trace, physical DAC and scroll jank measurement are separate gates. Frosted tint is not live backdrop refraction. Preserve `com.lazy.player.preview` and sign the v0.5.3 update (versionCode 4) with the same owner-controlled signing key as v0.5.1/0.5.2.
