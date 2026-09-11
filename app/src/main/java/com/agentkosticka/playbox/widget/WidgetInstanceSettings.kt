package com.agentkosticka.playbox.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/** Null selection means not configured; an empty set means explicitly select nothing. */
data class AgendaSettings(
    val calendarIds: Set<Long>? = null,
    val showAllDay: Boolean = true,
    val maxItems: Int = 3,
    val showMarkers: Boolean = false,
)

data class InstanceSettings(
    val utility: UtilityWidgetSettings,
    val time: TimeBarsSettings,
    val agenda: AgendaSettings,
)

/** One complete snapshot per instance: subsequent edits of defaults cannot leak into it. */
class WidgetInstanceSettings(private val context: Context) {
    private val preferences = context.getSharedPreferences("widget-instances", Context.MODE_PRIVATE)

    fun defaults(): InstanceSettings = InstanceSettings(
        UtilityWidgetSettings.load(context), TimeBarsSettings.load(context),
        decodeAgenda(JSONObject(preferences.getString("defaults", "{}") ?: "{}")),
    )

    fun load(id: Int?): InstanceSettings = if (id == null) defaults() else synchronized(LOCK) {
        val saved = preferences.getString("widget.$id", null)
        if (saved == null) defaults().also { put(id, it) }
        else runCatching { decode(JSONObject(saved)) }.getOrElse { defaults().also { put(id, it) } }
    }

    fun save(id: Int?, settings: InstanceSettings) = synchronized(LOCK) {
        if (id == null) {
            // Freeze legacy instances before changing their former global defaults.
            snapshotInstalled()
            settings.utility.save(context)
            settings.time.save(context)
            preferences.edit { putString("defaults", encodeAgenda(settings.agenda).toString()) }
        } else {
            put(id, settings)
        }
        TimeBarsWidget.requestImmediateUpdate(context)
    }

    fun snapshotInstalled() {
        val manager = AppWidgetManager.getInstance(context)
        WidgetDestination.entries.forEach { destination ->
            manager.getAppWidgetIds(ComponentName(context, destination.provider)).forEach { load(it) }
        }
    }

    fun delete(id: Int) = synchronized(LOCK) { preferences.edit { remove("widget.$id") } }

    fun restore(oldIds: IntArray, newIds: IntArray) = synchronized(LOCK) {
        // Read all sources first, since ID sets can overlap.
        val saved = oldIds.zip(newIds).map { (old, new) -> new to preferences.getString("widget.$old", null) }
        fun restoredValue(value: String): String = JSONObject(value).apply {
            // Calendar IDs belong to the old provider database. Require a fresh selection
            // after restore rather than accidentally showing a different account's events.
            put("calendars", org.json.JSONArray())
        }.toString()
        preferences.edit {
            putString("defaults", restoredValue(preferences.getString("defaults", "{}") ?: "{}"))
            oldIds.forEach { remove("widget.$it") }
            saved.forEach { (id, value) ->
                if (value != null) putString("widget.$id", restoredValue(value)) else remove("widget.$id")
            }
        }
    }

    fun stateHash(): Int = preferences.all.hashCode()

    private fun put(id: Int, value: InstanceSettings) {
        require(id != AppWidgetManager.INVALID_APPWIDGET_ID)
        preferences.edit { putString("widget.$id", encode(value).toString()) }
    }

    private fun encode(value: InstanceSettings) = encodeAgenda(value.agenda).apply {
        put("battery", value.utility.batteryVisual.name)
        put("storage", value.utility.storageDisplay.name)
        put("year", value.utility.yearDisplay.name)
        put("milestone", value.utility.milestoneTarget.name)
        put("customLabel", value.utility.customLabel)
        put("customDate", value.utility.customDate.toString())
        put("repeatYearly", value.utility.repeatYearly)
        put("weekStart", value.time.weekStart.name)
        put("fill", value.time.fill.name)
    }

    private fun decode(json: JSONObject) = InstanceSettings(
        UtilityWidgetSettings(
            batteryVisual = enumValue(json.optString("battery"), BatteryVisual.RING),
            storageDisplay = enumValue(json.optString("storage"), StorageDisplay.FREE),
            yearDisplay = enumValue(json.optString("year"), YearDisplay.ELAPSED),
            milestoneTarget = enumValue(json.optString("milestone"), MilestoneTarget.WEEKEND),
            customLabel = json.optString("customLabel", "Milestone"),
            customDate = runCatching { LocalDate.parse(json.optString("customDate")) }.getOrDefault(LocalDate.now()),
            repeatYearly = json.optBoolean("repeatYearly"),
        ),
        TimeBarsSettings(enumValue(json.optString("weekStart"), DayOfWeek.MONDAY), enumValue(json.optString("fill"), BarFill.LEFT_TO_RIGHT)),
        decodeAgenda(json),
    )

    private fun encodeAgenda(value: AgendaSettings) = JSONObject().apply {
        value.calendarIds?.let { put("calendars", org.json.JSONArray(it.sorted())) }
        put("allDay", value.showAllDay)
        put("maxItems", value.maxItems.coerceIn(1, 3))
        put("markers", value.showMarkers)
    }

    private fun decodeAgenda(json: JSONObject) = AgendaSettings(
        calendarIds = json.optJSONArray("calendars")?.let { array -> (0 until array.length()).map { array.getLong(it) }.toSet() },
        showAllDay = json.optBoolean("allDay", true),
        maxItems = json.optInt("maxItems", 3).coerceIn(1, 3),
        showMarkers = json.optBoolean("markers", false),
    )

    private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

    private companion object { val LOCK = Any() }
}

open class InstanceWidgetProvider : AppWidgetProvider() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetInstanceSettings(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetInstanceSettings(context).restore(oldWidgetIds, newWidgetIds)
    }
}
