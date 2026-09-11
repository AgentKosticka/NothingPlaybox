package com.agentkosticka.playbox.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as C
import android.provider.CalendarContract.Instances as I
import androidx.core.content.ContextCompat

/** Call on an IO/worker thread. No event content is persisted or logged. */
class CalendarRepository(private val context: Context) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun read(startMillis: Long, endMillis: Long, selections: List<Set<Long>?>): CalendarDataState {
        if (!hasPermission()) return CalendarDataState.PermissionMissing
        return try {
            val calendars = calendars()
            val ids = selections.flatMap { selectedCalendarIds(it, calendars) }.toSet()
            CalendarDataState.Available(calendars, if (ids.isEmpty()) emptyList() else occurrences(startMillis, endMillis, ids))
        } catch (_: SecurityException) {
            // Permission can disappear between the check and either query.
            CalendarDataState.PermissionMissing
        } catch (_: Exception) {
            CalendarDataState.Error
        }
    }

    private fun calendars(): List<DeviceCalendar> {
        val projection = arrayOf(C._ID, C.CALENDAR_DISPLAY_NAME, C.ACCOUNT_NAME, C.VISIBLE)
        return context.contentResolver.query(C.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(DeviceCalendar(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2), cursor.getInt(3) != 0))
            }
        } ?: error("Calendar provider unavailable")
    }

    private fun occurrences(start: Long, end: Long, ids: Set<Long>): List<EventOccurrence> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(I.EVENT_ID, I.CALENDAR_ID, I.TITLE, I.BEGIN, I.END, I.ALL_DAY, I.EVENT_LOCATION, I.STATUS, CalendarContract.Events.DELETED)
        val selection = "${I.CALENDAR_ID} IN (${ids.joinToString { "?" }})"
        return context.contentResolver.query(uri, projection, selection, ids.map { it.toString() }.toTypedArray(), "${I.BEGIN} ASC")?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(7) == CalendarContract.Events.STATUS_CANCELED || cursor.getInt(8) != 0) continue
                    val begin = cursor.getLong(3)
                    val finish = cursor.getLong(4)
                    if (finish <= begin || finish <= start || begin >= end) continue
                    add(EventOccurrence(cursor.getLong(0), cursor.getLong(1), cursor.getString(2).orEmpty(), begin, finish, cursor.getInt(5) != 0, cursor.getString(6)))
                }
            }
        } ?: error("Calendar provider unavailable")
    }
}
