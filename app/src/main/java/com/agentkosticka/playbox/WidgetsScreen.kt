package com.agentkosticka.playbox

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agentkosticka.playbox.ui.Muted
import com.agentkosticka.playbox.ui.NothingDotFont
import com.agentkosticka.playbox.widget.BarFill
import com.agentkosticka.playbox.widget.BatteryDotsWidget
import com.agentkosticka.playbox.widget.BatteryGlyphWidget
import com.agentkosticka.playbox.widget.BatteryVisual
import com.agentkosticka.playbox.widget.DashboardRenderer
import com.agentkosticka.playbox.widget.DayDialWidget
import com.agentkosticka.playbox.widget.DevicePanelWidget
import com.agentkosticka.playbox.widget.MilestoneTarget
import com.agentkosticka.playbox.widget.MilestoneWidget
import com.agentkosticka.playbox.widget.MonthMatrixWidget
import com.agentkosticka.playbox.widget.NDotClockWidget
import com.agentkosticka.playbox.widget.PlayboxShortcutsWidget
import com.agentkosticka.playbox.widget.NextAlarmWidget
import com.agentkosticka.playbox.widget.StorageDisplay
import com.agentkosticka.playbox.widget.StorageMatrixWidget
import com.agentkosticka.playbox.widget.TimeBarsRenderer
import com.agentkosticka.playbox.widget.TimeBarsSettings
import com.agentkosticka.playbox.widget.TimeBarsWidget
import com.agentkosticka.playbox.widget.UtilityWidgetSettings
import com.agentkosticka.playbox.widget.UtilityWidgetRenderer
import com.agentkosticka.playbox.widget.WeekStripWidget
import com.agentkosticka.playbox.widget.YearDisplay
import com.agentkosticka.playbox.widget.YearDotsWidget
import com.agentkosticka.playbox.widget.batteryInfo
import com.agentkosticka.playbox.widget.nextAlarm
import com.agentkosticka.playbox.widget.storageInfo
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

private data class WidgetSpec(
    val name: String,
    val size: String,
    val description: String,
    val provider: Class<*>,
    val previewAspect: Float,
)

@Composable
fun WidgetsScreen() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var status by remember { mutableStateOf<String?>(null) }
    var timeSettings by remember { mutableStateOf(TimeBarsSettings.load(context)) }
    var utilitySettings by remember { mutableStateOf(UtilityWidgetSettings.load(context)) }
    var weekMenu by remember { mutableStateOf(false) }
    var widgetType by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("Time Bars") }

    val specs = remember {
        listOf(
            WidgetSpec("Day Dial", "2 × 2", "Today as a 60-dot ring, with weekday, date and elapsed percentage.", DayDialWidget::class.java, 1f),
            WidgetSpec("Battery Dots", "2 × 2", "One dot per battery percent. Minimal, dense and immediately readable.", BatteryDotsWidget::class.java, 1f),
            WidgetSpec("Battery Glyph", "2 × 1 → 4 × 2", "Responsive battery meter with ring, dots or bar modes and charging ETA when Android can estimate it.", BatteryGlyphWidget::class.java, 2f),
            WidgetSpec("Next Alarm", "2 × 1 → 4 × 2", "Your next system alarm with day and time remaining. No calendar permission needed.", NextAlarmWidget::class.java, 2f),
            WidgetSpec("Storage Matrix", "2 × 2 → 4 × 2", "A 100-dot internal-storage meter that can show either free or used space.", StorageMatrixWidget::class.java, 1f),
            WidgetSpec("Month Matrix", "4 × 2", "Compact monthly calendar with today highlighted and configurable week start.", MonthMatrixWidget::class.java, 2f),
            WidgetSpec("Week Strip", "4 × 1 → 4 × 2", "Seven-day glance strip for the current week, with today picked out in Nothing red.", WeekStripWidget::class.java, 2.6f),
            WidgetSpec("Year Dots", "4 × 2", "Every valid day of the year in a 12-row matrix. Show elapsed or remaining days.", YearDotsWidget::class.java, 2f),
            WidgetSpec("Device Panel", "4 × 2", "Battery, free storage, next alarm and today progress in one dashboard.", DevicePanelWidget::class.java, 2f),
            WidgetSpec("Milestone", "2 × 2 → 4 × 2", "Countdown to the weekend, next month or next year with a dotted progress track.", MilestoneWidget::class.java, 1f),
            WidgetSpec("NDot Clock", "2 × 1 → 4 × 2", "Live TextClock using Nothing OS NDot57. Android updates it every minute without background polling.", NDotClockWidget::class.java, 2f),
            WidgetSpec("Playbox Shortcuts", "4 × 1", "Jump straight to the Matrix studio, widget gallery or Nothing OS Always-on Glyph Toy selector.", PlayboxShortcutsWidget::class.java, 3f),
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(60_000)
        }
    }
    LaunchedEffect(widgetType) { status = null }

    val battery = remember(now) { batteryInfo(context) }
    val storage = remember(now) { storageInfo() }
    val alarm = remember(now) { nextAlarm(context, now) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("Time Bars") + specs.map { it.name }).forEach { name ->
                FilterChip(selected = widgetType == name, onClick = { widgetType = name }, label = { Text(name) })
            }
        }

        if (widgetType == "Time Bars") {
            val preview = remember(now, timeSettings) { TimeBarsRenderer.render(now, timeSettings).asImageBitmap() }
            Image(preview, "Live preview of four dotted time progress bars", Modifier.fillMaxWidth().aspectRatio(2f))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TIME BARS / 4 × 2", fontFamily = NothingDotFont.family)
                    WeekStartSetting(locale, timeSettings.weekStart, weekMenu, { weekMenu = it }) { day ->
                        timeSettings = timeSettings.copy(weekStart = day)
                        timeSettings.save(context)
                    }
                    Text("FILL STYLE", fontFamily = NothingDotFont.family)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BarFill.entries.forEach { fill ->
                            FilterChip(selected = timeSettings.fill == fill, onClick = {
                                timeSettings = timeSettings.copy(fill = fill)
                                timeSettings.save(context)
                            }, label = { Text(fill.label) })
                        }
                    }
                    Text(
                        if (timeSettings.fill == BarFill.DENSITY) "Scattered dots build up in a stable pattern as each period passes."
                        else "Dots fill ${timeSettings.fill.label.lowercase(Locale.ROOT)} as each period passes.",
                        color = Muted,
                    )
                    Text("Refreshes about every 15 minutes. Android battery saving may delay bitmap widgets.", color = Muted)
                    AddWidgetButton(TimeBarsWidget::class.java, "Time Bars", onStatus = { status = it })
                    status?.let { Text(it, color = Muted) }
                }
            }
            return@Column
        }

        val spec = specs.first { it.name == widgetType }
        val preview = remember(now, widgetType, timeSettings, utilitySettings, battery, storage, alarm) {
            when (widgetType) {
                "Day Dial" -> DashboardRenderer.dayDial(now)
                "Battery Dots" -> DashboardRenderer.battery(battery.percent, battery.charging)
                "Battery Glyph" -> UtilityWidgetRenderer.batteryGlyph(battery, utilitySettings.batteryVisual, 720, 320)
                "Next Alarm" -> UtilityWidgetRenderer.nextAlarm(context, now, alarm, 720, 320)
                "Storage Matrix" -> UtilityWidgetRenderer.storage(storage, utilitySettings.storageDisplay, 360, 360)
                "Month Matrix" -> UtilityWidgetRenderer.month(now, timeSettings.weekStart, 720, 360)
                "Week Strip" -> UtilityWidgetRenderer.weekStrip(now, timeSettings.weekStart, 800, 300)
                "Year Dots" -> UtilityWidgetRenderer.year(now, utilitySettings.yearDisplay, 720, 360)
                "Device Panel" -> UtilityWidgetRenderer.devicePanel(context, now, battery, storage, alarm, 720, 360)
                "Milestone" -> UtilityWidgetRenderer.milestone(now, utilitySettings.milestoneTarget, 360, 360)
                "NDot Clock" -> UtilityWidgetRenderer.clockPreview(context, now, 720, 320)
                "Playbox Shortcuts" -> UtilityWidgetRenderer.shortcutsPreview(900, 300)
                else -> DashboardRenderer.dayDial(now)
            }.asImageBitmap()
        }
        Image(preview, "$widgetType preview", Modifier.fillMaxWidth().aspectRatio(spec.previewAspect))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${widgetType.uppercase(Locale.ROOT)} / ${spec.size}", fontFamily = NothingDotFont.family)
                Text(spec.description, color = Muted)

                when (widgetType) {
                    "Battery Glyph" -> {
                        Text("VISUAL", fontFamily = NothingDotFont.family)
                        SettingChips(BatteryVisual.entries.toList(), utilitySettings.batteryVisual, { it.label }) { value ->
                            utilitySettings = utilitySettings.copy(batteryVisual = value)
                            utilitySettings.save(context)
                        }
                    }
                    "Storage Matrix" -> {
                        Text("MEASURE", fontFamily = NothingDotFont.family)
                        SettingChips(StorageDisplay.entries.toList(), utilitySettings.storageDisplay, { it.label }) { value ->
                            utilitySettings = utilitySettings.copy(storageDisplay = value)
                            utilitySettings.save(context)
                        }
                    }
                    "Year Dots" -> {
                        Text("SHOW", fontFamily = NothingDotFont.family)
                        SettingChips(YearDisplay.entries.toList(), utilitySettings.yearDisplay, { it.label }) { value ->
                            utilitySettings = utilitySettings.copy(yearDisplay = value)
                            utilitySettings.save(context)
                        }
                    }
                    "Milestone" -> {
                        Text("COUNT DOWN TO", fontFamily = NothingDotFont.family)
                        SettingChips(MilestoneTarget.entries.toList(), utilitySettings.milestoneTarget, { it.label }) { value ->
                            utilitySettings = utilitySettings.copy(milestoneTarget = value)
                            utilitySettings.save(context)
                        }
                    }
                    "Month Matrix", "Week Strip" -> {
                        WeekStartSetting(locale, timeSettings.weekStart, weekMenu, { weekMenu = it }) { day ->
                            timeSettings = timeSettings.copy(weekStart = day)
                            timeSettings.save(context)
                        }
                    }
                }

                Text(
                    if (widgetType == "NDot Clock" || widgetType == "Playbox Shortcuts") "System-driven widget: no periodic worker needed."
                    else "Bitmap widgets refresh together about every 15 minutes and immediately on relevant clock, alarm or charging events.",
                    color = Muted,
                )
                AddWidgetButton(spec.provider, widgetType, onStatus = { status = it })
                status?.let { Text(it, color = Muted) }
            }
        }
    }
}

@Composable
private fun WeekStartSetting(
    locale: Locale,
    selected: DayOfWeek,
    expanded: Boolean,
    setExpanded: (Boolean) -> Unit,
    onSelected: (DayOfWeek) -> Unit,
) {
    Text("WEEK STARTS ON", fontFamily = NothingDotFont.family)
    Box {
        OutlinedButton(onClick = { setExpanded(true) }) {
            Text(selected.getDisplayName(TextStyle.FULL, locale))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            DayOfWeek.entries.forEach { day ->
                DropdownMenuItem(
                    text = { Text(day.getDisplayName(TextStyle.FULL, locale)) },
                    onClick = {
                        onSelected(day)
                        setExpanded(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> SettingChips(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value)) })
        }
    }
}

@Composable
private fun AddWidgetButton(provider: Class<*>, name: String, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    Button(
        onClick = {
            val manager = AppWidgetManager.getInstance(context)
            val fallback = "Long-press your home screen → Widgets → Nothing Playbox → $name."
            onStatus(
                if (manager.isRequestPinAppWidgetSupported) {
                    runCatching {
                        if (manager.requestPinAppWidget(ComponentName(context, provider), null, null)) {
                            "Finish adding the widget in your launcher."
                        } else fallback
                    }.getOrDefault(fallback)
                } else fallback,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("ADD TO HOME SCREEN") }
}
