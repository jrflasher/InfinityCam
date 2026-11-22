# InfinityCam — Changelog

## 1.0.1 (2026-11-23)

- **Pixel-style UI:** transparent top bar (Flash, Timer, Ratio, HDR, Settings),
  mode selector in Pixel order — Night Sight, Portrait, Photo, Video, Pro,
  Panorama, Food, Slow Motion, Timelapse — zoom chips `.5 / 1 / 2 / 5`,
  large round white shutter, round gallery thumbnail bottom-left, front/back
  switch bottom-right. No Samsung OneUI styling left.
- **Wide / Telephoto fix:** zoom is ratio-only on the logical main lens,
  clamped to ZoomState min/max. On InfinityX (Exynos 9825) the physical
  ultrawide (0.5x) and tele lenses are locked, so the 0.5x chip is disabled
  when aux lenses are unavailable and 2x/5x work as digital zoom. Bind
  failures fall back to the main lens at 1x — no crash / black screen.
- **Certificate change (important):** v1.0.1 is signed with the fixed repo
  debug keystore, cert SHA-256
  `178c8c06f55cc5a946dbe74f9f03278627ea29f0cd844e797944002894ed945d`.
  v1.0.0 used an ephemeral runner key, cert SHA-256
  `ca0077b581945db5789f695f9be418742281ed8772b6f1db1ad7d446ef40c532`, so
  **uninstall 1.0.0 once before installing 1.0.1**. From 1.0.1 onward,
  builds share the repo keystore cert and install over each other.
- Build: GitHub Actions run 45052953611, commit `b519a70d`.
- APK: `InfinityCam_1.0.1_debug.apk`, 4,687,589 bytes, SHA-256
  `f94026218051c3a1181fdb194e36f16b1d85551d9e93c0074f93ad91ab81d9f3`,
  apksigner v1+v2 verified.
- Release: https://github.com/jrflasher/InfinityCam/releases/tag/infinitycam-1.0.1

## 1.0.0 (2026-11-22)

- First release. CameraX camera app for Project Infinity X (AOSP Android 17)
  on Galaxy Note 10 5G (SM-N971N, d1xks), modes referencing EternityROM
  Samsung Camera: Photo, Video, Portrait, Night, Pro, Panorama, Food,
  Slow Motion, Hyperlapse; zoom 0.5x/1x/2x, flash/timer/aspect/HDR,
  tap-to-focus, Pro manual ISO/shutter/WB/focus, Portrait bokeh slider,
  Food/Night post-process.
- Known bugs fixed in 1.0.1: Wide (0.5x) button did nothing, 2x zoom call
  unclamped and could fail after a mode/front-back switch.
- APK: `InfinityCam_1.0.0_debug.apk`, 4,682,561 bytes, SHA-256
  `226145092d9ff070b7ca032d9ac685e5621b9f7a8b71b5c01cd493b9238f1832`.
- Release: https://github.com/jrflasher/InfinityCam/releases/tag/infinitycam-1.0.0

## Notes

- InfinityCam is **NOT Samsung Camera and NOT Pixel Camera**. Samsung Camera
  cannot be ported to AOSP (OneUI framework, Samsung camera HALs, Exynos
  vendor blobs), and Pixel Camera is Google proprietary. InfinityCam imitates
  the Pixel look and modes with CameraX only — no OneUI HAL / Galaxy AI /
  aux-lens access on AOSP.
- File verification only; not tested installed on a real device or emulator.
- by RAIS.PROJECT
