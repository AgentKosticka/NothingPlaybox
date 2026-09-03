# Nothing Playbox

Nothing Playbox is an offline Glyph Matrix studio for Nothing Phone (4a) Pro. It includes 16 built-in effects, a 137-pixel intensity editor, multi-frame animation, image and video import, a simulator, live Matrix output, portable `.playbox` files, and one dynamic Always-on Glyph Toy.

The showcase library includes Eye, Drinking Beer, Neon Vortex, Black Hole, Lightning Storm, Lava Lamp, Hyperspace, Wave Collider, Cyber Skull, Last Invader, Orbital Comet, Liquid Metal, Pulse, Scanner, Digital Rain, and Fireworks.

## Build

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
