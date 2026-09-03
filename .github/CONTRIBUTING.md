# Contributing to Nothing Playbox

Nothing Playbox is intentionally focused: it is an offline studio for the Nothing Phone (4a) Pro Glyph Matrix.

## Before opening a pull request

- Keep the 13×13 / 137-LED Phone (4a) Pro matrix as the primary product surface.
- Do not add root, Shizuku, accounts, analytics, ads, or network-dependent app features.
- Prefer small, reviewable changes with tests for reusable logic and regressions.
- Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`.
- For hardware-facing changes, explain what was verified on a real Phone (4a) Pro.

## Design direction

Favor playful, fluid effects and low-friction creative tools. The editor should make experimentation cheap: immediate previews, reversible edits, sensible defaults, and clear controls beat adding more knobs.
