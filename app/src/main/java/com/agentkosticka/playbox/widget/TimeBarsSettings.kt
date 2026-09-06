package com.agentkosticka.playbox.widget

import android.content.Context
import androidx.core.content.edit
import java.time.DayOfWeek
import kotlin.random.Random

enum class BarFill(val label: String) {
    LEFT_TO_RIGHT("Left to right"), RIGHT_TO_LEFT("Right to left"), DENSITY("Density")
}

data class TimeBarsSettings(
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val fill: BarFill = BarFill.LEFT_TO_RIGHT,
) {
    fun save(context: Context) {
        context.getSharedPreferences("time-bars", Context.MODE_PRIVATE).edit {
            putString("week-start", weekStart.name)
            putString("fill", fill.name)
        }
        TimeBarsWidget.requestImmediateUpdate(context)
    }

    companion object {
        fun load(context: Context): TimeBarsSettings {
            val preferences = context.getSharedPreferences("time-bars", Context.MODE_PRIVATE)
            return TimeBarsSettings(
                weekStart = runCatching { DayOfWeek.valueOf(preferences.getString("week-start", "MONDAY")!!) }.getOrDefault(DayOfWeek.MONDAY),
                fill = runCatching { BarFill.valueOf(preferences.getString("fill", "LEFT_TO_RIGHT")!!) }.getOrDefault(BarFill.LEFT_TO_RIGHT),
            )
        }
    }
}

/** A stable permutation: raising progress only adds dots; refreshes never reshuffle them. */
fun filledDots(count: Int, fraction: Double, fill: BarFill, barIndex: Int): BooleanArray {
    require(count > 0)
    val order = when (fill) {
        BarFill.LEFT_TO_RIGHT -> (0 until count).toList()
        BarFill.RIGHT_TO_LEFT -> (0 until count).reversed()
        BarFill.DENSITY -> (0 until count).shuffled(Random(0x71BA + barIndex))
    }
    val filled = (fraction.coerceIn(0.0, 1.0) * count).toInt()
    return BooleanArray(count).also { dots -> order.take(filled).forEach { dots[it] = true } }
}
