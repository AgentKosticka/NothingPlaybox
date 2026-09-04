package com.agentkosticka.playbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentkosticka.playbox.data.ProceduralEffectRuntime
import com.agentkosticka.playbox.data.ProceduralEffects
import com.agentkosticka.playbox.matrix.GlyphConnectionState
import com.agentkosticka.playbox.matrix.GlyphMatrixClient
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.model.ProceduralSpec
import com.agentkosticka.playbox.model.deepCopy
import com.agentkosticka.playbox.ui.MatrixDisplay
import com.agentkosticka.playbox.ui.Muted
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProceduralEditorScreen(
    initial: PlayboxEffect,
    connection: GlyphConnectionState,
    glyphClient: GlyphMatrixClient,
    onBack: (PlayboxEffect) -> Unit,
    onExport: (PlayboxEffect) -> Unit,
) {
    var draft by remember(initial.id) {
        mutableStateOf(initial.copy(procedural = requireNotNull(initial.procedural).deepCopy()))
    }
    var previewPixels by remember(initial.id) {
        mutableStateOf(ProceduralEffectRuntime(draft).frameAt(0).pixels)
    }
    var playing by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var conwayGeneration by remember { mutableIntStateOf(0) }

    fun refreshPreview() {
        previewPixels = ProceduralEffectRuntime(draft).frameAt(0).pixels
        conwayGeneration = 0
        if (live && connection == GlyphConnectionState.Ready) glyphClient.showFrame(previewPixels)
    }

    fun updateSpec(next: ProceduralSpec) {
        playing = false
        draft = draft.copy(procedural = next, updatedAt = System.currentTimeMillis())
        refreshPreview()
    }

    fun finish() {
        glyphClient.stopDisplay()
        onBack(draft)
    }

    BackHandler { finish() }
    DisposableEffect(Unit) { onDispose { glyphClient.stopDisplay() } }

    LaunchedEffect(playing, draft.procedural, live, connection) {
        if (!playing) return@LaunchedEffect
        val runtime = ProceduralEffectRuntime(draft)
        val started = android.os.SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            val frame = runtime.frameAt(elapsed)
            previewPixels = frame.pixels
            if (draft.procedural is ProceduralSpec.ConwayLife) {
                conwayGeneration = (elapsed / frame.durationMs).toInt()
            }
            if (live && connection == GlyphConnectionState.Ready) glyphClient.showFrame(frame.pixels)
            delay(frame.durationMs.toLong())
        }
    }

    LaunchedEffect(live, connection, previewPixels) {
        if (live && connection == GlyphConnectionState.Ready) glyphClient.showFrame(previewPixels)
        else if (!live) glyphClient.stopDisplay()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                title = {
                    Column {
                        Text("PROCEDURAL LAB", fontFamily = FontFamily.Monospace)
                        Text(proceduralTypeLabel(requireNotNull(draft.procedural)), color = Muted, fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::finish) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Save and back")
                    }
                },
                actions = {
                    IconButton(onClick = { onExport(draft) }) { Icon(Icons.Default.Download, "Export") }
                    TextButton(onClick = ::finish) { Text("SAVE") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(60)) },
                    label = { Text("Effect name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val conway = draft.procedural as? ProceduralSpec.ConwayLife
                Surface(color = Color.Black, shape = CircleShape, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    MatrixDisplay(
                        pixels = previewPixels,
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        onPixel = if (conway == null || playing) null else { index ->
                            val seed = conway.initialState.copyOf()
                            seed[index] = if (seed[index] > 0) 0 else 255
                            updateSpec(conway.copy(initialState = seed))
                        },
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { playing = !playing }) {
                        Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Text(if (playing) " STOP" else " PLAY")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = live,
                        onClick = {
                            live = !live
                            if (live) glyphClient.connect() else glyphClient.stopDisplay()
                        },
                        label = { Text(if (live) "LIVE MATRIX" else "SIMULATOR") },
                        leadingIcon = { Icon(Icons.Default.Lightbulb, null) },
                    )
                }
                if (connection is GlyphConnectionState.Connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (val spec = draft.procedural) {
                is ProceduralSpec.ConwayLife -> {
                    item {
                        Text("CONWAY CONTROLS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Tap or drag the matrix to define generation 0. Playback always starts from this seed.", color = Muted, fontSize = 12.sp)
                    }
                    item {
                        Text("STEP TIME  ${spec.frameDurationMs} ms", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(
                            value = spec.frameDurationMs.toFloat(),
                            onValueChange = { updateSpec(spec.copy(frameDurationMs = it.roundToInt())) },
                            valueRange = 67f..1_000f,
                        )
                    }
                    item {
                        Text("PREVIEW GENERATION  $conwayGeneration", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !playing, onClick = {
                                previewPixels = ProceduralEffects.lifeStep(previewPixels)
                                conwayGeneration++
                                if (live && connection == GlyphConnectionState.Ready) glyphClient.showFrame(previewPixels)
                            }) { Text("STEP") }
                            OutlinedButton(enabled = !playing, onClick = { refreshPreview() }) {
                                Icon(Icons.Default.Refresh, null)
                                Text(" RESET")
                            }
                            OutlinedButton(enabled = !playing, onClick = {
                                updateSpec(spec.copy(initialState = IntArray(PIXEL_COUNT)))
                            }) { Text("CLEAR") }
                        }
                    }
                    item {
                        OutlinedButton(enabled = !playing, onClick = {
                            val seed = System.nanoTime()
                            updateSpec(spec.copy(initialState = ProceduralEffects.randomLifeSeed(seed)))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("RANDOMIZE STARTING CELLS")
                        }
                    }
                }
                is ProceduralSpec.ShiftingNoise -> {
                    item {
                        Text("NOISE CONTROLS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Nothing is pre-rendered: the field is sampled continuously while the effect runs.", color = Muted, fontSize = 12.sp)
                    }
                    item {
                        Text("DRIFT SPEED  ${"%.2f".format(spec.speed)}×", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(value = spec.speed, onValueChange = { updateSpec(spec.copy(speed = it)) }, valueRange = 0.15f..4f)
                    }
                    item {
                        Text("SCALE  ${"%.2f".format(spec.scale)}×", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(value = spec.scale, onValueChange = { updateSpec(spec.copy(scale = it)) }, valueRange = 0.45f..2.2f)
                    }
                    item {
                        Text("DETAIL  ${(spec.detail * 100).roundToInt()}%", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(value = spec.detail, onValueChange = { updateSpec(spec.copy(detail = it)) }, valueRange = 0f..1f)
                    }
                    item {
                        OutlinedButton(onClick = { updateSpec(spec.copy(seed = System.nanoTime())) }, modifier = Modifier.fillMaxWidth()) {
                            Text("NEW NOISE SEED")
                        }
                    }
                }
                is ProceduralSpec.LavaLamp -> {
                    item {
                        Text("LAVA CONTROLS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Metaballs are generated live, so changing the controls changes the motion itself — not a stored loop.", color = Muted, fontSize = 12.sp)
                    }
                    item {
                        Text("FLOW SPEED  ${"%.2f".format(spec.speed)}×", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(value = spec.speed, onValueChange = { updateSpec(spec.copy(speed = it)) }, valueRange = 0.15f..4f)
                    }
                    item {
                        Text("BLOBS  ${spec.blobCount}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(
                            value = spec.blobCount.toFloat(),
                            onValueChange = { updateSpec(spec.copy(blobCount = it.roundToInt())) },
                            valueRange = 2f..8f,
                            steps = 5,
                        )
                    }
                    item {
                        Text("SOFTNESS  ${(spec.softness * 100).roundToInt()}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(value = spec.softness, onValueChange = { updateSpec(spec.copy(softness = it)) }, valueRange = 0.08f..0.45f)
                    }
                    item {
                        OutlinedButton(onClick = { updateSpec(spec.copy(seed = System.nanoTime())) }, modifier = Modifier.fillMaxWidth()) {
                            Text("NEW LAVA SEED")
                        }
                    }
                }
                null -> Unit
            }
        }
    }
}

private fun proceduralTypeLabel(spec: ProceduralSpec): String = when (spec) {
    is ProceduralSpec.ConwayLife -> "CONWAY LIFE • LIVE SIMULATION"
    is ProceduralSpec.ShiftingNoise -> "SHIFTING NOISE • LIVE FIELD"
    is ProceduralSpec.LavaLamp -> "LAVA LAMP • LIVE METABALLS"
}
