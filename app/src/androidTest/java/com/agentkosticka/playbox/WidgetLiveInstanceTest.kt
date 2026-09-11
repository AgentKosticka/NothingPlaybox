package com.agentkosticka.playbox

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.widget.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetLiveInstanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun twoBoundWidgetsOpenTheirOwnEditorAndSurviveRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = AppWidgetHost(context, 900001)
        val manager = AppWidgetManager.getInstance(context)
        val store = WidgetInstanceSettings(context)
        val ids = mutableListOf<Int>()
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
            repeat(2) {
                val id = host.allocateAppWidgetId()
                ids += id
                assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, BatteryGlyphWidget::class.java)))
            }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            val defaults = store.defaults()
            store.save(ids[0], defaults.copy(utility = defaults.utility.copy(batteryVisual = BatteryVisual.RING)))
            store.save(ids[1], defaults.copy(utility = defaults.utility.copy(batteryVisual = BatteryVisual.BAR)))
            TimeBarsWidget.updateAll(context)
            val firstIntent = widgetIntent(context, WidgetDestination.BATTERY_GLYPH, ids[0])
            ActivityScenario.launch<MainActivity>(firstIntent).use { scenario ->
                val instanceLabel = context.getString(R.string.widget_instance_explanation)
                compose.waitUntil(10_000) { compose.onAllNodesWithText(instanceLabel).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                compose.onNodeWithText(context.getString(R.string.battery_visual_ring)).assertIsSelected()
                compose.onRoot().performTouchInput { swipeUp(startY = height * .70f, endY = height * .25f) }
                compose.waitForIdle()
                java.io.File(context.getExternalFilesDir(null), "instance-editor-test.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                compose.onNodeWithText(context.getString(R.string.battery_visual_dots)).assertIsDisplayed().performClick()
                compose.waitForIdle()
                compose.onNodeWithText(context.getString(R.string.battery_visual_dots)).assertIsSelected()
                assertEquals(BatteryVisual.DOTS, store.load(ids[0]).utility.batteryVisual)
                assertEquals(BatteryVisual.BAR, store.load(ids[1]).utility.batteryVisual)
                scenario.recreate()
                compose.onNodeWithText(context.getString(R.string.battery_visual_dots)).assertIsSelected()
                scenario.onActivity { it.startActivity(widgetIntent(it, WidgetDestination.BATTERY_GLYPH, ids[1])) }
                compose.waitForIdle()
                compose.onNodeWithText(context.getString(R.string.battery_visual_bar)).assertIsSelected()
                // Keep ActivityScenario's launch identity for orderly lifecycle cleanup.
                scenario.onActivity { it.intent = firstIntent }
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            ids.forEach { host.deleteAppWidgetId(it); store.delete(it) }
            host.deleteHost()
            TimeBarsWidget.cancelIfUnused(context)
        }
    }
}
