package com.agentkosticka.playbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.calendar.*
import com.agentkosticka.playbox.widget.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.ZonedDateTime

class WidgetInstanceAndAgendaTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun snapshotsAreIndependentAndRestoreRemapsAndDeletionIsScoped() {
        val store = WidgetInstanceSettings(context)
        val ids = intArrayOf(900041, 900082, 900091, 900092)
        ids.forEach(store::delete)
        val initialDefaults = context.getSharedPreferences("widget-instances", 0).getString("defaults", null)
        val initial = store.load(ids[0])
        try {
            store.save(ids[0], initial.copy(utility = initial.utility.copy(batteryVisual = BatteryVisual.BAR), agenda = AgendaSettings(calendarIds = setOf(777))))
            store.save(ids[1], initial.copy(time = initial.time.copy(weekStart = java.time.DayOfWeek.SUNDAY), agenda = AgendaSettings(calendarIds = emptySet())))
            assertEquals(BatteryVisual.BAR, store.load(ids[0]).utility.batteryVisual)
            assertEquals(initial.utility, store.load(ids[1]).utility)
            assertEquals(emptySet<Long>(), store.load(ids[1]).agenda.calendarIds)
            assertEquals(initial.time, store.load(ids[0]).time)
            InstanceWidgetProvider().onRestored(context, ids.take(2).toIntArray(), ids.takeLast(2).toIntArray())
            assertEquals(BatteryVisual.BAR, store.load(ids[2]).utility.batteryVisual)
            assertEquals(emptySet<Long>(), store.load(ids[2]).agenda.calendarIds)
            assertEquals(java.time.DayOfWeek.SUNDAY, store.load(ids[3]).time.weekStart)
            InstanceWidgetProvider().onDeleted(context, intArrayOf(ids[2]))
            assertFalse(context.getSharedPreferences("widget-instances", Context.MODE_PRIVATE).contains("widget.${ids[2]}"))
            assertEquals(emptySet<Long>(), store.load(ids[3]).agenda.calendarIds)
        } finally {
            ids.forEach(store::delete)
            context.getSharedPreferences("widget-instances", 0).edit().apply {
                if (initialDefaults == null) remove("defaults") else putString("defaults", initialDefaults)
            }.apply()
        }
    }

    @Test fun editingDefaultsDoesNotMutateExistingSnapshot() {
        val store = WidgetInstanceSettings(context)
        val defaults = store.defaults()
        val id = 900044
        store.delete(id)
        val saved = store.load(id)
        try {
            store.save(null, defaults.copy(utility = defaults.utility.copy(batteryVisual = BatteryVisual.BAR)))
            assertEquals(saved, store.load(id))
        } finally { store.save(null, defaults); store.delete(id) }
    }

    @Test fun pendingIntentsSeparateInstancesAndRecurringOccurrences() {
        assertNotEquals(widgetPendingIntent(context, WidgetDestination.BATTERY_GLYPH, 41), widgetPendingIntent(context, WidgetDestination.BATTERY_GLYPH, 82))
        val first = EventOccurrence(7, 1, "Private title", 100, 200, false, null)
        val second = first.copy(beginMillis = 300, endMillis = 400)
        assertNotEquals(eventPendingIntent(context, 41, first), eventPendingIntent(context, 41, second))
        assertNotEquals(eventPendingIntent(context, 41, first), eventPendingIntent(context, 82, first))
        assertEquals(eventPendingIntent(context, 41, first), eventPendingIntent(context, 41, first))
    }

    @Test fun permissionMissingIsADataStateWithoutQueryingTheProvider() {
        val deniedContext = object : android.content.ContextWrapper(context) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int = android.content.pm.PackageManager.PERMISSION_DENIED
            override fun getContentResolver(): android.content.ContentResolver = error("Must not query without permission")
        }
        assertEquals(CalendarDataState.PermissionMissing, CalendarRepository(deniedContext).read(0, 1000, listOf(null)))
    }

    @Test fun devicePanelHasFourIndependentTapAreasAfterResize() {
        instrumentation.runOnMainSync {
            listOf(140 to 140, 300 to 140, 140 to 280).forEach { (width, height) ->
                val views = android.widget.RemoteViews(context.packageName, R.layout.widget_device_panel)
                configureDeviceActions(context, views, 41)
                val view = views.apply(context, FrameLayout(context))
                saveView(view, width, height, "device-actions-$width-$height")
                val battery = view.findViewById<View>(R.id.device_battery)
                val storage = view.findViewById<View>(R.id.device_storage)
                val alarm = view.findViewById<View>(R.id.device_alarm)
                val day = view.findViewById<View>(R.id.device_day)
                listOf(battery, storage, alarm, day).forEach {
                    assertTrue(it.hasOnClickListeners())
                    assertTrue(it.width > 0 && it.height > 0)
                }
                assertEquals(battery.right, storage.left)
                assertEquals(alarm.right, day.left)
                assertEquals(view.height, battery.height + alarm.height)
            }
        }
    }

    @Test fun agendaRemoteViewsInflateWithDatesAndDistinctEmptyStatesAtMultipleSizes() {
        val now = ZonedDateTime.parse("2026-09-11T09:00:00Z")
        val events = listOf("Physics", "Dentist", "Dinner").mapIndexed { index, title ->
            EventOccurrence(index.toLong(), 1, title, now.plusHours(index + 1L).toInstant().toEpochMilli(), now.plusHours(index + 2L).toInstant().toEpochMilli(), false, null)
        }
        val data = CalendarDataState.Available(listOf(DeviceCalendar(1, "Personal", null, true)), events)
        instrumentation.runOnMainSync {
            listOf(140 to 140, 300 to 180, 300 to 220).forEach { (width, height) ->
                val view = agendaViews(context, 41, AgendaSettings(), data, now, width, height).apply(context, FrameLayout(context))
                assertTrue(view.findViewById<TextView>(R.id.agenda_time_1).text.contains("11"))
                assertEquals("Physics", view.findViewById<TextView>(R.id.agenda_title_1).text.toString())
                saveView(view, width, height, "agenda-$width-$height")
            }
            val denied = agendaViews(context, 41, AgendaSettings(), CalendarDataState.PermissionMissing, now, 140, 140).apply(context, FrameLayout(context))
            assertEquals(context.getString(R.string.agenda_permission_off), denied.findViewById<TextView>(R.id.agenda_empty).text.toString())
            assertEquals(View.GONE, denied.findViewById<View>(R.id.agenda_row_1).visibility)
            val empty = agendaViews(context, 41, AgendaSettings(calendarIds = emptySet()), data, now, 140, 140).apply(context, FrameLayout(context))
            assertEquals(context.getString(R.string.agenda_select_calendars), empty.findViewById<TextView>(R.id.agenda_empty).text.toString())
            val error = agendaViews(context, 41, AgendaSettings(), CalendarDataState.Error, now, 140, 140).apply(context, FrameLayout(context))
            assertEquals(context.getString(R.string.agenda_error), error.findViewById<TextView>(R.id.agenda_empty).text.toString())
        }
    }

    private fun saveView(view: View, width: Int, height: Int, name: String) {
        val density = context.resources.displayMetrics.density
        val w = (width * density).toInt()
        val h = (height * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
