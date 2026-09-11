package com.agentkosticka.playbox.widget

import android.content.Context
import androidx.core.content.edit

enum class BatteryVisual(val label: String) {
    RING("Ring"), DOTS("Dots"), BAR("Bar")
}

enum class StorageDisplay(val label: String) {
    USED("Used"), FREE("Free")
}

enum class YearDisplay(val label: String) {
    ELAPSED("Elapsed"), REMAINING("Remaining")
}

enum class MilestoneTarget(val label: String) {
    WEEKEND("Weekend"), MONTH_END("Month end"), YEAR_END("Year end"), CUSTOM("Custom date")
}

data class UtilityWidgetSettings(
    val batteryVisual: BatteryVisual = BatteryVisual.RING,
    val storageDisplay: StorageDisplay = StorageDisplay.FREE,
    val yearDisplay: YearDisplay = YearDisplay.ELAPSED,
    val milestoneTarget: MilestoneTarget = MilestoneTarget.WEEKEND,
    val customLabel: String = "Milestone",
    val customDate: java.time.LocalDate = java.time.LocalDate.now(),
    val repeatYearly: Boolean = false,
) {
    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("battery-visual", batteryVisual.name)
            putString("storage-display", storageDisplay.name)
            putString("year-display", yearDisplay.name)
            putString("milestone-target", milestoneTarget.name)
            putString("custom-label", customLabel)
            putString("custom-date", customDate.toString())
            putBoolean("repeat-yearly", repeatYearly)
        }
        TimeBarsWidget.requestImmediateUpdate(context)
    }

    companion object {
        private const val PREFS = "utility-widgets"

        fun load(context: Context): UtilityWidgetSettings {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return UtilityWidgetSettings(
                batteryVisual = enumValue(preferences.getString("battery-visual", null), BatteryVisual.RING),
                storageDisplay = enumValue(preferences.getString("storage-display", null), StorageDisplay.FREE),
                yearDisplay = enumValue(preferences.getString("year-display", null), YearDisplay.ELAPSED),
                milestoneTarget = enumValue(preferences.getString("milestone-target", null), MilestoneTarget.WEEKEND),
                customLabel = preferences.getString("custom-label", "Milestone") ?: "Milestone",
                customDate = runCatching { java.time.LocalDate.parse(preferences.getString("custom-date", "")) }.getOrDefault(java.time.LocalDate.now()),
                repeatYearly = preferences.getBoolean("repeat-yearly", false),
            )
        }

        private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
            runCatching { value?.let { enumValueOf<T>(it) } ?: fallback }.getOrDefault(fallback)
    }
}
