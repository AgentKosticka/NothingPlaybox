package com.agentkosticka.playbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentkosticka.playbox.data.ProceduralEffects
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.ProceduralSpec
import kotlin.math.roundToInt

@Composable
internal fun ProceduralEditorControls(
    spec: ProceduralSpec,
    onChange: (ProceduralSpec) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PROCEDURAL CONTROLS", fontFamily = FontFamily.Monospace)
        when (spec) {
            is ProceduralSpec.ConwayLife -> ConwayControls(spec, onChange)
            is ProceduralSpec.ShiftingNoise -> NoiseControls(spec, onChange)
            is ProceduralSpec.LavaLamp -> LavaControls(spec, onChange)
        }
    }
}

@Composable
private fun ConwayControls(
    spec: ProceduralSpec.ConwayLife,
    onChange: (ProceduralSpec) -> Unit,
) {
    val stepsPerSecond = 1_000f / spec.frameDurationMs
    Text("SPEED  ${"%.1f".format(stepsPerSecond)} steps/s", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Slider(
        value = stepsPerSecond,
        onValueChange = { speed ->
            val duration = (1_000f / speed.coerceAtLeast(0.5f)).roundToInt().coerceIn(67, 2_000)
            onChange(spec.copy(frameDurationMs = duration))
        },
        valueRange = 0.5f..15f,
    )
    Text("Draw directly on the matrix to choose the starting cells. STEP advances that seed by exactly one Conway generation.", fontSize = 12.sp, color = Muted)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onChange(spec.copy(initialState = ProceduralEffects.lifeStep(spec.initialState))) },
            modifier = Modifier.weight(1f),
        ) { Text("STEP") }
        OutlinedButton(
            onClick = { onChange(spec.copy(initialState = ProceduralEffects.randomLifeSeed(System.nanoTime()))) },
            modifier = Modifier.weight(1f),
        ) { Text("RANDOMIZE") }
        OutlinedButton(
            onClick = { onChange(spec.copy(initialState = IntArray(PIXEL_COUNT))) },
            modifier = Modifier.weight(1f),
        ) { Text("CLEAR") }
    }
}

@Composable
private fun NoiseControls(
    spec: ProceduralSpec.ShiftingNoise,
    onChange: (ProceduralSpec) -> Unit,
) {
    Text("DRIFT SPEED  ${"%.2f".format(spec.speed)}×", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Slider(value = spec.speed, onValueChange = { onChange(spec.copy(speed = it)) }, valueRange = 0.15f..4f)

    Text("SCALE  ${"%.2f".format(spec.scale)}×", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Slider(value = spec.scale, onValueChange = { onChange(spec.copy(scale = it)) }, valueRange = 0.45f..2.2f)

    Text("DETAIL  ${(spec.detail * 100).roundToInt()}%", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Slider(value = spec.detail, onValueChange = { onChange(spec.copy(detail = it)) }, valueRange = 0f..1f)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onChange(spec.copy(seed = System.nanoTime())) },
            modifier = Modifier.weight(1f),
        ) { Text("NEW SEED") }
        Text("Seed ${spec.seed.toString(16).uppercase()}", modifier = Modifier.weight(1f), fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun LavaControls(
    spec: ProceduralSpec.LavaLamp,
    onChange: (ProceduralSpec) -> Unit,
) {
    Text("SPEED  ${"%.2f".format(spec.speed)}×", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Slider(value = spec.speed, onValueChange = { onChange(spec.copy(speed = it)) }, valueRange = 0.15f..4f)

    Text("BLOBS  ${spec.blobCount}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Slider(
        value = spec.blobCount.toFloat(),
        onValueChange = { onChange(spec.copy(blobCount = it.roundToInt())) },
        valueRange = 2f..8f,
        steps = 5,
    )

    Text("SOFTNESS  ${"%.2f".format(spec.softness)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Slider(value = spec.softness, onValueChange = { onChange(spec.copy(softness = it)) }, valueRange = 0.08f..0.45f)

    OutlinedButton(onClick = { onChange(spec.copy(seed = System.nanoTime())) }) { Text("NEW SEED") }
}
