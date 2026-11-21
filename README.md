# InfinityCam

Camera app for **Project Infinity X (AOSP Android 17)** on Galaxy Note 10 5G
(SM-N971N, d1xks). The stock AOSP camera on InfinityX is too basic, so this
is a new CameraX app with OneUI 7 / Samsung Camera style modes — re-implemented
from scratch.

**This is NOT Samsung Camera.** Samsung Camera cannot be ported to AOSP: it
depends on the OneUI framework, Samsung camera HALs and Exynos vendor blobs.
InfinityCam imitates the experience with CameraX only.

## Modes (referencing EternityROM v5.5 Samsung Camera, OneUI 7)

- Foto, Video, Portrait (bokeh simulation — real radial-blur post-process),
  Night (longer exposure + brighten), Pro (manual ISO / shutter / WB / focus /
  EV where the device reports support), Panorama (wide 16:9 + guide, no
  auto-stitch), Food (saturation + warm vignette), Slow Motion, Hyperlapse
  (photo burst every 2s)
- Zoom 0.5x / 1x / 2x + pinch zoom (0.5x only if CameraX exposes zoom < 1x;
  aux lenses are locked on Exynos AOSP)
- Flash Auto/On/Off, timer 2s/5s/10s, aspect 4:3 / 16:9 / Full, HDR toggle
  (only if the device reports HDR scene mode), tap-to-focus
- Photos saved to Pictures/InfinityCam, videos to Movies/InfinityCam,
  gallery thumbnail preview (tap to open)

## Build

Local Gradle is blocked in this VM (daemon localhost TCP). Build via
GitHub Actions: Actions > Build Debug APK > Run workflow.

- Package: `com.raisproject.infinitycam`
- Version: 1.0.0, minSdk 21, targetSdk 35
- by RAIS.PROJECT
