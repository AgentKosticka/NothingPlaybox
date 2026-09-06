# Nothing Playbox

Nothing Playbox is an offline Glyph Matrix studio for Nothing Phone (4a) Pro. It includes 24 built-in effects and engines, a 137-pixel intensity editor, multi-frame animation, image and video import, a simulator, live Matrix output, portable `.playbox` files, and configurable Always-on Glyph playback.

The showcase library includes the original static and imported effect families plus Radar Sweep, Breathing Orbit, Woven Light, Conway Life, Shifting Noise, Lava Lamp, Organic Bloom, Ripple Field, and Starfield. Procedural effects are generated at runtime from persisted settings rather than stored animation loops.

## App sections

- **Matrix** — static artwork, frame animations, Pixel Lab, and image/video imports.
- **Procedural** — Conway Life, Shifting Noise, Lava Lamp, Organic Bloom, Ripple Field, and Starfield. Open an engine to play its built-in profile or create named profiles with independent settings. Saved profiles use the same procedural runtime, remain editable, and support `.playbox` import/export and AOD selection. Existing saved effects are grouped automatically; no migration is required.
- **Widgets** — Time Bars, Day Dial, and Battery Dots, each with a live preview and an **Add to home screen** button.
- **AOD** — choose the active effect, preview on the Glyph Matrix, adjust brightness and speed, rotate a selected playlist, and set quiet hours. The system Always-on Glyph Toy reads the same settings live.

Time Bars occupies four columns × two rows and shows weekday, week number, month, and year in four spacious rows with compact dotted bars. Each dot fills independently. Widget settings offer any day as the start of the week and left-to-right, right-to-left, or density fill. Density uses a stable scattered order, so progress adds dots without reshuffling; settings persist and immediately refresh all installed Time Bars widgets. All fractions are recalculated together by a unique WorkManager task every 15 minutes, including within-day progress for week/month/year. Android may defer work during Doze or battery saving. Time-zone and clock changes, reboot, and app replacement also refresh installed widgets. The last widget's removal cancels periodic work. No exact alarms or persistent foreground service are used.

On Nothing phones, widgets and selected app headings resolve the system NDot57 font directly. Other Android devices use a safe monospace fallback for headings and the built-in dotted renderer for widget labels.

Calculations use the device time zone, the selected week start (Monday by default), actual month/year lengths and daylight-saving-aware boundaries. Week numbering uses the four-day rule for week 1, matching ISO numbering when Monday is selected. Tap a widget to open the Widgets section. Widgets work without Glyph hardware.

## Build and verification

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Hardware setup

1. Install the app on Phone (4a) Pro and open it once.
2. Use **LIVE MATRIX** in Pixel Lab for direct preview.
3. Tap the lightbulb on an effect to select it for the system toy.
4. If the system screen does not open, go to **Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy** and select **Nothing Playbox**.

The hardware layer uses only the documented `GlyphMatrixManager` APIs. Unsupported devices retain the complete editor and on-screen simulator.

## Storage and privacy

Effects are saved atomically in the app's private storage. Imported images and videos are converted to 13×13 luminance frames; original media is not retained. Video import samples at 10 FPS, deduplicates identical frames, corrects rotation, and accepts up to the first 60 seconds/600 frames. The app has no network permission, accounts, analytics, advertising, or cloud dependency.

## Vendor SDK

`app/libs/glyph-matrix-sdk-2.0.aar` is the official Nothing Glyph Matrix SDK 2.0 downloaded from the [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

- SHA-256: `BE00EE9CD7115F6B11984C6E31FE98E298FB726940D1555063610685EF3BBF29`
- The SDK is covered by Nothing's EULA. Commercial use requires prior written permission from Nothing.
