# Nothing Playbox

Nothing Playbox is an offline Glyph Matrix studio for Nothing Phone (4a) Pro. It combines a 137-pixel intensity editor, multi-frame animation, image/video import, live procedural engines, a simulator, direct Matrix output, portable `.playbox` files, 13 home-screen widgets, and configurable Always-on Glyph playback.

The built-in library includes static/animated effects plus Radar Sweep, Breathing Orbit, Woven Light, Conway Life, Shifting Noise, Lava Lamp, Organic Bloom, Ripple Field, and Starfield. Procedural effects are evaluated live from compact saved settings instead of storing giant baked animation loops.

## App sections

- **Matrix** — static artwork, frame animations, Pixel Lab, and image/video imports.
- **Procedural** — Conway Life, Shifting Noise, Lava Lamp, Organic Bloom, Ripple Field, and Starfield. Open an engine to preview it or create independent named profiles. Profiles stay editable and work with `.playbox` import/export and AOD selection.
- **Widgets** — live previews, settings, and one-tap launcher pinning for all 13 widgets.
- **AOD** — select the active effect, preview on the Glyph Matrix, adjust brightness/speed, rotate a playlist, and configure quiet hours. Nothing OS's Always-on Glyph Toy reads the same settings.

Editor changes are kept as an in-memory draft until **Save**, including across Activity/configuration recreation. Opening an effect no longer mutates the library, and **Discard** leaves persisted data untouched.

## Home-screen widgets

Nothing Playbox currently ships:

1. **Time Bars** — day/week/month/year dotted progress bars with configurable week start and fill style.
2. **Day Dial** — today's progress as a 60-dot ring.
3. **Battery Dots** — one dot per battery percent.
4. **Battery Glyph** — responsive ring, dots, or bar battery meter with charging ETA when available.
5. **Next Alarm** — next system alarm plus time remaining.
6. **Storage Matrix** — 100-dot used/free internal-storage meter.
7. **Month Matrix** — compact monthly calendar with configurable week start.
8. **Week Strip** — current seven-day strip with today highlighted.
9. **Year Dots** — every day of the year, showing elapsed or remaining days.
10. **Device Panel** — battery, free storage, alarm, and today's progress in one dashboard.
11. **Milestone** — countdown to weekend, next month, or next year.
12. **NDot Clock** — system-driven live clock using NDot57 when Android actually exposes that family.
13. **Playbox Shortcuts** — quick entry points to Matrix, Widgets, and Nothing's AOD Toy selector.

Bitmap widgets share one periodic WorkManager refresh (about every 15 minutes). Setting changes and relevant system events request a coalesced background refresh instead of rendering the whole widget fleet in the UI callback. NDot Clock and shortcuts are system-driven and do not need periodic polling. Android may defer periodic work during Doze/battery saving.

All providers include launcher preview metadata. Tap an installed widget to open the Widgets section. Widgets do not require Glyph hardware.

## Build and verification

Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
```

Linux/macOS:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
```

Pull requests run unit tests, lint, debug + release builds, SDK/wrapper integrity checks, and the Android instrumentation suite on an emulator. CI artifacts/reports are retained for 30 days.

### Tagged releases

Pushing a `v*` tag triggers `.github/workflows/release.yml`. The workflow requires these repository secrets:

- `PLAYBOX_KEYSTORE_BASE64` — base64-encoded release keystore
- `PLAYBOX_KEYSTORE_PASSWORD`
- `PLAYBOX_KEY_ALIAS`
- `PLAYBOX_KEY_PASSWORD`

The workflow builds the optimized release with R8/resource optimization, verifies the APK signature with `apksigner`, then creates a GitHub Release with the signed APK. Signing material is never committed.

## Hardware setup

1. Install the app on Phone (4a) Pro and open it once.
2. Use **LIVE MATRIX** in Pixel Lab for direct preview.
3. Tap the lightbulb on an effect to select it for the system toy.
4. If the system screen does not open, go to **Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy** and select **Nothing Playbox**.

The hardware layer uses the documented `GlyphMatrixManager` APIs. APP/TOY ownership is reference-counted, preview output is released when the Activity leaves the foreground, and connection failures are retried instead of poisoning a held lease. Unsupported devices retain the editor and simulator.

## Storage and privacy

User effects are stored as bounded per-effect atomic files in app-private storage; older `effects-v1.json` libraries migrate automatically. Library size/count and imported archive decompression are bounded to avoid unbounded work/memory growth.

Imported images/videos are converted to 13×13 luminance frames and original media is not retained. Video import accepts at most 60 seconds/600 samples and keeps all decode paths bounded before conversion.

The app has no network permission, accounts, analytics, advertising, or app-managed cloud dependency. Android cloud backup/device-transfer export is explicitly disabled, so Playbox's effect library/settings remain local to the app installation unless you manually export `.playbox` files.

## Licensing

Nothing Playbox source code is licensed under the [MIT License](LICENSE).

`app/libs/glyph-matrix-sdk-2.0.aar` is the official Nothing Glyph Matrix SDK 2.0 from the [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) and is **not** relicensed under MIT. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

- SDK SHA-256: `BE00EE9CD7115F6B11984C6E31FE98E298FB726940D1555063610685EF3BBF29`
- The SDK remains subject to Nothing's applicable SDK terms/EULA.
