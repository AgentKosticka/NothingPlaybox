package com.agentkosticka.playbox

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.agentkosticka.playbox.calendar.*
import com.agentkosticka.playbox.ui.Muted
import com.agentkosticka.playbox.widget.AgendaSettings
import com.agentkosticka.playbox.widget.TimeBarsWidget
import com.agentkosticka.playbox.widget.agendaEventTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

@Composable
internal fun CalendarSettingsEditor(settings: AgendaSettings, isAgenda: Boolean, onChange: (AgendaSettings) -> Unit) {
    val untitled = stringResource(R.string.agenda_untitled)
    val context = LocalContext.current
    val repository = remember(context) { CalendarRepository(context) }
    val permissionPrefs = remember { context.getSharedPreferences("calendar-permission", 0) }
    var revision by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(repository.hasPermission()) }
    var requested by remember { mutableStateOf(permissionPrefs.getBoolean("requested", false)) }
    var state by remember { mutableStateOf<CalendarDataState?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        requested = true
        permissionPrefs.edit().putBoolean("requested", true).apply()
        revision++
        TimeBarsWidget.requestImmediateUpdate(context)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = repository.hasPermission()
                revision++
                TimeBarsWidget.requestImmediateUpdate(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(granted, revision, settings.calendarIds) {
        state = if (!granted) CalendarDataState.PermissionMissing else null
        if (granted) {
            state = withContext(Dispatchers.IO) {
                val now = ZonedDateTime.now()
                repository.read(now.minusDays(2).toInstant().toEpochMilli(), now.plusDays(9).toInstant().toEpochMilli(), listOf(settings.calendarIds))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.calendar_heading), style = MaterialTheme.typography.titleMedium)
        if (!isAgenda) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = settings.showMarkers, onCheckedChange = { onChange(settings.copy(showMarkers = it)) })
                Text(stringResource(R.string.calendar_markers))
            }
        }
        if (!granted) {
            Text(stringResource(R.string.calendar_explanation), color = Muted)
            if (requested) Text(stringResource(R.string.calendar_denied), color = Muted)
            val activity = context as? Activity
            val blocked = requested && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)
            Button(onClick = {
                if (blocked) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                else launcher.launch(Manifest.permission.READ_CALENDAR)
            }) { Text(stringResource(if (blocked) R.string.calendar_settings else R.string.calendar_enable)) }
        } else {
            when (val data = state) {
                null -> Text(stringResource(R.string.calendar_loading))
                CalendarDataState.Error -> Text(stringResource(R.string.agenda_error))
                CalendarDataState.PermissionMissing -> Text(stringResource(R.string.calendar_denied))
                is CalendarDataState.Available -> {
                    if (data.calendars.isEmpty()) Text(stringResource(R.string.calendar_empty), color = Muted)
                    val selected = selectedCalendarIds(settings.calendarIds, data.calendars)
                    if (settings.calendarIds == null) Text(stringResource(R.string.calendar_selected_default), color = Muted)
                    data.calendars.forEach { calendar ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = calendar.id in selected, onCheckedChange = { enabled ->
                                onChange(settings.copy(calendarIds = if (enabled) selected + calendar.id else selected - calendar.id))
                            })
                            Column(Modifier.weight(1f)) {
                                Text(calendar.name)
                                calendar.accountName?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    TextButton(onClick = { onChange(settings.copy(calendarIds = null)) }) { Text(stringResource(R.string.calendar_visible)) }
                    TextButton(onClick = { onChange(settings.copy(calendarIds = emptySet())) }) { Text(stringResource(R.string.calendar_select_none)) }
                    if (isAgenda) {
                        val now = ZonedDateTime.now()
                        val events = agendaEvents(data.events, selected, settings.showAllDay, now, settings.maxItems)
                            .filter { it.startDate(now.zone) < now.toLocalDate().plusDays(7) }
                        Card {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(R.string.agenda_name), color = MaterialTheme.colorScheme.primary)
                                if (events.isEmpty()) Text(stringResource(if (selected.isEmpty()) R.string.agenda_select_calendars else R.string.agenda_no_events))
                                events.forEach { event ->
                                    Column {
                                        Text(agendaEventTime(context, event, now), style = MaterialTheme.typography.labelSmall, color = Muted)
                                        Text(event.title.ifBlank { untitled })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { revision++; TimeBarsWidget.requestImmediateUpdate(context) }) { Text(stringResource(R.string.calendar_retry)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = settings.showAllDay, onCheckedChange = { onChange(settings.copy(showAllDay = it)) })
            Text(stringResource(R.string.calendar_all_day))
        }
        if (isAgenda) {
            Text(stringResource(R.string.calendar_max_items))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { count -> FilterChip(selected = settings.maxItems == count, onClick = { onChange(settings.copy(maxItems = count)) }, label = { Text(count.toString()) }) }
            }
            Text(stringResource(R.string.calendar_refresh_help), color = Muted)
        }
    }
}
