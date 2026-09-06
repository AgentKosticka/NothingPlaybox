package com.agentkosticka.playbox

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agentkosticka.playbox.data.AodPlayback
import com.agentkosticka.playbox.data.EffectRepository
import com.agentkosticka.playbox.matrix.GlyphMatrixClient
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.ui.MatrixDisplay
import com.agentkosticka.playbox.ui.Muted
import com.agentkosticka.playbox.ui.NothingDotFont
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun AodScreen(repository: EffectRepository, glyphClient: GlyphMatrixClient) {
    val context = LocalContext.current
    val store = (context.applicationContext as PlayboxApplication).aodSettings
    val settings by store.settings.collectAsState()
    val effects by repository.effects.collectAsState()
    val selectedId by repository.activeId.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var pixels by remember { mutableStateOf(IntArray(PIXEL_COUNT)) }
    var message by remember { mutableStateOf<String?>(null) }
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    val selected = effects.firstOrNull { it.id == selectedId } ?: effects.firstOrNull()
    LaunchedEffect(settings, effects, selectedId, live) {
        val renderer = AodPlayback(effects, selectedId, settings)
        val start = android.os.SystemClock.elapsedRealtime()
        while (true) {
            hour = LocalTime.now().hour
            val frame = renderer.frameAt(android.os.SystemClock.elapsedRealtime() - start, hour)
            pixels = frame.pixels
            if (live) glyphClient.showFrame(frame.pixels)
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
                        Text(selected?.name ?: "Choose an effect", fontFamily = NothingDotFont.family)
                        Text(when { !settings.enabled -> "Paused"; settings.isQuiet(hour) -> "Quiet hours"; settings.rotate && settings.rotationIds.isNotEmpty() -> "Rotating your selection"; else -> "AOD preview" }, color = Muted)
                        TextButton(onClick = { showPicker = true }) { Text("CHOOSE EFFECT") }
                    }
                }
                AodToggle("AOD playback", settings.enabled) { store.save(settings.copy(enabled = it)) }
                OutlinedButton(onClick = {
                    live = !live
                    if (live) glyphClient.connect() else glyphClient.stopDisplay()
                }, enabled = glyphClient.isProbablySupported) { Text(if (live) "STOP MATRIX PREVIEW" else "PREVIEW ON MATRIX") }
                Button(onClick = {
                    glyphClient.openAodToyManager().onFailure { message = "Open Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy, then select Nothing Playbox." }
                }, modifier = Modifier.fillMaxWidth()) { Text("ENABLE IN NOTHING SETTINGS") }
                Text("Select Nothing Playbox as the system's Always-on Glyph Toy. These controls apply when Nothing OS runs the toy.", color = Muted)
                message?.let { Text(it, color = Muted) }
            }
        }
        Text("OUTPUT", fontFamily = NothingDotFont.family)
        Text("BRIGHTNESS  ${(settings.brightness * 100).roundToInt()}%")
        Slider(settings.brightness, { store.save(settings.copy(brightness = it)) }, valueRange = .05f..1f)
        Text("SPEED  ${String.format(java.util.Locale.US, "%.2f", settings.speed)}×")
        Slider(settings.speed, { store.save(settings.copy(speed = it)) }, valueRange = .25f..2f)
        AodToggle("Quiet hours", settings.quietHours) { store.save(settings.copy(quietHours = it)) }
        if (settings.quietHours) {
            HourPicker("From", settings.quietStart) { store.save(settings.copy(quietStart = it)) }
            HourPicker("Until", settings.quietEnd) { store.save(settings.copy(quietEnd = it)) }
            Text(if (settings.quietStart == settings.quietEnd) "Equal hours mute the entire day." else "Matrix stays dark during these hours in your phone's time zone.", color = Muted)
        }
        AodToggle("Rotate effects", settings.rotate) { store.save(settings.copy(rotate = it)) }
        if (settings.rotate) {
            Text("CHANGE EVERY ${settings.rotationSeconds} SECONDS")
            Slider(settings.rotationSeconds.toFloat(), { store.save(settings.copy(rotationSeconds = it.roundToInt())) }, valueRange = 10f..300f)
            Text("Choose effects and profiles to rotate. An empty selection uses your chosen effect.", color = Muted)
            effects.forEach { effect ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(effect.id in settings.rotationIds, { checked ->
                        store.save(settings.copy(rotationIds = if (checked) settings.rotationIds + effect.id else settings.rotationIds - effect.id))
                    })
                    Text(effect.name, Modifier.weight(1f))
                }
            }
        }
    }
    if (showPicker) AlertDialog(onDismissRequest = { showPicker = false }, title = { Text("AOD EFFECT") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                items(effects.size) { i ->
                    TextButton(onClick = { repository.setActiveEffect(effects[i].id); showPicker = false }) { Text(effects[i].name) }
                }
            }
        }, confirmButton = { TextButton(onClick = { showPicker = false }) { Text("CLOSE") } })
}

@Composable
private fun AodToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, change)
    }
}

@Composable
private fun HourPicker(label: String, hour: Int, change: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label ${hour.toString().padStart(2, '0')}:00") }
        DropdownMenu(open, { open = false }) {
            repeat(24) { value -> DropdownMenuItem(text = { Text("${value.toString().padStart(2, '0')}:00") }, onClick = { change(value); open = false }) }
        }
    }
}
