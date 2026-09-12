package com.agentkosticka.playbox

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.widget.WidgetDestination
import com.agentkosticka.playbox.widget.WidgetPalette
import com.agentkosticka.playbox.widget.WidgetVisualStyle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only device probe: compare applied resources, wallpaper inputs and launcher options.
 * Run again after changing the picker; never assumes that a particular hue must be present.
 */
@RunWith(AndroidJUnit4::class)
class WidgetColorDetectionTest {
    @Test fun captureColorSources() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        fun hex(color: Int) = "#%08X".format(color)
        val report = buildString {
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
            appendLine("theme metadata (diagnostic only): " + runCatching {
                Settings.Secure.getString(base.contentResolver, "theme_customization_overlay_packages")
            }.getOrElse { "unavailable: ${it.javaClass.simpleName}" })
            for (night in listOf(false, true)) {
                val config = Configuration(base.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val context = base.createConfigurationContext(config)
                appendLine("\nnight=$night")
                for (family in listOf("accent1", "accent2", "accent3", "neutral1", "neutral2")) {
                    for (tone in listOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)) {
                        val name = "system_${family}_$tone"
                        val id = context.resources.getIdentifier(name, "color", "android")
                        appendLine("$name=${if (id == 0) "missing" else hex(context.getColor(id))}")
                    }
                }
                val scheme = if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                appendLine("Material3 primary=${hex(scheme.primary.toArgb())} primaryContainer=${hex(scheme.primaryContainer.toArgb())} tertiary=${hex(scheme.tertiary.toArgb())}")
                for (style in WidgetVisualStyle.entries) {
                    val palette = WidgetPalette.resolve(context, night, style)
                    appendLine("widget $style accent=${hex(palette.accent)} background=${hex(palette.background)} inactive=${hex(palette.inactive)}")
                    if (style == WidgetVisualStyle.DYNAMIC) {
                        assertEquals(context.getColor(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600), palette.accent)
                        assertEquals(context.getColor(if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50), palette.background)
                        org.junit.Assert.assertTrue("Readable foreground in both modes",
                            androidx.core.graphics.ColorUtils.calculateContrast(palette.foreground, palette.background) >= 4.5)
                    }
                }
            }
            val wallpaper = base.getSystemService(WallpaperManager::class.java)
            for ((name, flag) in listOf("home" to WallpaperManager.FLAG_SYSTEM, "lock" to WallpaperManager.FLAG_LOCK)) {
                appendLine("wallpaper $name=" + runCatching {
                    wallpaper.getWallpaperColors(flag)?.let {
                        listOfNotNull(it.primaryColor, it.secondaryColor, it.tertiaryColor).joinToString { color -> hex(color.toArgb()) }
                    } ?: "unavailable"
                }.getOrElse { "unavailable: ${it.javaClass.simpleName}" })
            }
            val manager = AppWidgetManager.getInstance(base)
            for (destination in WidgetDestination.entries) {
                for (id in manager.getAppWidgetIds(ComponentName(base, destination.provider))) {
                    val options = manager.getAppWidgetOptions(id)
                    appendLine("${destination.key} id=$id options=" + options.keySet().sorted().joinToString { key ->
                        @Suppress("DEPRECATION")
                        "$key=${options.get(key)}"
                    })
                }
            }
        }
        File(base.getExternalFilesDir(null), "widget-color-detection.txt").writeText(report)
        android.util.Log.i("WidgetColorDetection", report)
    }

    @Test fun refreshAllInstalledWidgets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (destination in WidgetDestination.entries) {
            org.junit.Assert.assertTrue(destination.key,
                com.agentkosticka.playbox.widget.InstanceWidgetProvider::class.java.isAssignableFrom(destination.provider))
        }
        com.agentkosticka.playbox.widget.InstanceWidgetProvider.refreshAll(context)
    }

    @Test fun nativeLayoutsAndBitmapDefaultsSharePalette() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        for (night in listOf(false, true)) {
            val config = Configuration(base.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val context = base.createConfigurationContext(config)
            val palette = WidgetPalette.resolve(context)
            assertEquals(palette, WidgetPalette.resolve(context, style = WidgetVisualStyle.DYNAMIC))
            assertEquals(context.getColor(R.color.widget_on_surface), palette.foreground)
            assertEquals(context.getColor(R.color.widget_inactive), palette.inactive)
            org.junit.Assert.assertTrue("Calendar selection labels remain readable",
                androidx.core.graphics.ColorUtils.calculateContrast(palette.onAccent, palette.accent) >= 4.5)
            org.junit.Assert.assertTrue("Primary controls remain readable",
                androidx.core.graphics.ColorUtils.calculateContrast(
                    context.getColor(R.color.widget_on_accent), context.getColor(R.color.widget_accent)) >= 4.5)
            instrumentation.runOnMainSync {
                for (layout in listOf(R.layout.widget_ndot_clock, R.layout.widget_dual_clock,
                    R.layout.widget_quick_tasks, R.layout.widget_habit_tracker,
                    R.layout.widget_playbox_shortcuts, R.layout.widget_focus_timer,
                    R.layout.widget_goal_tracker, R.layout.widget_matrix_showcase,
                    R.layout.widget_agenda_compact, R.layout.widget_agenda_wide,
                    R.layout.widget_next_event)) {
                    val view = android.widget.RemoteViews(context.packageName, layout)
                        .apply(context, android.widget.FrameLayout(context))
                    val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
                    val background = requireNotNull(view.background) { "Missing background: $layout" }
                    background.setBounds(0, 0, 100, 100)
                    background.draw(android.graphics.Canvas(bitmap))
                    assertEquals("Native surface ${context.resources.getResourceEntryName(layout)} night=$night",
                        palette.background, bitmap.getPixel(50, 50))
                    bitmap.recycle()
                }
            }
        }
        assertEquals(WidgetPalette.resolve(base), WidgetPalette.current())
    }
}
