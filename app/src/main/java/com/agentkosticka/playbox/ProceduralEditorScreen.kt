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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProceduralEditorScreen(
    initial: PlayboxEffect,
    connection: GlyphConnectionState,
    glyphClient: GlyphMatrixClient,
    onDraftChanged: (PlayboxEffect) -> Unit,
    onBack: (PlayboxEffect) -> Unit,
    onDiscard: () -> Unit,
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

    SideEffect { onDraftChanged(draft) }

    fun refreshPreview() {
        previewPixels = ProceduralEffectRuntime(draft).frameAt(0).pixels
        conwayGeneration = 0
    }

    fun updateSpec(next: ProceduralSpec) {
        draft = draft.copy(procedural = next, updatedAt = System.currentTimeMillis())
        refreshPreview()
    }

    fun exportableDraft(): PlayboxEffect = draft.copy(
        frames = listOf(ProceduralEffectRuntime(draft).frameAt(0)),
    )

    fun finish() {
        glyphClient.stopDisplay()
        onBack(draft)
    }

    fun discard() {
        glyphClient.stopDisplay()
        onDiscard()
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

    LaunchedEffect(live, playing, connection, if (playing) 0 else previewPixels.contentHashCode()) {
        when {
            live && !playing && connection == GlyphConnectionState.Ready -> glyphClient.showFrame(previewPixels)
            !live -> glyphClient.stopDisplay()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                title = {
                    Column {
                        Text(stringResource(R.string.profile_lab_title), fontFamily = FontFamily.Monospace)
                        Text(proceduralTypeLabel(requireNotNull(draft.procedural)), color = Muted, fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::finish) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.save_and_back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = { onExport(exportableDraft()) }) {
                        Icon(Icons.Default.Download, stringResource(R.string.export_cd))
                    }
                    TextButton(onClick = ::discard) { Text(stringResource(R.string.discard)) }
                    TextButton(onClick = ::finish) { Text(stringResource(R.string.save)) }
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
                    label = { Text(stringResource(R.string.profile_name)) },
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
                        Text(stringResource(if (playing) R.string.stop else R.string.play))
                    }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = live,
                        onClick = {
                            live = !live
                            if (!live) glyphClient.stopDisplay()
                        },
                        label = { Text(stringResource(if (live) R.string.live_matrix else R.string.simulator)) },
                        leadingIcon = { Icon(Icons.Default.Lightbulb, null) },
                    )
                }
                if (connection is GlyphConnectionState.Connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (val spec = draft.procedural) {
                is ProceduralSpec.RippleField -> {
                    item { Text(stringResource(R.string.ripple_controls), fontFamily = FontFamily.Monospace) }
                    item {
                        Text(stringResource(R.string.wave_speed, String.format(Locale.US, "%.1f", spec.speed)))
                        Slider(spec.speed, { updateSpec(spec.copy(speed = it)) }, valueRange = .15f..4f)
                    }
                    item {
                        Text(stringResource(R.string.wavelength, String.format(Locale.US, "%.1f", spec.wavelength)))
                        Slider(spec.wavelength, { updateSpec(spec.copy(wavelength = it)) }, valueRange = 1.5f..6f)
                    }
                    item {
                        Text(stringResource(R.string.sources, spec.sources))
                        Slider(spec.sources.toFloat(), { updateSpec(spec.copy(sources = it.roundToInt())) }, valueRange = 1f..5f, steps = 3)
                    }
                }
                is ProceduralSpec.Starfield -> {
                    item { Text(stringResource(R.string.starfield_controls), fontFamily = FontFamily.Monospace) }
                    item {
                        Text(stringResource(R.string.flight_speed, String.format(Locale.US, "%.1f", spec.speed)))
                        Slider(spec.speed, { updateSpec(spec.copy(speed = it)) }, valueRange = .15f..4f)
                    }
                    item {
                        Text(stringResource(R.string.stars, spec.stars))
                        Slider(spec.stars.toFloat(), { updateSpec(spec.copy(stars = it.roundToInt())) }, valueRange = 8f..60f)
                    }
                    item {
                        Text(stringResource(R.string.trails, (spec.trails * 100).roundToInt()))
                        Slider(spec.trails, { updateSpec(spec.copy(trails = it)) })
                    }
                    item {
                        OutlinedButton(onClick = { updateSpec(spec.copy(seed = System.nanoTime())) }) {
                            Text(stringResource(R.string.new_constellation))
                        }
                    }
                }
                is ProceduralSpec.ConwayLife -> {
                    item {
                        Text(stringResource(R.string.conway_controls), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.conway_seed_help), color = Muted, fontSize = 12.sp)
                    }
                    item {
                        val stepsPerSecond = 1_000f / spec.frameDurationMs
                        Text(
                            stringResource(R.string.conway_speed, String.format(Locale.US, "%.1f", stepsPerSecond)),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                        Slider(
                            value = stepsPerSecond,
                            onValueChange = { speed ->
                                val duration = (1_000f / speed.coerceAtLeast(0.5f)).roundToInt().coerceIn(67, 2_000)
                                updateSpec(spec.copy(frameDurationMs = duration))
                            },
                            valueRange = 0.5f..15f,
                        )
                    }
                    item {
                        Text(stringResource(R.string.preview_generation, conwayGeneration), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !playing, onClick = {
                                previewPixels = ProceduralEffects.lifeStep(previewPixels)
                                conwayGeneration++
                            }) { Text(stringResource(R.string.step)) }
                            OutlinedButton(enabled = !playing, onClick = { refreshPreview() }) {
                                Icon(Icons.Default.Refresh, null)
                                Text(stringResource(R.string.reset))
                            }
                            OutlinedButton(enabled = !playing, onClick = {
                                updateSpec(spec.copy(initialState = IntArray(PIXEL_COUNT)))
                            }) { Text(stringResource(R.string.clear)) }
                        }
                    }
                    item {
                        OutlinedButton(enabled = !playing, onClick = {
                            val seed = System.nanoTime()
                            updateSpec(spec.copy(initialState = ProceduralEffects.randomLifeSeed(seed)))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.randomize_starting_cells))
                        }
                    }
                }
                is ProceduralSpec.ShiftingNoise -> {
                    item {
                        Text(stringResource(R.string.noise_controls), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.noise_help), color = Muted, fontSize = 12.sp)
                    }
                    item {
                        Text(
                            stringResource(R.string.drift_speed, String.format(Locale.US, "%.2f", spec.speed)),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                        Slider(value = spec.speed, onValueChange = { updateSpec(spec.copy(speed = it)) }, valueRange = 0.15f..4f)
                    }
                    item {
                        Text(
                            stringResource(R.string.scale, String.format(Locale.US, "%.2f", spec.scale)),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                        Slider(value = spec.scale, onValueChange = { updateSpec(spec.copy(scale = it)) }, valueRange = 0.45f..2.2f)
                    }
                    item {
                        Text(stringResource(R.string.detail, (spec.detail * 100).roundToInt()), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(value = spec.detail, onValueChange = { updateSpec(spec.copy(detail = it)) }, valueRange = 0f..1f)
                    }
                    item {
                        OutlinedButton(onClick = { updateSpec(spec.copy(seed = System.nanoTime())) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.new_noise_seed))
                        }
                    }
                }
                is ProceduralSpec.LavaLamp -> {
                    item {
                        Text(stringResource(R.string.lava_controls), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.lava_help), color = Muted, fontSize = 12.sp)
                    }
                    item {
                        Text(
                            stringResource(R.string.flow_speed, String.format(Locale.US, "%.2f", spec.speed)),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                        Slider(value = spec.speed, onValueChange = { updateSpec(spec.copy(speed = it)) }, valueRange = 0.15f..4f)
                    }
                    item {
                        Text(stringResource(R.string.blobs, spec.blobCount), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(
                            value = spec.blobCount.toFloat(),
                            onValueChange = { updateSpec(spec.copy(blobCount = it.roundToInt())) },
                            valueRange = 2f..8f,
                            steps = 5,
                        )
                    }
                    item {
                        Text(stringResource(R.string.softness, (spec.softness * 100).roundToInt()), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(value = spec.softness, onValueChange = { updateSpec(spec.copy(softness = it)) }, valueRange = 0.08f..0.45f)
                    }
                    item {
                        OutlinedButton(onClick = { updateSpec(spec.copy(seed = System.nanoTime())) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.new_lava_seed))
                        }
                    }
                }
                is ProceduralSpec.OrganicBloom -> {
                    item {
                        Text(stringResource(R.string.organic_bloom_controls), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.organic_bloom_help), color = Muted, fontSize = 12.sp)
                    }
                    item {
                        val updatesPerSecond = 1_000f / spec.frameDurationMs
                        Text(
                            stringResource(R.string.evolution, String.format(Locale.US, "%.1f", updatesPerSecond)),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                        Slider(
                            value = updatesPerSecond,
                            onValueChange = { speed ->
                                updateSpec(spec.copy(frameDurationMs = (1_000f / speed.coerceAtLeast(2f)).roundToInt().coerceIn(67, 500)))
                            },
                            valueRange = 2f..15f,
                        )
                    }
                    item {
                        val growth = ((spec.feed - 0.035f) / 0.04f).coerceIn(0f, 1f)
                        Text(stringResource(R.string.growth, (growth * 100).roundToInt()), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(value = growth, onValueChange = { updateSpec(spec.copy(feed = 0.035f + it * 0.04f)) })
                    }
                    item {
                        val split = ((spec.kill - 0.045f) / 0.03f).coerceIn(0f, 1f)
                        Text(stringResource(R.string.split, (split * 100).roundToInt()), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Slider(value = split, onValueChange = { updateSpec(spec.copy(kill = 0.045f + it * 0.03f)) })
                    }
                    item {
                        OutlinedButton(onClick = { updateSpec(spec.copy(seed = System.nanoTime())) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.new_bloom_seed))
                        }
                    }
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun proceduralTypeLabel(spec: ProceduralSpec): String = stringResource(
    when (spec) {
        is ProceduralSpec.RippleField -> R.string.type_ripple
        is ProceduralSpec.Starfield -> R.string.type_starfield
        is ProceduralSpec.ConwayLife -> R.string.type_conway
        is ProceduralSpec.ShiftingNoise -> R.string.type_noise
        is ProceduralSpec.LavaLamp -> R.string.type_lava
        is ProceduralSpec.OrganicBloom -> R.string.type_bloom
    },
)
