package com.agentkosticka.playbox

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agentkosticka.playbox.data.AodPlayback
import com.agentkosticka.playbox.data.EffectRepository
import com.agentkosticka.playbox.matrix.GlyphConnectionState
import com.agentkosticka.playbox.matrix.GlyphMatrixClient
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.ui.MatrixDisplay
import com.agentkosticka.playbox.ui.Muted
import com.agentkosticka.playbox.ui.NothingDotFont
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AodScreen(repository: EffectRepository, glyphClient: GlyphMatrixClient) {
    val context = LocalContext.current
    val store = (context.applicationContext as PlayboxApplication).aodSettings
    val settings by store.settings.collectAsState()
    val effects by repository.effects.collectAsState()
    val selectedId by repository.activeId.collectAsState()
    val connection by glyphClient.state.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var pixels by remember { mutableStateOf(IntArray(PIXEL_COUNT)) }
    var message by remember { mutableStateOf<String?>(null) }
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    val selected = effects.firstOrNull { it.id == selectedId } ?: effects.firstOrNull()

    LaunchedEffect(settings, effects, selectedId, live, connection) {
        val renderer = AodPlayback(effects, selectedId, settings)
        val start = android.os.SystemClock.elapsedRealtime()
        while (true) {
            hour = LocalTime.now().hour
            val frame = renderer.frameAt(android.os.SystemClock.elapsedRealtime() - start, hour)
            pixels = frame.pixels
            if (live && connection == GlyphConnectionState.Ready) glyphClient.showFrame(frame.pixels)
            delay(frame.durationMs.toLong())
        }
    }
    DisposableEffect(Unit) { onDispose { glyphClient.stopDisplay() } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(104.dp).clip(CircleShape).background(Color.Black).padding(7.dp)) {
                        MatrixDisplay(pixels, Modifier.fillMaxSize())
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(selected?.name ?: stringResource(R.string.choose_effect), fontFamily = NothingDotFont.family)
                        Text(
                            when {
                                !settings.enabled -> stringResource(R.string.paused)
                                settings.isQuiet(hour) -> stringResource(R.string.quiet_hours_status)
                                settings.rotate && settings.rotationIds.isNotEmpty() -> stringResource(R.string.rotating_selection)
                                else -> stringResource(R.string.aod_preview)
                            },
                            color = Muted,
                        )
                        TextButton(onClick = { showPicker = true }) { Text(stringResource(R.string.choose_effect_action)) }
                    }
                }
                AodToggle(R.string.aod_playback, settings.enabled) { store.save(settings.copy(enabled = it)) }
                OutlinedButton(
                    onClick = {
                        live = !live
                        if (!live) glyphClient.stopDisplay()
                    },
                    enabled = glyphClient.isProbablySupported,
                ) { Text(stringResource(if (live) R.string.stop_matrix_preview else R.string.preview_on_matrix)) }
                if (live && connection is GlyphConnectionState.Connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (live && connection is GlyphConnectionState.Error) {
                    Text((connection as GlyphConnectionState.Error).message, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        glyphClient.openAodToyManager().onFailure {
                            message = context.getString(R.string.open_aod_settings_fallback)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.enable_in_nothing_settings)) }
                Text(stringResource(R.string.aod_settings_help), color = Muted)
                message?.let { Text(it, color = Muted) }
            }
        }
        Text(stringResource(R.string.output), fontFamily = NothingDotFont.family)
        Text(stringResource(R.string.brightness, (settings.brightness * 100).roundToInt()))
        Slider(settings.brightness, { store.save(settings.copy(brightness = it)) }, valueRange = .05f..1f)
        Text(stringResource(R.string.speed_multiplier, String.format(Locale.US, "%.2f", settings.speed)))
        Slider(settings.speed, { store.save(settings.copy(speed = it)) }, valueRange = .25f..2f)
        AodToggle(R.string.quiet_hours, settings.quietHours) { store.save(settings.copy(quietHours = it)) }
        if (settings.quietHours) {
            HourPicker(R.string.from, settings.quietStart) { store.save(settings.copy(quietStart = it)) }
            HourPicker(R.string.until, settings.quietEnd) { store.save(settings.copy(quietEnd = it)) }
            Text(
                stringResource(if (settings.quietStart == settings.quietEnd) R.string.equal_hours_mute else R.string.quiet_hours_help),
                color = Muted,
            )
        }
        AodToggle(R.string.rotate_effects, settings.rotate) { store.save(settings.copy(rotate = it)) }
        if (settings.rotate) {
            Text(stringResource(R.string.change_every_seconds, settings.rotationSeconds))
            Slider(
                settings.rotationSeconds.toFloat(),
                { store.save(settings.copy(rotationSeconds = it.roundToInt())) },
                valueRange = 10f..300f,
            )
            Text(stringResource(R.string.rotation_help), color = Muted)
            effects.forEach { effect ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(effect.id in settings.rotationIds, { checked ->
                        store.save(
                            settings.copy(
                                rotationIds = if (checked) settings.rotationIds + effect.id else settings.rotationIds - effect.id,
                            ),
                        )
                    })
                    Text(effect.name, Modifier.weight(1f))
                }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.aod_effect_title)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(effects.size) { i ->
                        TextButton(onClick = {
                            repository.setActiveEffect(effects[i].id)
                            showPicker = false
                        }) { Text(effects[i].name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun AodToggle(@StringRes labelRes: Int, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), Modifier.weight(1f))
        Switch(checked, change)
    }
}

@Composable
private fun HourPicker(@StringRes labelRes: Int, hour: Int, change: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(labelRes)
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(stringResource(R.string.hour_picker_format, label, hour.toString().padStart(2, '0')))
        }
        DropdownMenu(open, { open = false }) {
            repeat(24) { value ->
                DropdownMenuItem(
                    text = { Text("${value.toString().padStart(2, '0')}:00") },
                    onClick = {
                        change(value)
                        open = false
                    },
                )
            }
        }
    }
}
