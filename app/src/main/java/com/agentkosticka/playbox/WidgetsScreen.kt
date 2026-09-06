package com.agentkosticka.playbox

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.annotation.StringRes
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
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
import com.agentkosticka.playbox.widget.NextAlarmWidget
import com.agentkosticka.playbox.widget.PlayboxShortcutsWidget
import com.agentkosticka.playbox.widget.StorageDisplay
import com.agentkosticka.playbox.widget.StorageMatrixWidget
import com.agentkosticka.playbox.widget.TimeBarsRenderer
import com.agentkosticka.playbox.widget.TimeBarsSettings
import com.agentkosticka.playbox.widget.TimeBarsWidget
import com.agentkosticka.playbox.widget.UtilityWidgetRenderer
import com.agentkosticka.playbox.widget.UtilityWidgetSettings
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
    val key: String,
    @StringRes val nameRes: Int,
    @StringRes val sizeRes: Int,
    @StringRes val descriptionRes: Int,
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
    var widgetType by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("time-bars") }

    val specs = remember {
        listOf(
            WidgetSpec("day-dial", R.string.day_dial_name, R.string.widget_size_2x2, R.string.day_dial_description, DayDialWidget::class.java, 1f),
            WidgetSpec("battery-dots", R.string.battery_dots_name, R.string.widget_size_2x2, R.string.battery_dots_description, BatteryDotsWidget::class.java, 1f),
            WidgetSpec("battery-glyph", R.string.battery_glyph_name, R.string.widget_size_2x1_to_4x2, R.string.battery_glyph_description, BatteryGlyphWidget::class.java, 2f),
            WidgetSpec("next-alarm", R.string.next_alarm_name, R.string.widget_size_2x1_to_4x2, R.string.next_alarm_description, NextAlarmWidget::class.java, 2f),
            WidgetSpec("storage-matrix", R.string.storage_matrix_name, R.string.widget_size_2x2_to_4x2, R.string.storage_matrix_description, StorageMatrixWidget::class.java, 1f),
            WidgetSpec("month-matrix", R.string.month_matrix_name, R.string.widget_size_4x2, R.string.month_matrix_description, MonthMatrixWidget::class.java, 2f),
            WidgetSpec("week-strip", R.string.week_strip_name, R.string.widget_size_4x1_to_4x2, R.string.week_strip_description, WeekStripWidget::class.java, 2.6f),
            WidgetSpec("year-dots", R.string.year_dots_name, R.string.widget_size_4x2, R.string.year_dots_description, YearDotsWidget::class.java, 2f),
            WidgetSpec("device-panel", R.string.device_panel_name, R.string.widget_size_4x2, R.string.device_panel_description, DevicePanelWidget::class.java, 2f),
            WidgetSpec("milestone", R.string.milestone_name, R.string.widget_size_2x2_to_4x2, R.string.milestone_description, MilestoneWidget::class.java, 1f),
            WidgetSpec("ndot-clock", R.string.ndot_clock_name, R.string.widget_size_2x1_to_4x2, R.string.ndot_clock_description, NDotClockWidget::class.java, 2f),
            WidgetSpec("playbox-shortcuts", R.string.playbox_shortcuts_name, R.string.widget_size_4x1, R.string.playbox_shortcuts_description, PlayboxShortcutsWidget::class.java, 3f),
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
            FilterChip(
                selected = widgetType == "time-bars",
                onClick = { widgetType = "time-bars" },
                label = { Text(stringResource(R.string.time_bars_name)) },
            )
            specs.forEach { spec ->
                FilterChip(
                    selected = widgetType == spec.key,
                    onClick = { widgetType = spec.key },
                    label = { Text(stringResource(spec.nameRes)) },
                )
            }
        }

        if (widgetType == "time-bars") {
            val preview = remember(now, timeSettings) { TimeBarsRenderer.render(now, timeSettings).asImageBitmap() }
            Image(
                preview,
                stringResource(R.string.time_bars_preview_cd),
                Modifier.fillMaxWidth().aspectRatio(2f),
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.time_bars_header), fontFamily = NothingDotFont.family)
                    WeekStartSetting(locale, timeSettings.weekStart, weekMenu, { weekMenu = it }) { day ->
                        timeSettings = timeSettings.copy(weekStart = day)
                        timeSettings.save(context)
                    }
                    Text(stringResource(R.string.fill_style), fontFamily = NothingDotFont.family)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BarFill.entries.forEach { fill ->
                            FilterChip(
                                selected = timeSettings.fill == fill,
                                onClick = {
                                    timeSettings = timeSettings.copy(fill = fill)
                                    timeSettings.save(context)
                                },
                                label = { Text(barFillLabel(fill)) },
                            )
                        }
                    }
                    Text(
                        if (timeSettings.fill == BarFill.DENSITY) {
                            stringResource(R.string.density_fill_help)
                        } else {
                            stringResource(R.string.directional_fill_help, barFillLabel(timeSettings.fill).lowercase(locale))
                        },
                        color = Muted,
                    )
                    Text(stringResource(R.string.bitmap_refresh_help), color = Muted)
                    AddWidgetButton(
                        TimeBarsWidget::class.java,
                        stringResource(R.string.time_bars_name),
                        onStatus = { status = it },
                    )
                    status?.let { Text(it, color = Muted) }
                }
            }
            return@Column
        }

        val spec = specs.first { it.key == widgetType }
        val widgetName = stringResource(spec.nameRes)
        val preview = remember(now, widgetType, timeSettings, utilitySettings, battery, storage, alarm) {
            when (widgetType) {
                "day-dial" -> DashboardRenderer.dayDial(now)
                "battery-dots" -> DashboardRenderer.battery(battery.percent, battery.charging)
                "battery-glyph" -> UtilityWidgetRenderer.batteryGlyph(battery, utilitySettings.batteryVisual, 720, 320)
                "next-alarm" -> UtilityWidgetRenderer.nextAlarm(context, now, alarm, 720, 320)
                "storage-matrix" -> UtilityWidgetRenderer.storage(storage, utilitySettings.storageDisplay, 360, 360)
                "month-matrix" -> UtilityWidgetRenderer.month(now, timeSettings.weekStart, 720, 360)
                "week-strip" -> UtilityWidgetRenderer.weekStrip(now, timeSettings.weekStart, 800, 300)
                "year-dots" -> UtilityWidgetRenderer.year(now, utilitySettings.yearDisplay, 720, 360)
                "device-panel" -> UtilityWidgetRenderer.devicePanel(context, now, battery, storage, alarm, 720, 360)
                "milestone" -> UtilityWidgetRenderer.milestone(now, utilitySettings.milestoneTarget, 360, 360)
                "ndot-clock" -> UtilityWidgetRenderer.clockPreview(context, now, 720, 320)
                "playbox-shortcuts" -> UtilityWidgetRenderer.shortcutsPreview(900, 300)
                else -> DashboardRenderer.dayDial(now)
            }.asImageBitmap()
        }
        Image(
            preview,
            stringResource(R.string.widget_preview_cd, widgetName),
            Modifier.fillMaxWidth().aspectRatio(spec.previewAspect),
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.widget_header,
                        widgetName.uppercase(locale),
                        stringResource(spec.sizeRes),
                    ),
                    fontFamily = NothingDotFont.family,
                )
                Text(stringResource(spec.descriptionRes), color = Muted)

                when (widgetType) {
                    "battery-glyph" -> {
                        Text(stringResource(R.string.visual), fontFamily = NothingDotFont.family)
                        SettingChips(BatteryVisual.entries.toList(), utilitySettings.batteryVisual, ::batteryVisualLabel) { value ->
                            utilitySettings = utilitySettings.copy(batteryVisual = value)
                            utilitySettings.save(context)
                        }
                    }
                    "storage-matrix" -> {
                        Text(stringResource(R.string.measure), fontFamily = NothingDotFont.family)
                        SettingChips(StorageDisplay.entries.toList(), utilitySettings.storageDisplay, ::storageDisplayLabel) { value ->
                            utilitySettings = utilitySettings.copy(storageDisplay = value)
                            utilitySettings.save(context)
                        }
                    }
                    "year-dots" -> {
                        Text(stringResource(R.string.show), fontFamily = NothingDotFont.family)
                        SettingChips(YearDisplay.entries.toList(), utilitySettings.yearDisplay, ::yearDisplayLabel) { value ->
                            utilitySettings = utilitySettings.copy(yearDisplay = value)
                            utilitySettings.save(context)
                        }
                    }
                    "milestone" -> {
                        Text(stringResource(R.string.count_down_to), fontFamily = NothingDotFont.family)
                        SettingChips(MilestoneTarget.entries.toList(), utilitySettings.milestoneTarget, ::milestoneTargetLabel) { value ->
                            utilitySettings = utilitySettings.copy(milestoneTarget = value)
                            utilitySettings.save(context)
                        }
                    }
                    "month-matrix", "week-strip" -> {
                        WeekStartSetting(locale, timeSettings.weekStart, weekMenu, { weekMenu = it }) { day ->
                            timeSettings = timeSettings.copy(weekStart = day)
                            timeSettings.save(context)
                        }
                    }
                }

                Text(
                    if (widgetType == "ndot-clock" || widgetType == "playbox-shortcuts") {
                        stringResource(R.string.system_widget_help)
                    } else {
                        stringResource(R.string.shared_widget_refresh_help)
                    },
                    color = Muted,
                )
                AddWidgetButton(spec.provider, widgetName, onStatus = { status = it })
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
    Text(stringResource(R.string.week_starts_on), fontFamily = NothingDotFont.family)
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
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = { Text(label(value)) },
            )
        }
    }
}

@Composable
private fun barFillLabel(fill: BarFill): String = stringResource(
    when (fill) {
        BarFill.LEFT_TO_RIGHT -> R.string.fill_left_to_right
        BarFill.RIGHT_TO_LEFT -> R.string.fill_right_to_left
        BarFill.DENSITY -> R.string.fill_density
    },
)

@Composable
private fun batteryVisualLabel(value: BatteryVisual): String = stringResource(
    when (value) {
        BatteryVisual.RING -> R.string.battery_visual_ring
        BatteryVisual.DOTS -> R.string.battery_visual_dots
        BatteryVisual.BAR -> R.string.battery_visual_bar
    },
)

@Composable
private fun storageDisplayLabel(value: StorageDisplay): String = stringResource(
    when (value) {
        StorageDisplay.USED -> R.string.storage_used
        StorageDisplay.FREE -> R.string.storage_free
    },
)

@Composable
private fun yearDisplayLabel(value: YearDisplay): String = stringResource(
    when (value) {
        YearDisplay.ELAPSED -> R.string.year_elapsed
        YearDisplay.REMAINING -> R.string.year_remaining
    },
)

@Composable
private fun milestoneTargetLabel(value: MilestoneTarget): String = stringResource(
    when (value) {
        MilestoneTarget.WEEKEND -> R.string.milestone_weekend
        MilestoneTarget.MONTH_END -> R.string.milestone_month_end
        MilestoneTarget.YEAR_END -> R.string.milestone_year_end
    },
)

@Composable
private fun AddWidgetButton(provider: Class<*>, name: String, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    Button(
        onClick = {
            val manager = AppWidgetManager.getInstance(context)
            val fallback = resources.getString(R.string.pin_widget_fallback, name)
            onStatus(
                if (manager.isRequestPinAppWidgetSupported) {
                    runCatching {
                        if (manager.requestPinAppWidget(ComponentName(context, provider), null, null)) {
                            resources.getString(R.string.pin_widget_finish)
                        } else fallback
                    }.getOrDefault(fallback)
                } else fallback,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.add_to_home_screen)) }
}
