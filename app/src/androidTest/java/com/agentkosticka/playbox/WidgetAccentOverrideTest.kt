package com.agentkosticka.playbox

import android.content.res.Configuration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.widget.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetAccentOverrideTest {
    @Test fun overridePersistsAndRestoresNativeAndBitmapAccents() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val settings = WidgetAppearanceSettings(base)
        val original = settings.classicRed
        try {
            for (night in listOf(false, true)) {
                val config = Configuration(base.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val context = base.createConfigurationContext(config)
                settings.classicRed = false
                val system = WidgetPalette.resolve(context)
                for (enabled in listOf(true, false)) {
                    settings.classicRed = enabled
                    assertEquals(enabled, WidgetAppearanceSettings(context).classicRed)
                    val expected = if (enabled) WidgetAppearanceSettings.NOTHING_RED else system.accent
                    assertEquals(system.copy(accent = expected), WidgetPalette.resolve(context))
                    instrumentation.runOnMainSync {
                        for ((layout, id) in listOf(
                            R.layout.widget_agenda_compact to R.id.agenda_header,
                            R.layout.widget_agenda_wide to R.id.agenda_header,
                            R.layout.widget_dual_clock to R.id.dual_clock_remote_date,
                            R.layout.widget_next_event to R.id.next_event_label,
                            R.layout.widget_playbox_shortcuts to R.id.shortcut_aod)) {
                            val view = widgetRemoteViews(context, layout).apply(context, FrameLayout(context))
                            assertEquals(expected, view.findViewById<TextView>(id).currentTextColor)
                        }
                        for ((layout, id) in listOf(
                            R.layout.widget_focus_timer to R.id.focus_start_pause,
                            R.layout.widget_goal_tracker to R.id.goal_plus,
                            R.layout.widget_habit_tracker to R.id.habit_tracker_toggle,
                            R.layout.widget_matrix_showcase to R.id.matrix_showcase_active,
                            R.layout.widget_tally_counter to R.id.tally_plus)) {
                            val button = widgetRemoteViews(context, layout).apply(context, FrameLayout(context))
                                .findViewById<TextView>(id)
                            val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
                            button.background.setBounds(0, 0, 100, 100)
                            button.background.draw(android.graphics.Canvas(bitmap))
                            assertEquals(expected, bitmap.getPixel(50, 50))
                            assertEquals(if (enabled) android.graphics.Color.WHITE else context.getColor(R.color.widget_on_accent), button.currentTextColor)
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            settings.classicRed = original
        }
    }
}
