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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import com.agentkosticka.playbox.widget.*
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
fun WidgetsScreen(widgetType: String, onWidgetType: (String) -> Unit, appWidgetId: Int? = null, onEditDefaults: () -> Unit = {}) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var status by remember { mutableStateOf<String?>(null) }
    val store = remember(context) { WidgetInstanceSettings(context) }
    val taskStore = remember(context) { QuickTasksStore(context) }
    val dualClockStore = remember(context) { DualClockStore(context) }
    val habitStore = remember(context) { HabitTrackerStore(context) }
    val tallyStore = remember(context) { TallyStore(context) }
    val noteStore = remember(context) { PinnedNoteStore(context) }
    val validInstance = appWidgetId == null || AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider == ComponentName(context, WidgetDestination.fromKey(widgetType).provider)
    var instance by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(store.load(appWidgetId.takeIf { validInstance })) }
    var taskState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(taskStore.load(appWidgetId.takeIf { validInstance && widgetType == "quick-tasks" })) }
    var dualClockZone by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(dualClockStore.load(appWidgetId.takeIf { validInstance && widgetType == "dual-clock" })) }
    var habitState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(habitStore.load(appWidgetId.takeIf { validInstance && widgetType == "habit-tracker" }, now.toLocalDate())) }
    var tallyState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(tallyStore.load(appWidgetId.takeIf { validInstance && widgetType == "tally-counter" })) }
    var noteState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(noteStore.load(appWidgetId.takeIf { validInstance && widgetType == "pinned-note" })) }
    val timeSettings = instance.time
    val utilitySettings = instance.utility

    fun saveTime(value: TimeBarsSettings) { instance = instance.copy(time = value); store.save(appWidgetId, instance) }
    fun saveUtility(value: UtilityWidgetSettings) { instance = instance.copy(utility = value); store.save(appWidgetId, instance) }
    fun saveTasks(value: QuickTasksState) { taskState = value.normalized(); taskStore.save(appWidgetId.takeIf { widgetType == "quick-tasks" }, taskState) }
    fun saveDualClock(value: String) { dualClockZone = value; dualClockStore.save(appWidgetId.takeIf { widgetType == "dual-clock" }, value) }
    fun saveHabit(value: HabitTrackerState) { val today = now.toLocalDate(); habitState = value.normalized(today); habitStore.save(appWidgetId.takeIf { widgetType == "habit-tracker" }, habitState, today) }
    fun saveTally(value: TallyState) { tallyState = value.normalized(); tallyStore.save(appWidgetId.takeIf { widgetType == "tally-counter" }, tallyState) }
    fun saveNote(value: PinnedNoteState) { noteState = value.normalized(); noteStore.save(appWidgetId.takeIf { widgetType == "pinned-note" }, noteState) }

    var weekMenu by remember { mutableStateOf(false) }
    var variantMenu by remember { mutableStateOf(false) }
    var zoneMenu by remember { mutableStateOf(false) }
    val category = WidgetDestination.fromKey(widgetType).category

    val specs = remember {
        listOf(
            WidgetSpec("agenda", R.string.agenda_name, R.string.widget_size_2x2_to_4x2, R.string.agenda_description, AgendaWidget::class.java, 1f),
            WidgetSpec("battery-column", R.string.battery_column_name, R.string.widget_size_vertical, R.string.battery_column_description, BatteryColumnWidget::class.java, .5f),
            WidgetSpec("week-column", R.string.week_column_name, R.string.widget_size_vertical, R.string.week_column_description, WeekColumnWidget::class.java, .5f),
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
            WidgetSpec("dual-clock", R.string.dual_clock_name, R.string.widget_size_4x2, R.string.dual_clock_description, DualClockWidget::class.java, 2f),
            WidgetSpec("playbox-shortcuts", R.string.playbox_shortcuts_name, R.string.widget_size_4x1, R.string.playbox_shortcuts_description, PlayboxShortcutsWidget::class.java, 3f),
            WidgetSpec("quick-tasks", R.string.quick_tasks_name, R.string.widget_size_4x2, R.string.quick_tasks_description, QuickTasksWidget::class.java, 2f),
            WidgetSpec("habit-tracker", R.string.habit_tracker_name, R.string.widget_size_4x2, R.string.habit_tracker_description, HabitTrackerWidget::class.java, 2f),
            WidgetSpec("tally-counter", R.string.tally_counter_name, R.string.widget_size_2x2_to_4x2, R.string.tally_counter_description, TallyCounterWidget::class.java, 1.6f),
            WidgetSpec("pinned-note", R.string.pinned_note_name, R.string.widget_size_4x2, R.string.pinned_note_description, PinnedNoteWidget::class.java, 2f),
        )
    }

    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(60_000) } }
    LaunchedEffect(widgetType) { status = null }
    val battery = remember(now) { batteryInfo(context) }
    val storage = remember(now) { storageInfo() }
    val alarm = remember(now) { nextAlarm(context, now) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(if (appWidgetId == null) R.string.widget_defaults_explanation else R.string.widget_instance_explanation), color = Muted)
        if (appWidgetId != null) {
            TextButton(onClick = onEditDefaults) { Text(stringResource(R.string.widget_edit_defaults)) }
            if (!validInstance) { Text(stringResource(R.string.widget_instance_missing)); return@Column }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WidgetCategory.entries.forEach { item ->
                FilterChip(selected = category == item, onClick = { variantMenu = false; if (category != item) onWidgetType(WidgetDestination.entries.first { it.category == item }.key) }, label = { Text(stringResource(item.titleRes)) })
            }
        }
        Box {
            val selectedName = if (widgetType == "time-bars") R.string.time_bars_name else specs.first { it.key == widgetType }.nameRes
            OutlinedButton(onClick = { variantMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.widget_variant_selector, stringResource(selectedName))) }
            DropdownMenu(expanded = variantMenu, onDismissRequest = { variantMenu = false }) {
                WidgetDestination.entries.filter { it.category == category }.forEach { destination ->
                    val name = if (destination.key == "time-bars") R.string.time_bars_name else specs.first { it.key == destination.key }.nameRes
                    DropdownMenuItem(text = { Text(stringResource(name)) }, onClick = { onWidgetType(destination.key); variantMenu = false })
                }
            }
        }

        if (widgetType == "agenda") {
            CalendarSettingsEditor(instance.agenda, true) { settings -> instance = instance.copy(agenda = settings); store.save(appWidgetId, instance) }
            if (appWidgetId == null) AddWidgetButton(AgendaWidget::class.java, stringResource(R.string.agenda_name), onStatus = { status = it })
            status?.let { Text(it, color = Muted) }; return@Column
        }
        if (widgetType == "time-bars") {
            val preview = remember(now, timeSettings) { TimeBarsRenderer.render(now, timeSettings, 900, 360).asImageBitmap() }
            Image(preview, stringResource(R.string.time_bars_preview_cd), Modifier.fillMaxWidth().aspectRatio(2.5f))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.time_bars_header), fontFamily = NothingDotFont.family)
                    WeekStartSetting(locale, timeSettings.weekStart, weekMenu, { weekMenu = it }) { day -> saveTime(timeSettings.copy(weekStart = day)) }
                    Text(stringResource(R.string.fill_style), fontFamily = NothingDotFont.family)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BarFill.entries.forEach { fill -> FilterChip(selected = timeSettings.fill == fill, onClick = { saveTime(timeSettings.copy(fill = fill)) }, label = { Text(barFillLabel(fill)) }) }
                    }
                    Text(if (timeSettings.fill == BarFill.DENSITY) stringResource(R.string.density_fill_help) else stringResource(R.string.directional_fill_help, barFillLabel(timeSettings.fill).lowercase(locale)), color = Muted)
                    Text(stringResource(R.string.bitmap_refresh_help), color = Muted)
                    if (appWidgetId == null) AddWidgetButton(TimeBarsWidget::class.java, stringResource(R.string.time_bars_name), onStatus = { status = it })
                    status?.let { Text(it, color = Muted) }
                }
            }; return@Column
        }

        val spec = specs.first { it.key == widgetType }
        val widgetName = stringResource(spec.nameRes)
        val preview = remember(now, widgetType, timeSettings, utilitySettings, battery, storage, alarm, taskState, dualClockZone, habitState, tallyState, noteState) {
            when (widgetType) {
                "battery-column" -> UtilityWidgetRenderer.batteryColumn(battery, 360, 720)
                "week-column" -> UtilityWidgetRenderer.weekColumn(now, timeSettings.weekStart, 360, 720)
                "day-dial" -> DashboardRenderer.dayDial(now)
                "battery-dots" -> DashboardRenderer.battery(battery.percent, battery.charging)
                "battery-glyph" -> UtilityWidgetRenderer.batteryGlyph(battery, utilitySettings.batteryVisual, 720, 320)
                "next-alarm" -> UtilityWidgetRenderer.nextAlarm(context, now, alarm, 720, 320)
                "storage-matrix" -> UtilityWidgetRenderer.storage(storage, utilitySettings.storageDisplay, 360, 360)
                "month-matrix" -> UtilityWidgetRenderer.month(now, timeSettings.weekStart, 720, 360)
                "week-strip" -> UtilityWidgetRenderer.weekStrip(now, timeSettings.weekStart, 800, 300)
                "year-dots" -> UtilityWidgetRenderer.year(now, utilitySettings.yearDisplay, 720, 360)
                "device-panel" -> UtilityWidgetRenderer.devicePanel(context, now, battery, storage, alarm, 720, 360)
                "milestone" -> UtilityWidgetRenderer.milestone(now, utilitySettings.milestoneTarget, 360, 360, settings = utilitySettings)
                "ndot-clock" -> UtilityWidgetRenderer.clockPreview(context, now, 720, 320)
                "dual-clock" -> ProductivityWidgetRenderer.dualClock(context, now, dualClockZone, 720, 360)
                "playbox-shortcuts" -> UtilityWidgetRenderer.shortcutsPreview(900, 300)
                "quick-tasks" -> ProductivityWidgetRenderer.quickTasks(taskState, 720, 360)
                "habit-tracker" -> HabitTrackerRenderer.preview(habitState, now.toLocalDate(), 720, 360)
                "tally-counter" -> TallyRenderer.render(tallyState, 720, 360)
                "pinned-note" -> PinnedNoteRenderer.render(noteState, 720, 360)
                else -> DashboardRenderer.dayDial(now)
            }.asImageBitmap()
        }
        Image(preview, stringResource(R.string.widget_preview_cd, widgetName), Modifier.fillMaxWidth(if (spec.previewAspect < 1f) .35f else 1f).aspectRatio(spec.previewAspect))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.widget_header, widgetName.uppercase(locale), stringResource(spec.sizeRes)), fontFamily = NothingDotFont.family)
                Text(stringResource(spec.descriptionRes), color = Muted)
                when (widgetType) {
                    "battery-glyph" -> { Text(stringResource(R.string.visual), fontFamily = NothingDotFont.family); SettingChips(BatteryVisual.entries.toList(), utilitySettings.batteryVisual, ::batteryVisualLabel) { saveUtility(utilitySettings.copy(batteryVisual = it)) } }
                    "storage-matrix" -> { Text(stringResource(R.string.measure), fontFamily = NothingDotFont.family); SettingChips(StorageDisplay.entries.toList(), utilitySettings.storageDisplay, ::storageDisplayLabel) { saveUtility(utilitySettings.copy(storageDisplay = it)) } }
                    "year-dots" -> { Text(stringResource(R.string.show), fontFamily = NothingDotFont.family); SettingChips(YearDisplay.entries.toList(), utilitySettings.yearDisplay, ::yearDisplayLabel) { saveUtility(utilitySettings.copy(yearDisplay = it)) } }
                    "milestone" -> {
                        Text(stringResource(R.string.count_down_to), fontFamily = NothingDotFont.family)
                        SettingChips(MilestoneTarget.entries.toList(), utilitySettings.milestoneTarget, ::milestoneTargetLabel) { saveUtility(utilitySettings.copy(milestoneTarget = it)) }
                        if (utilitySettings.milestoneTarget == MilestoneTarget.CUSTOM) {
                            OutlinedTextField(value = utilitySettings.customLabel, onValueChange = { saveUtility(utilitySettings.copy(customLabel = it.take(40))) }, label = { Text(stringResource(R.string.milestone_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { val date = utilitySettings.customDate; android.app.DatePickerDialog(context, { _, year, month, day -> saveUtility(utilitySettings.copy(customDate = java.time.LocalDate.of(year, month + 1, day))) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) { Text(utilitySettings.customDate.toString()) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Switch(checked = utilitySettings.repeatYearly, onCheckedChange = { saveUtility(utilitySettings.copy(repeatYearly = it)) }); Text(stringResource(R.string.milestone_repeat)) }
                            Text(stringResource(R.string.milestone_repeat_help), color = Muted)
                        }
                    }
                    "month-matrix", "week-strip", "week-column" -> WeekStartSetting(locale, timeSettings.weekStart, weekMenu, { weekMenu = it }) { day -> saveTime(timeSettings.copy(weekStart = day)) }
                    "quick-tasks" -> {
                        Text(stringResource(R.string.quick_tasks_list_title), fontFamily = NothingDotFont.family)
                        OutlinedTextField(value = taskState.title, onValueChange = { saveTasks(taskState.copy(title = it.take(24))) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.quick_tasks_tasks), fontFamily = NothingDotFont.family)
                        taskState.normalized().tasks.forEachIndexed { index, task ->
                            OutlinedTextField(value = task.text, onValueChange = { value -> val tasks = taskState.normalized().tasks.toMutableList(); tasks[index] = task.copy(text = value.take(60), done = task.done && value.isNotBlank()); saveTasks(taskState.copy(tasks = tasks)) }, label = { Text(stringResource(R.string.quick_tasks_task_label, index + 1)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    "dual-clock" -> {
                        Text(stringResource(R.string.dual_clock_second_zone), fontFamily = NothingDotFont.family)
                        Box { OutlinedButton(onClick = { zoneMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(DualClockZones.label(dualClockZone)) }; DropdownMenu(expanded = zoneMenu, onDismissRequest = { zoneMenu = false }) { DualClockZones.common.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { saveDualClock(option.id); zoneMenu = false }) } } }
                    }
                    "habit-tracker" -> {
                        Text(stringResource(R.string.habit_tracker_label), fontFamily = NothingDotFont.family)
                        OutlinedTextField(value = habitState.name, onValueChange = { saveHabit(habitState.copy(name = it.take(24))) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { val today = now.toLocalDate(); saveHabit(habitState.toggle(today, today)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (habitState.isDone(now.toLocalDate())) R.string.habit_tracker_done_today else R.string.habit_tracker_mark_today)) }
                        Text(stringResource(R.string.habit_tracker_local_help), color = Muted)
                    }
                    "tally-counter" -> {
                        Text(stringResource(R.string.tally_label), fontFamily = NothingDotFont.family)
                        OutlinedTextField(value = tallyState.label, onValueChange = { saveTally(tallyState.copy(label = it.take(24))) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.tally_step), fontFamily = NothingDotFont.family)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1, 5, 10, 25, 100).forEach { step -> FilterChip(selected = tallyState.step == step, onClick = { saveTally(tallyState.copy(step = step)) }, label = { Text(step.toString()) }) } }
                        Text(stringResource(R.string.visual), fontFamily = NothingDotFont.family)
                        SettingChips(TallyStyle.entries.toList(), tallyState.style, ::tallyStyleLabel) { saveTally(tallyState.copy(style = it)) }
                    }
                    "pinned-note" -> {
                        Text(stringResource(R.string.note_title), fontFamily = NothingDotFont.family)
                        OutlinedTextField(value = noteState.title, onValueChange = { saveNote(noteState.copy(title = it.take(28))) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.note_body), fontFamily = NothingDotFont.family)
                        OutlinedTextField(value = noteState.text, onValueChange = { saveNote(noteState.copy(text = it.take(500))) }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8)
                        Text(stringResource(R.string.visual), fontFamily = NothingDotFont.family)
                        SettingChips(PinnedNoteStyle.entries.toList(), noteState.style, ::noteStyleLabel) { saveNote(noteState.copy(style = it)) }
                        Text(stringResource(R.string.local_content_help), color = Muted)
                    }
                }
                if (widgetType in listOf("month-matrix", "week-strip", "week-column")) CalendarSettingsEditor(instance.agenda, false) { settings -> instance = instance.copy(agenda = settings); store.save(appWidgetId, instance) }
                Text(when (widgetType) { "ndot-clock", "playbox-shortcuts", "dual-clock", "pinned-note" -> stringResource(R.string.system_widget_help); "quick-tasks", "habit-tracker", "tally-counter" -> stringResource(R.string.interactive_widget_help); else -> stringResource(R.string.shared_widget_refresh_help) }, color = Muted)
                if (appWidgetId == null) AddWidgetButton(spec.provider, widgetName, onStatus = { status = it })
                status?.let { Text(it, color = Muted) }
            }
        }
    }
}

@Composable private fun WeekStartSetting(locale: Locale, selected: DayOfWeek, expanded: Boolean, setExpanded: (Boolean) -> Unit, onSelected: (DayOfWeek) -> Unit) {
    Text(stringResource(R.string.week_starts_on), fontFamily = NothingDotFont.family)
    Box { OutlinedButton(onClick = { setExpanded(true) }) { Text(selected.getDisplayName(TextStyle.FULL, locale)) }; DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) { DayOfWeek.entries.forEach { day -> DropdownMenuItem(text = { Text(day.getDisplayName(TextStyle.FULL, locale)) }, onClick = { onSelected(day); setExpanded(false) }) } } }
}

@Composable private fun <T> SettingChips(values: List<T>, selected: T, label: @Composable (T) -> String, onSelected: (T) -> Unit) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value)) }) } } }
@Composable private fun barFillLabel(fill: BarFill): String = stringResource(when (fill) { BarFill.LEFT_TO_RIGHT -> R.string.fill_left_to_right; BarFill.RIGHT_TO_LEFT -> R.string.fill_right_to_left; BarFill.DENSITY -> R.string.fill_density })
@Composable private fun batteryVisualLabel(value: BatteryVisual): String = stringResource(when (value) { BatteryVisual.RING -> R.string.battery_visual_ring; BatteryVisual.DOTS -> R.string.battery_visual_dots; BatteryVisual.BAR -> R.string.battery_visual_bar })
@Composable private fun storageDisplayLabel(value: StorageDisplay): String = stringResource(when (value) { StorageDisplay.USED -> R.string.storage_used; StorageDisplay.FREE -> R.string.storage_free })
@Composable private fun yearDisplayLabel(value: YearDisplay): String = stringResource(when (value) { YearDisplay.ELAPSED -> R.string.year_elapsed; YearDisplay.REMAINING -> R.string.year_remaining })
@Composable private fun milestoneTargetLabel(value: MilestoneTarget): String = stringResource(when (value) { MilestoneTarget.CUSTOM -> R.string.milestone_custom; MilestoneTarget.WEEKEND -> R.string.milestone_weekend; MilestoneTarget.MONTH_END -> R.string.milestone_month_end; MilestoneTarget.YEAR_END -> R.string.milestone_year_end })
@Composable private fun tallyStyleLabel(value: TallyStyle): String = stringResource(if (value == TallyStyle.BIG_NUMBER) R.string.tally_style_big else R.string.tally_style_dots)
@Composable private fun noteStyleLabel(value: PinnedNoteStyle): String = stringResource(when (value) { PinnedNoteStyle.CARD -> R.string.note_style_card; PinnedNoteStyle.TERMINAL -> R.string.note_style_terminal; PinnedNoteStyle.MINIMAL -> R.string.note_style_minimal })

@Composable private fun AddWidgetButton(provider: Class<*>, name: String, onStatus: (String) -> Unit) {
    val context = LocalContext.current; val resources = LocalResources.current
    Button(onClick = { val manager = AppWidgetManager.getInstance(context); val fallback = resources.getString(R.string.pin_widget_fallback, name); onStatus(if (manager.isRequestPinAppWidgetSupported) runCatching { if (manager.requestPinAppWidget(ComponentName(context, provider), null, null)) resources.getString(R.string.pin_widget_finish) else fallback }.getOrDefault(fallback) else fallback) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_to_home_screen)) }
}
