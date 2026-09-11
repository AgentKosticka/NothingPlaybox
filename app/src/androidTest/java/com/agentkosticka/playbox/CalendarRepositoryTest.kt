package com.agentkosticka.playbox

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.calendar.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Temporary local fixtures only. Production manifest remains read-only. */
class CalendarRepositoryTest {
    @Test fun realProviderExpandsRecurrencesAndPreservesAllDayAndBlankTitles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val automation = instrumentation.uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        var calendarId: Long? = null
        try {
            val calendars = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "playbox-test")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
            val calendar = resolver.insert(calendars, ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, "playbox-test")
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "playbox-test")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Playbox test calendar")
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, "playbox-test")
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
            })!!
            val id = ContentUris.parseId(calendar)
            calendarId = id
            val start = Instant.parse("2026-09-11T10:00:00Z").toEpochMilli()
            val recurring = resolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, id)
                put(CalendarContract.Events.TITLE, "Fixture recurring")
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DURATION, "PT1H")
                put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=3")
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            })!!
            val allDay = resolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, id)
                put(CalendarContract.Events.TITLE, "")
                put(CalendarContract.Events.DTSTART, Instant.parse("2026-09-11T00:00:00Z").toEpochMilli())
                put(CalendarContract.Events.DTEND, Instant.parse("2026-09-12T00:00:00Z").toEpochMilli())
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            })!!
            val repository = CalendarRepository(context)
            val state = repository.read(start - 86_400_000, start + 4 * 86_400_000, listOf(setOf(id)))
            assertTrue("Expected available calendar data, got ${state::class.simpleName}", state is CalendarDataState.Available)
            val data = state as CalendarDataState.Available
            val occurrences = data.events.filter { it.eventId == ContentUris.parseId(recurring) }
            assertEquals(3, occurrences.size)
            assertEquals(listOf(start, start + 86_400_000, start + 2 * 86_400_000), occurrences.map { it.beginMillis })
            assertTrue(occurrences.all { it.endMillis - it.beginMillis == 3_600_000L })
            val day = data.events.single { it.eventId == ContentUris.parseId(allDay) }
            assertTrue(day.allDay)
            assertEquals("", day.title)
            assertEquals(id, day.calendarId)
            val none = repository.read(start, start + 86_400_000, listOf(emptySet())) as CalendarDataState.Available
            assertTrue(none.events.isEmpty())
        } finally {
            calendarId?.let { id ->
                resolver.delete(CalendarContract.Calendars.CONTENT_URI.buildUpon().appendPath(id.toString())
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "playbox-test")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build(), null, null)
            }
            automation.dropShellPermissionIdentity()
        }
    }
}
