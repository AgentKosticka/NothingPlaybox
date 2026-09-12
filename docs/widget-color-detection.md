# Nothing OS widget color probe

Verified on Nothing A069P, API 37, Nothing Launcher 5.0.1, September 12–13, 2026.

## Findings

The app can read the home-screen surface color through public framework resources.
Screenshot pixels from Nothing's weather widgets and folder backgrounds matched
`android.R.color.system_neutral1_900` exactly:

| Capture | System neutral1_900 | Nothing background | Time Bars background |
| --- | --- | --- | --- |
| Initial purple theme | #1C163C | #1C163C | #1B1B1B (classic) |
| Subsequent burgundy theme | #350E13 | #350E13 | #350E13 (updated dynamic) |

The screenshot after installation verifies the actual launcher-rendered bitmap,
not just a palette calculation. The color picker changed between captures; this
probe does not modify system theme settings.

## Detection methods compared

- Framework tonal resources: exact observed surface match; use context.getColor.
  Read neutrals as well as accents. Purple-theme accent1_500 was blue #5964FF,
  accent3_500 was pink #B65396, while neutral1_900 was the dark purple surface.
- Material 3 dynamic schemes: available and theme-aware, but semantic primary and
  surface roles need not match Nothing's chosen widget tone. Reading primary alone
  does not identify the launcher background.
- WallpaperManager: home primary remained #566786 and lock primary #8EEBA1 in both
  probes. These describe wallpaper inputs, not the applied palette selection.
- Secure theme customization JSON: readable on this device and useful for diagnosis.
  It changed from lock_wallpaper seed 2E3379 to home_wallpaper seed 955F56; neither
  seed is the resulting widget surface color. Do not use this private schema as
  the production color API.
- AppWidget options: installed Playbox instances supplied dimensions, category,
  sizes and sometimes display ID; no tint/palette was supplied in those bundles.
- Shell overlay lookup: agreed with app resource reads. Useful for debugging only;
  the app does not need shell access or privileged permissions.

## Implementation and scope

Dynamic WidgetPalette now reads all six roles from the applied system palette,
including the background and foreground. All 25 providers default to dynamic color
through the shared bitmap helper and native XML resources. Explicit user-selected
Classic/Glass/High Contrast modes remain available. The chosen light mapping
is a design choice, not a verified exact match to Nothing's light-mode widgets.

Both light and dark foreground/background pairs passed WCAG 4.5:1 checks on the
device, including native primary controls and filled calendar selection labels.
Three instrumentation tests and the unit suite passed. Native backgrounds were
verified for 11 layouts in both modes, and all 25 providers were checked against
the common base class. WidgetPaletteMonitor checks on process startup, activity
resume, configuration changes and optional secure-setting notifications, with
bounded retries for overlay application timing. Changed palettes refresh all
installed providers through InstanceWidgetProvider. Monitoring is process-bound;
when Android kills the process, checks resume on its next start. Existing periodic
updates remain in place. No continuous background polling is introduced.

## Repeat without uninstalling the app

Build with `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest`, then:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.agentkosticka.playbox.WidgetColorDetectionTest com.agentkosticka.playbox.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.agentkosticka.playbox/files/widget-color-detection.txt build/widget-color-detection.txt
```

`captureColorSources` records all public tonal families in light/dark contexts,
Material roles, widget roles, wallpaper colors and installed widget options.
`refreshAllInstalledWidgets` refreshes every installed provider.
`nativeLayoutsAndBitmapDefaultsSharePalette` checks XML/bitmap parity and contrast.
The tests do not change widget configuration or system theme settings.

Reference: https://developer.android.com/develop/ui/views/appwidgets/enhance
