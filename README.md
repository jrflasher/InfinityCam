# InfinityCam

Camera app for **Project Infinity X (AOSP Android 17)** on Galaxy Note 10 5G
(SM-N971N, d1xks). The stock AOSP camera on InfinityX is too basic, so this
is a new CameraX app — v1.0.1 with a **Google Pixel Camera style UI**,
re-implemented from scratch.

**This is NOT Samsung Camera and NOT Pixel Camera.** Samsung Camera cannot
be ported to AOSP (OneUI framework, Samsung camera HALs, Exynos vendor
blobs), and Pixel Camera is Google proprietary. InfinityCam imitates the
Pixel look and modes with CameraX only.

## UI (v1.0.1, Pixel style)

- Transparent top bar: Flash, Timer, Ratio, HDR, Settings (shows lens info)
- Mode selector like Pixel: Night Sight, Portrait, Photo, Video, Pro,
  Panorama, Food, Slow Motion, Timelapse — active mode white + bold,
  inactive modes muted grey
- Zoom chips `.5 / 1 / 2 / 5` above a large round white shutter button,
  gallery thumbnail bottom-left (round), front/back switch bottom-right

## Modes

- Photo, Video, Portrait (bokeh simulation — real radial-blur post-process),
  Night Sight (longer exposure + brighten), Pro (manual ISO / shutter / WB /
  focus / EV where the device reports support), Panorama (wide 16:9 + guide,
  no auto-stitch), Food (saturation + warm vignette), Slow Motion, Timelapse
  (photo burst every 2s)
- Flash Auto/On/Off, timer 2s/5s/10s, aspect 4:3 / 16:9 / Full, HDR toggle
  (only if the device reports HDR scene mode), tap-to-focus, pinch zoom
- Photos saved to Pictures/InfinityCam, videos to Movies/InfinityCam,
  gallery thumbnail preview (tap to open)

## Wide / Telephoto — v1.0.1 fix

On Exynos 9825 / InfinityX, CameraX only exposes the logical main lens.
The physical ultrawide (0.5x) and telephoto lenses are locked by Samsung
and are NOT exposed as separate Camera2 cameras. v1.0.0's 0.5x button did
nothing (min zoom is 1.0x) and 2x used an unclamped zoom call.

v1.0.1 enumerates cameras with CameraManager (logged), never binds a
physical camera ID, and drives wide/tele purely as zoom ratios on the main
lens, clamped to the device's ZoomState min/max:

- **Wide (0.5x):** if min zoom is 1x, the chip is greyed out / disabled and
  tapping it (if enabled) falls back to 1x with the toast
  "Lensa Wide tidak tersedia di InfinityX — pakai 1x (zoom digital)".
- **Tele (2x / 5x):** digital zoom on the main lens, clamped to the
  device's max zoom ratio, with a toast if the request exceeds it.
- Every bind and front/back switch is wrapped: a failure falls back to
  the main back lens instead of crashing or black-screening.

## Download

- **InfinityCam 1.0.1** (latest): https://github.com/jrflasher/InfinityCam/releases/tag/infinitycam-1.0.1
  - APK: `InfinityCam_1.0.1_debug.apk`, 4,687,589 bytes
  - SHA-256: `f94026218051c3a1181fdb194e36f16b1d85551d9e93c0074f93ad91ab81d9f3`
  - Direct link (once the asset is attached): https://github.com/jrflasher/InfinityCam/releases/download/infinitycam-1.0.1/InfinityCam_1.0.1_debug.apk
- InfinityCam 1.0.0: https://github.com/jrflasher/InfinityCam/releases/tag/infinitycam-1.0.0

### ⚠️ Certificate note — uninstall 1.0.0 once

v1.0.1 cert SHA-256: `178c8c06f55cc5a946dbe74f9f03278627ea29f0cd844e797944002894ed945d`
v1.0.0 cert SHA-256: `ca0077b581945db5789f695f9be418742281ed8772b6f1db1ad7d446ef40c532`

The certificates do **not** match — v1.0.0 was signed with an ephemeral
GitHub runner key that is unrecoverable. **Uninstall InfinityCam 1.0.0 once
before installing 1.0.1.** From 1.0.1 onward, debug builds use the fixed
debug keystore in `.github/debug.keystore` (explicit `signingConfig` in
`app/build.gradle`), so future updates install over each other without
uninstalling.

### Version details

- Package: `com.raisproject.infinitycam`
- Version: 1.0.1 (versionCode 2), minSdk 21, targetSdk 35, CameraX 1.4.2
- GitHub Actions run 45052953611 (Build Debug APK), commit `b519a70d`
- apksigner v1+v2 verified; file verification only — not tested installed
  on a real device or emulator.
- See [CHANGELOG.md](CHANGELOG.md) for the full version history.
- by RAIS.PROJECT
