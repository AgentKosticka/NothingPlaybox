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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import android.widget.RemoteViews
import android.view.View
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.agentkosticka.playbox.data.EffectRepository
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(widgetType: String, onWidgetType: (String) -> Unit, appWidgetId: Int? = null, onEditDefaults: () -> Unit = {}, repository: EffectRepository) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var status by remember { mutableStateOf<String?>(null) }
    val store = remember(context) { WidgetInstanceSettings(context) }
    val appearanceSettings = remember(context) { WidgetAppearanceSettings(context) }
    var classicRed by remember { mutableStateOf(appearanceSettings.classicRed) }
    val taskStore = remember(context) { QuickTasksStore(context) }
    val dualClockStore = remember(context) { DualClockStore(context) }
    val habitStore = remember(context) { HabitTrackerStore(context) }
    val tallyStore = remember(context) { TallyStore(context) }
    val noteStore = remember(context) { PinnedNoteStore(context) }
    val focusStore = remember(context) { FocusTimerStore(context) }
    val goalStore = remember(context) { GoalTrackerStore(context) }
    val validInstance = appWidgetId == null || AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider == ComponentName(context, WidgetDestination.fromKey(widgetType).provider)
    var instance by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(store.load(appWidgetId.takeIf { validInstance })) }
    var taskState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(taskStore.load(appWidgetId.takeIf { validInstance && widgetType == "quick-tasks" })) }
    var dualClockZone by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(dualClockStore.load(appWidgetId.takeIf { validInstance && widgetType == "dual-clock" })) }
    var habitState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(habitStore.load(appWidgetId.takeIf { validInstance && widgetType == "habit-tracker" }, now.toLocalDate())) }
    var tallyState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(tallyStore.load(appWidgetId.takeIf { validInstance && widgetType == "tally-counter" })) }
    var noteState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(noteStore.load(appWidgetId.takeIf { validInstance && widgetType == "pinned-note" })) }
    var focusState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(focusStore.load(appWidgetId.takeIf { validInstance && widgetType == "focus-timer" })) }
    var goalState by remember(widgetType, appWidgetId, validInstance) { mutableStateOf(goalStore.load(appWidgetId.takeIf { validInstance && widgetType == "goal-tracker" })) }
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
            WidgetSpec("matrix-showcase", R.string.matrix_showcase_name, R.string.widget_size_4x2, R.string.matrix_showcase_description, MatrixShowcaseWidget::class.java, 2f),
            WidgetSpec("agenda", R.string.agenda_name, R.string.widget_size_2x2_to_4x2, R.string.agenda_description, AgendaWidget::class.java, 1f),
            WidgetSpec("next-event", R.string.next_event_name, R.string.widget_size_2x1_to_4x2, R.string.next_event_description, NextEventWidget::class.java, 2f),
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
            WidgetSpec("focus-timer", R.string.focus_timer_name, R.string.widget_size_4x2, R.string.focus_timer_description, FocusTimerWidget::class.java, 2f),
            WidgetSpec("goal-tracker", R.string.goal_tracker_name, R.string.widget_size_4x2, R.string.goal_tracker_description, GoalTrackerWidget::class.java, 2f),
        )
    }

    LaunchedEffect(widgetType, appWidgetId, validInstance) {
        while (true) {
            now = ZonedDateTime.now()
            if (widgetType == "focus-timer") focusState = focusStore.load(appWidgetId.takeIf { validInstance })
            if (widgetType == "goal-tracker") goalState = goalStore.load(appWidgetId.takeIf { validInstance })
            delay(60_000)
        }
    }
    val categoryScroll = rememberScrollState()
    LaunchedEffect(category) { categoryScroll.scrollTo(if (category == WidgetCategory.PRODUCTIVITY) categoryScroll.maxValue else 0) }
    LaunchedEffect(widgetType) { status = null }
    val battery = remember(now) { batteryInfo(context) }
    val storage = remember(now) { storageInfo() }
    val alarm = remember(now) { nextAlarm(context, now) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(if (appWidgetId == null) R.string.widget_gallery_title else R.string.widget_editor_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(if (appWidgetId == null) R.string.widget_gallery_subtitle else R.string.widget_editor_subtitle),
                style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.widget_classic_red))
                Text(stringResource(R.string.widget_classic_red_description), color = Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = classicRed, onCheckedChange = {
                classicRed = it
                appearanceSettings.classicRed = it
            })
        }
        if (appWidgetId != null) {
            TextButton(onClick = onEditDefaults) { Text(stringResource(R.string.widget_edit_defaults)) }
            Text(stringResource(R.string.widget_instance_explanation), color = Muted)
            if (!validInstance) { Text(stringResource(R.string.widget_instance_missing)); return@Column }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(categoryScroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WidgetCategory.entries.forEach { item ->
                FilterChip(colors = widgetChipColors(), selected = category == item, onClick = { variantMenu = false; if (category != item) onWidgetType(WidgetDestination.entries.first { it.category == item }.key) }, label = { Text(stringResource(item.titleRes)) })
            }
        }
        Box {
            val selectedName = if (widgetType == "time-bars") R.string.time_bars_name else specs.first { it.key == widgetType }.nameRes
            OutlinedButton(onClick = { variantMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.widget_variant_selector, stringResource(selectedName))) }
            if (variantMenu) {
                ModalBottomSheet(onDismissRequest = { variantMenu = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(category.titleRes), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
                        WidgetDestination.entries.filter { it.category == category }.forEach { destination ->
                            val name = if (destination.key == "time-bars") R.string.time_bars_name else specs.first { it.key == destination.key }.nameRes
                            TextButton(onClick = { onWidgetType(destination.key); variantMenu = false }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                                    if (destination.key == widgetType) Text("✓")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (widgetType == "matrix-showcase") {
            val effects by repository.effects.collectAsState()
            val activeId by repository.activeId.collectAsState()
            val effect = effects.firstOrNull { it.id == activeId } ?: effects.firstOrNull()
            if (effect != null) {
                WidgetPreviewStage(stringResource(R.string.matrix_showcase_name), stringResource(R.string.widget_size_4x2)) {
                    NativeWidgetPreview(Modifier.fillMaxWidth().height(180.dp), stringResource(R.string.matrix_showcase_description)) { width, height ->
                        val palette = WidgetPalette.resolve(context, style = WidgetVisualStyle.DYNAMIC)
                        widgetRemoteViews(context, R.layout.widget_matrix_showcase).apply {
                            setWidgetSurface(context, R.id.matrix_showcase_root, WidgetVisualStyle.DYNAMIC)
                            setImageViewBitmap(R.id.matrix_showcase_image, MatrixShowcaseRenderer.render(effect, effect.id == activeId,
                                width, (height - 56 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1), palette.copy(background = android.graphics.Color.TRANSPARENT)))
                            setTextViewText(R.id.matrix_showcase_active, context.getString(if (effect.id == activeId) R.string.matrix_showcase_active else R.string.matrix_showcase_set_active))
                        }
                    }
                }
            }
            Text(stringResource(R.string.widget_matrix_gallery_help), color = Muted)
            if (appWidgetId == null) AddWidgetButton(MatrixShowcaseWidget::class.java, stringResource(R.string.matrix_showcase_name), onStatus = { status = it })
            status?.let { Text(it, color = Muted) }
            return@Column
        }

        if (widgetType == "agenda") {
            CalendarSettingsEditor(instance.agenda, true) { settings -> instance = instance.copy(agenda = settings); store.save(appWidgetId, instance) }
            if (appWidgetId == null) AddWidgetButton(AgendaWidget::class.java, stringResource(R.string.agenda_name), onStatus = { status = it })
            status?.let { Text(it, color = Muted) }; return@Column
        }
        if (widgetType == "next-event") {
            WidgetPreviewStage(stringResource(R.string.next_event_name), stringResource(R.string.widget_size_2x1_to_4x2), example = true) {
                NativeWidgetPreview(Modifier.fillMaxWidth().height(160.dp), stringResource(R.string.next_event_description)) { _, _ ->
                    widgetRemoteViews(context, R.layout.widget_next_event)
                }
            }
            if (appWidgetId == null) AddWidgetButton(NextEventWidget::class.java, stringResource(R.string.next_event_name), onStatus = { status = it })
            CalendarSettingsEditor(instance.agenda, true) { settings -> instance = instance.copy(agenda = settings); store.save(appWidgetId, instance); TimeBarsWidget.requestImmediateUpdate(context) }

            status?.let { Text(it, color = Muted) }; return@Column
        }
        if (widgetType == "time-bars") {
            val preview = remember(now, timeSettings, classicRed) { TimeBarsRenderer.render(now, timeSettings, 900, 360).asImageBitmap() }
            Image(preview, stringResource(R.string.time_bars_preview_cd), Modifier.fillMaxWidth().aspectRatio(2.5f))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.time_bars_header), style = MaterialTheme.typography.labelLarge)
                    WeekStartSetting(locale, timeSettings.weekStart, weekMenu, { weekMenu = it }) { day -> saveTime(timeSettings.copy(weekStart = day)) }
                    Text(stringResource(R.string.fill_style), style = MaterialTheme.typography.labelLarge)
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
        val focusPreviewLabel = context.getString(when (focusState.phase) {
            FocusPhase.FOCUS -> R.string.focus_phase_focus
            FocusPhase.BREAK -> R.string.focus_phase_break
            FocusPhase.PAUSED_FOCUS, FocusPhase.PAUSED_BREAK -> R.string.focus_phase_paused
            FocusPhase.IDLE -> R.string.focus_phase_ready
        })
        val focusPreviewSessions = context.resources.getQuantityString(R.plurals.focus_sessions, focusState.completedSessions, focusState.completedSessions)
        val goalPreviewLabel = context.getString(when (goalState.preset) {
            GoalPreset.WATER -> R.string.goal_water
            GoalPreset.READ -> R.string.goal_read
            GoalPreset.MOVE -> R.string.goal_move
            GoalPreset.CUSTOM -> R.string.goal_custom
        })
        val goalPreviewUnit = context.getString(when (goalState.preset) {
            GoalPreset.WATER -> R.string.goal_unit_glasses
            GoalPreset.READ, GoalPreset.MOVE -> R.string.goal_unit_minutes
            GoalPreset.CUSTOM -> R.string.goal_unit_units
        })
        val preview = if (widgetType in listOf("quick-tasks", "dual-clock", "habit-tracker", "tally-counter", "pinned-note", "focus-timer", "goal-tracker")) null else remember(now, classicRed, widgetType, timeSettings, utilitySettings, battery, storage, alarm, taskState, dualClockZone, habitState, tallyState, noteState, focusState, goalState, focusPreviewLabel, focusPreviewSessions, goalPreviewLabel, goalPreviewUnit) {
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
                "focus-timer" -> FocusTimerRenderer.render(focusState, focusState.remainingMillis(), focusPreviewLabel, focusPreviewSessions, 720, 300, WidgetPalette.resolve(context, style = focusState.style))
                "goal-tracker" -> GoalTrackerRenderer.render(goalState, goalPreviewLabel, goalPreviewUnit, 720, 300, WidgetPalette.resolve(context, style = goalState.style))
                else -> DashboardRenderer.dayDial(now)
            }.asImageBitmap()
        }
        WidgetPreviewStage(widgetName, stringResource(spec.sizeRes)) {
            val nativeLayout = when (widgetType) {
                "quick-tasks" -> R.layout.widget_quick_tasks
                "dual-clock" -> R.layout.widget_dual_clock
                "habit-tracker" -> R.layout.widget_habit_tracker
                "tally-counter" -> R.layout.widget_tally_counter
                "pinned-note" -> R.layout.widget_pinned_note
                "focus-timer" -> R.layout.widget_focus_timer
                "goal-tracker" -> R.layout.widget_goal_tracker
                else -> null
            }
            if (nativeLayout == null) {
                Image(requireNotNull(preview), stringResource(R.string.widget_preview_cd, widgetName), Modifier.fillMaxWidth(if (spec.previewAspect < 1f) .40f else 1f).aspectRatio(spec.previewAspect))
            } else {
                NativeWidgetPreview(Modifier.fillMaxWidth().height(if (widgetType == "habit-tracker" || widgetType == "quick-tasks") 200.dp else 180.dp),
                    stringResource(R.string.widget_preview_cd, widgetName)) { width, height ->
                    val views = widgetRemoteViews(context, nativeLayout)
                    val contentHeight = (height - 56 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    val palette = WidgetPalette.resolve(context)
                    when (widgetType) {
                        "quick-tasks" -> {
                            val tasks = taskState.normalized().tasks
                            val visible = tasks.count { it.text.isNotBlank() }
                            views.setTextViewText(R.id.quick_tasks_title, taskState.displayTitle)
                            views.setTextViewText(R.id.quick_tasks_progress, "${tasks.count { it.text.isNotBlank() && it.done }}/$visible")
                            views.setViewVisibility(R.id.quick_tasks_empty, if (visible == 0) View.VISIBLE else View.GONE)
                            listOf(R.id.quick_task_1, R.id.quick_task_2, R.id.quick_task_3, R.id.quick_task_4).forEachIndexed { index, id ->
                                views.setTextViewText(id, tasks[index].text)
                                views.setCompoundButtonChecked(id, tasks[index].done)
                                views.setViewVisibility(id, if (tasks[index].text.isBlank()) View.GONE else View.VISIBLE)
                            }
                        }
                        "dual-clock" -> {
                            views.setTextViewText(R.id.dual_clock_remote_label, DualClockZones.label(dualClockZone))
                            views.setString(R.id.dual_clock_remote_time, "setTimeZone", dualClockZone)
                            views.setString(R.id.dual_clock_remote_date, "setTimeZone", dualClockZone)
                        }
                        "habit-tracker" -> {
                            views.setTextViewText(R.id.habit_tracker_name, habitState.displayName)
                            views.setTextViewText(R.id.habit_tracker_streak, "${habitState.currentStreak(now.toLocalDate())} days")
                            views.setTextViewText(R.id.habit_tracker_meta, now.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", locale)))
                            views.setTextViewText(R.id.habit_tracker_toggle, context.getString(if (habitState.isDone(now.toLocalDate())) R.string.habit_tracker_done_today else R.string.habit_tracker_mark_today))
                            views.setImageViewBitmap(R.id.habit_tracker_grid, HabitTrackerRenderer.monthGrid(habitState, now.toLocalDate(), 720, 220, palette))
                        }
                        "tally-counter" -> views.setImageViewBitmap(R.id.tally_image, TallyRenderer.render(tallyState, width, contentHeight, palette))
                        "pinned-note" -> views.setImageViewBitmap(R.id.pinned_note_image, PinnedNoteRenderer.render(noteState, width, height, palette))
                        "focus-timer" -> {
                            views.setWidgetSurface(context, R.id.focus_root, focusState.style)
                            val colors = WidgetPalette.resolve(context, style = focusState.style)
                            views.setImageViewBitmap(R.id.focus_background, FocusTimerRenderer.render(focusState, focusState.remainingMillis(), focusPreviewLabel, focusPreviewSessions, width, contentHeight, colors.copy(background = android.graphics.Color.TRANSPARENT), false))
                            views.setTextViewText(R.id.focus_static_time, FocusTimerWidget.formatDuration(focusState.remainingMillis()))
                            views.setTextColor(R.id.focus_static_time, colors.foreground)
                            if (focusState.phase.running) {
                                views.setViewVisibility(R.id.focus_static_time, View.GONE)
                                views.setViewVisibility(R.id.focus_chronometer, View.VISIBLE)
                                views.setTextColor(R.id.focus_chronometer, colors.foreground)
                                views.setChronometer(R.id.focus_chronometer, android.os.SystemClock.elapsedRealtime() + focusState.remainingMillis(), null, true)
                                views.setChronometerCountDown(R.id.focus_chronometer, true)
                            }
                            views.setTextViewText(R.id.focus_start_pause, context.getString(if (focusState.phase.running) R.string.focus_pause else if (focusState.phase.paused) R.string.focus_resume else R.string.focus_start))
                            views.setViewVisibility(R.id.focus_skip, if (focusState.phase == FocusPhase.IDLE) View.INVISIBLE else View.VISIBLE)
                        }
                        "goal-tracker" -> {
                            views.setWidgetSurface(context, R.id.goal_root, goalState.style)
                            views.setImageViewBitmap(R.id.goal_background, GoalTrackerRenderer.render(goalState, goalPreviewLabel, goalPreviewUnit, width, contentHeight, WidgetPalette.resolve(context, style = goalState.style).copy(background = android.graphics.Color.TRANSPARENT)))
                        }
                    }
                    views
                }
            }
        }
        if (appWidgetId == null) AddWidgetButton(spec.provider, widgetName, onStatus = { status = it })
        status?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.widget_customize), style = MaterialTheme.typography.titleMedium)
                when (widgetType) {
                    "battery-glyph" -> { Text(stringResource(R.string.visual), style = MaterialTheme.typography.labelLarge); SettingChips(BatteryVisual.entries.toList(), utilitySettings.batteryVisual, ::batteryVisualLabel) { saveUtility(utilitySettings.copy(batteryVisual = it)) } }
                    "storage-matrix" -> { Text(stringResource(R.string.measure), style = MaterialTheme.typography.labelLarge); SettingChips(StorageDisplay.entries.toList(), utilitySettings.storageDisplay, ::storageDisplayLabel) { saveUtility(utilitySettings.copy(storageDisplay = it)) } }
                    "year-dots" -> { Text(stringResource(R.string.show), style = MaterialTheme.typography.labelLarge); SettingChips(YearDisplay.entries.toList(), utilitySettings.yearDisplay, ::yearDisplayLabel) { saveUtility(utilitySettings.copy(yearDisplay = it)) } }
                    "milestone" -> {
                        Text(stringResource(R.string.count_down_to), style = MaterialTheme.typography.labelLarge)
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
                        Text(stringResource(R.string.quick_tasks_list_title), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(value = taskState.title, onValueChange = { saveTasks(taskState.copy(title = it.take(24))) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.quick_tasks_tasks), style = MaterialTheme.typography.labelLarge)
                        taskState.normalized().tasks.forEachIndexed { index, task ->
                            OutlinedTextField(value = task.text, onValueChange = { value -> val tasks = taskState.normalized().tasks.toMutableList(); tasks[index] = task.copy(text = value.take(60), done = task.done && value.isNotBlank()); saveTasks(taskState.copy(tasks = tasks)) }, label = { Text(stringResource(R.string.quick_tasks_task_label, index + 1)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    "dual-clock" -> {
                        Text(stringResource(R.string.dual_clock_second_zone), style = MaterialTheme.typography.labelLarge)
                        Box { OutlinedButton(onClick = { zoneMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(DualClockZones.label(dualClockZone)) }; DropdownMenu(expanded = zoneMenu, onDismissRequest = { zoneMenu = false }) { DualClockZones.common.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { saveDualClock(option.id); zoneMenu = false }) } } }
                    }
                    "habit-tracker" -> {
                        Text(stringResource(R.string.habit_tracker_label), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(value = habitState.name, onValueChange = { saveHabit(habitState.copy(name = it.take(24))) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.visual), style = MaterialTheme.typography.labelLarge)
                        SettingChips(HabitGridStyle.entries.toList(), habitState.style, ::habitStyleLabel) { saveHabit(habitState.copy(style = it)) }
                        OutlinedButton(onClick = { val today = now.toLocalDate(); saveHabit(habitState.toggle(today, today)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (habitState.isDone(now.toLocalDate())) R.string.habit_tracker_done_today else R.string.habit_tracker_mark_today)) }
                        Text(stringResource(R.string.habit_tracker_local_help), color = Muted)
                    }
                    "tally-counter" -> {
                        Text(stringResource(R.string.tally_label), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(value = tallyState.label, onValueChange = { saveTally(tallyState.copy(label = it.take(24))) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.tally_step), style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1, 5, 10, 25, 100).forEach { step -> FilterChip(selected = tallyState.step == step, onClick = { saveTally(tallyState.copy(step = step)) }, label = { Text(step.toString()) }) } }
                        Text(stringResource(R.string.visual), style = MaterialTheme.typography.labelLarge)
                        SettingChips(TallyStyle.entries.toList(), tallyState.style, ::tallyStyleLabel) { saveTally(tallyState.copy(style = it)) }
                    }
                    "pinned-note" -> {
                        Text(stringResource(R.string.note_title), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(value = noteState.title, onValueChange = { saveNote(noteState.copy(title = it.take(28))) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.note_body), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(value = noteState.text, onValueChange = { saveNote(noteState.copy(text = it.take(500))) }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8)
                        Text(stringResource(R.string.visual), style = MaterialTheme.typography.labelLarge)
                        SettingChips(PinnedNoteStyle.entries.toList(), noteState.style, ::noteStyleLabel) { saveNote(noteState.copy(style = it)) }
                        Text(stringResource(R.string.local_content_help), color = Muted)
                    }
                    "focus-timer" -> {
                        Text(stringResource(R.string.widget_appearance), style = MaterialTheme.typography.labelLarge)
                        SettingChips(WidgetVisualStyle.entries.toList(), focusState.style, ::appearanceLabel) { style ->
                            focusStore.setAppearance(appWidgetId.takeIf { validInstance }, style)
                            focusState = focusStore.load(appWidgetId.takeIf { validInstance })
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${context.packageName}")))
                        }) { Text(stringResource(R.string.widget_precise_timing)) }
                        Text(stringResource(R.string.focus_timer_gallery_help), color = Muted)
                        Text(stringResource(R.string.focus_timer_precise_help), color = Muted)
                    }
                    "goal-tracker" -> {
                        fun configureGoal(preset: GoalPreset = goalState.preset, visual: GoalVisual = goalState.visual, style: WidgetVisualStyle = goalState.style) {
                            goalStore.configure(appWidgetId.takeIf { validInstance }, preset, visual, style)
                            goalState = goalStore.load(appWidgetId.takeIf { validInstance })
                        }
                        Text(stringResource(R.string.widget_goal_kind), style = MaterialTheme.typography.labelLarge)
                        SettingChips(GoalPreset.entries.toList(), goalState.preset, ::goalPresetLabel) { configureGoal(preset = it) }
                        Text(stringResource(R.string.visual), style = MaterialTheme.typography.labelLarge)
                        SettingChips(GoalVisual.entries.toList(), goalState.visual, { stringResource(when (it) { GoalVisual.RING -> R.string.battery_visual_ring; GoalVisual.BAR -> R.string.battery_visual_bar; GoalVisual.DOTS -> R.string.battery_visual_dots }) }) { configureGoal(visual = it) }
                        Text(stringResource(R.string.widget_appearance), style = MaterialTheme.typography.labelLarge)
                        SettingChips(WidgetVisualStyle.entries.toList(), goalState.style, ::appearanceLabel) { configureGoal(style = it) }
                        Text(stringResource(R.string.goal_tracker_gallery_help), color = Muted)
                        Text(stringResource(R.string.local_content_help), color = Muted)
                    }
                }
                if (widgetType in listOf("month-matrix", "week-strip", "week-column")) CalendarSettingsEditor(instance.agenda, false) { settings -> instance = instance.copy(agenda = settings); store.save(appWidgetId, instance) }
                Text(when (widgetType) { "ndot-clock", "playbox-shortcuts", "dual-clock", "pinned-note" -> stringResource(R.string.system_widget_help); "quick-tasks", "habit-tracker", "tally-counter", "focus-timer", "goal-tracker" -> stringResource(R.string.interactive_widget_help); else -> stringResource(R.string.shared_widget_refresh_help) }, color = Muted)

            }
        }
    }
}

@Composable private fun WeekStartSetting(locale: Locale, selected: DayOfWeek, expanded: Boolean, setExpanded: (Boolean) -> Unit, onSelected: (DayOfWeek) -> Unit) {
    Text(stringResource(R.string.week_starts_on), style = MaterialTheme.typography.labelLarge)
    Box { OutlinedButton(onClick = { setExpanded(true) }) { Text(selected.getDisplayName(TextStyle.FULL, locale)) }; DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) { DayOfWeek.entries.forEach { day -> DropdownMenuItem(text = { Text(day.getDisplayName(TextStyle.FULL, locale)) }, onClick = { onSelected(day); setExpanded(false) }) } } }
}

@Composable private fun <T> SettingChips(values: List<T>, selected: T, label: @Composable (T) -> String, onSelected: (T) -> Unit) { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { values.forEach { value -> FilterChip(colors = widgetChipColors(), selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value)) }) } } }
@Composable private fun barFillLabel(fill: BarFill): String = stringResource(when (fill) { BarFill.LEFT_TO_RIGHT -> R.string.fill_left_to_right; BarFill.RIGHT_TO_LEFT -> R.string.fill_right_to_left; BarFill.DENSITY -> R.string.fill_density })
@Composable private fun batteryVisualLabel(value: BatteryVisual): String = stringResource(when (value) { BatteryVisual.RING -> R.string.battery_visual_ring; BatteryVisual.DOTS -> R.string.battery_visual_dots; BatteryVisual.BAR -> R.string.battery_visual_bar })
@Composable private fun storageDisplayLabel(value: StorageDisplay): String = stringResource(when (value) { StorageDisplay.USED -> R.string.storage_used; StorageDisplay.FREE -> R.string.storage_free })
@Composable private fun yearDisplayLabel(value: YearDisplay): String = stringResource(when (value) { YearDisplay.ELAPSED -> R.string.year_elapsed; YearDisplay.REMAINING -> R.string.year_remaining })
@Composable private fun milestoneTargetLabel(value: MilestoneTarget): String = stringResource(when (value) { MilestoneTarget.CUSTOM -> R.string.milestone_custom; MilestoneTarget.WEEKEND -> R.string.milestone_weekend; MilestoneTarget.MONTH_END -> R.string.milestone_month_end; MilestoneTarget.YEAR_END -> R.string.milestone_year_end })
@Composable private fun habitStyleLabel(value: HabitGridStyle): String = stringResource(when (value) { HabitGridStyle.DOTS -> R.string.habit_style_dots; HabitGridStyle.SQUARES -> R.string.habit_style_squares; HabitGridStyle.RINGS -> R.string.habit_style_rings })
@Composable private fun tallyStyleLabel(value: TallyStyle): String = stringResource(if (value == TallyStyle.BIG_NUMBER) R.string.tally_style_big else R.string.tally_style_dots)
@Composable private fun noteStyleLabel(value: PinnedNoteStyle): String = stringResource(when (value) { PinnedNoteStyle.CARD -> R.string.note_style_card; PinnedNoteStyle.TERMINAL -> R.string.note_style_terminal; PinnedNoteStyle.MINIMAL -> R.string.note_style_minimal })

@Composable private fun AddWidgetButton(provider: Class<*>, name: String, onStatus: (String) -> Unit) {
    val context = LocalContext.current; val resources = LocalResources.current
    Button(onClick = { val manager = AppWidgetManager.getInstance(context); val fallback = resources.getString(R.string.pin_widget_fallback, name); onStatus(if (manager.isRequestPinAppWidgetSupported) runCatching { if (manager.requestPinAppWidget(ComponentName(context, provider), null, null)) resources.getString(R.string.pin_widget_finish) else fallback }.getOrDefault(fallback) else fallback) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_to_home_screen)) }
}

@Composable
private fun WidgetPreviewStage(name: String, size: String, example: Boolean = false, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text(size, style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) { content() }
            Text(stringResource(if (example) R.string.widget_example_preview else R.string.widget_preview_hint),
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable private fun appearanceLabel(style: WidgetVisualStyle): String = stringResource(when (style) {
    WidgetVisualStyle.CLASSIC -> R.string.widget_appearance_classic
    WidgetVisualStyle.DYNAMIC -> R.string.widget_appearance_dynamic
    WidgetVisualStyle.GLASS -> R.string.widget_appearance_glass
    WidgetVisualStyle.HIGH_CONTRAST -> R.string.widget_appearance_contrast
})

@Composable private fun goalPresetLabel(preset: GoalPreset): String = stringResource(when (preset) {
    GoalPreset.WATER -> R.string.goal_water
    GoalPreset.READ -> R.string.goal_read
    GoalPreset.MOVE -> R.string.goal_move
    GoalPreset.CUSTOM -> R.string.goal_custom
})

@Composable private fun widgetChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
    selectedLabelColor = MaterialTheme.colorScheme.surface,
)
