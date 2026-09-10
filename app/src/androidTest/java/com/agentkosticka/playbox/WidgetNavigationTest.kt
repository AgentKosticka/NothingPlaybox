package com.agentkosticka.playbox

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.widget.WidgetDestination
import com.agentkosticka.playbox.widget.widgetIntent
import com.agentkosticka.playbox.widget.widgetPendingIntent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WidgetNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun awaitWidget(nameRes: Int, categoryRes: Int) {
        val label = context.getString(R.string.widget_variant_selector, context.getString(nameRes))
        compose.waitUntil(10_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithText(context.getString(categoryRes)).assertIsSelected()
    }

    @Test fun coldLaunchCategorySelectionAndRecreation() {
        ActivityScenario.launch<MainActivity>(widgetIntent(context, WidgetDestination.BATTERY_COLUMN)).use { scenario ->
            awaitWidget(R.string.battery_column_name, R.string.widget_category_battery)
            compose.onNodeWithText(context.getString(R.string.widget_category_calendar)).performClick()
            awaitWidget(R.string.month_matrix_name, R.string.widget_category_calendar)
            compose.onNodeWithText(context.getString(R.string.widget_variant_selector, context.getString(R.string.month_matrix_name))).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.week_column_name)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithText(context.getString(R.string.week_column_name)).performClick()
            awaitWidget(R.string.week_column_name, R.string.widget_category_calendar)
            scenario.recreate()
            awaitWidget(R.string.week_column_name, R.string.widget_category_calendar)
            compose.onNodeWithText(context.getString(R.string.section_matrix)).performClick()
            compose.onNodeWithText(context.getString(R.string.section_widgets)).performClick()
            awaitWidget(R.string.week_column_name, R.string.widget_category_calendar)
            val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
            File(context.getExternalFilesDir(null), "widget-categories-review.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun differentWidgetTapsAndRepeatedTapsRouteTheExistingActivity() {
        // Intent extras alone do not give PendingIntents distinct identities.
        val pending = WidgetDestination.entries.map { widgetPendingIntent(context, it) }
        assertEquals(WidgetDestination.entries.size, pending.toSet().size)
        ActivityScenario.launch<MainActivity>(widgetIntent(context, WidgetDestination.TIME_BARS)).use { scenario ->
            awaitWidget(R.string.time_bars_name, R.string.widget_category_time)
            scenario.onActivity { it.startActivity(widgetIntent(it, WidgetDestination.BATTERY_DOTS)) }
            awaitWidget(R.string.battery_dots_name, R.string.widget_category_battery)
            scenario.onActivity { it.startActivity(widgetIntent(it, WidgetDestination.WEEK_COLUMN)) }
            awaitWidget(R.string.week_column_name, R.string.widget_category_calendar)
            compose.onNodeWithText(context.getString(R.string.widget_category_time)).performClick()
            awaitWidget(R.string.time_bars_name, R.string.widget_category_time)
            scenario.onActivity { it.startActivity(widgetIntent(it, WidgetDestination.WEEK_COLUMN)) }
            awaitWidget(R.string.week_column_name, R.string.widget_category_calendar)
            // ActivityScenario tracks lifecycle by its launch Intent; onNewIntent updates the
            // Activity's data URI, so restore that identity before Scenario closes it.
            scenario.onActivity { it.intent = widgetIntent(it, WidgetDestination.TIME_BARS) }
        }
    }
}
