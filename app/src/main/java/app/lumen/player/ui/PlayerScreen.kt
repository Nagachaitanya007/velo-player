package app.lumen.player.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.lumen.player.playback.PlayerEngine
import app.lumen.player.playback.PlayerUiState
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    inPip: Boolean,
    showHint: Boolean,
    engine: PlayerEngine,
    onBack: () -> Unit,
    onPickSub: () -> Unit,
    onBrightness: (phase: Int, dy: Float, height: Float) -> Float,
    onVolume: (phase: Int, dy: Float, height: Float) -> Float,
    onPip: () -> Unit,
    onHintDone: () -> Unit,
) {
    var chrome by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var lockVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var osd by remember { mutableStateOf<String?>(null) }
    var osdToken by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFrom by remember { mutableLongStateOf(0L) }
    var scrubTo by remember { mutableLongStateOf(0L) }
    var hint by remember { mutableStateOf(showHint) }

    fun flash(text: String) {
        osdToken += 1
        osd = text
    }

    LaunchedEffect(osdToken) {
        val token = osdToken
        delay(900)
        if (token == osdToken && !scrubbing) osd = null
    }
    LaunchedEffect(chrome, state.playing, sheet, locked) {
        if (chrome && state.playing && !sheet && !locked) {
            delay(3200)
            chrome = false
        }
    }
    LaunchedEffect(locked) {
        if (locked) {
            lockVisible = true
            delay(1600)
            lockVisible = false
        }
    }
    LaunchedEffect(state.uri) {
        if (hint) {
            delay(5600)
            hint = false
            onHintDone()
        }
    }

    BackHandler {
        when {
            sheet -> sheet = false
            locked -> locked = false
            else -> onBack()
        }
    }

    val shownPosition = if (scrubbing) scrubTo else state.positionMs
    val duration = state.durationMs
    val chapter = state.chapters.lastOrNull { it.timeMs <= shownPosition }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                VLCVideoLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    engine.attach(this)
                }
            },
            onRelease = { engine.detach() },
        )

        if (!state.hasVideo && !state.opening && state.error == null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.title, color = Cream, fontFamily = Outfit, fontWeight = FontWeight(620), fontSize = 22.sp)
                Spacer(Modifier.height(6.dp))
                Text("Audio", color = CreamDim, fontFamily = Outfit)
            }
        }

        if (!inPip) {
            Box(
                Modifier
                    .fillMaxSize()
                    .watchGestures(
                        enabled = !sheet && !locked,
                        onTap = {
                            if (locked) lockVisible = true else chrome = !chrome
                        },
                        onDouble = { side ->
                            when (side) {
                                -1 -> {
                                    engine.seekBy(-10_000)
                                    flash("−10 seconds")
                                }
                                1 -> {
                                    engine.seekBy(10_000)
                                    flash("+10 seconds")
                                }
                                else -> engine.toggle()
                            }
                        },
                        onBoost = { active ->
                            engine.setBoost(active)
                            if (active) flash("2×")
                        },
                        onSeek = { phase, dx, width ->
                            val dur = state.durationMs
                            if (dur <= 0) return@watchGestures
                            when (phase) {
                                0 -> {
                                    scrubFrom = state.positionMs
                                    scrubTo = state.positionMs
                                    scrubbing = true
                                }
                                1 -> {
                                    val delta = (dx / width * dur).toLong()
                                    scrubTo = (scrubFrom + delta).coerceIn(0, dur)
                                    osd = formatTime(scrubTo)
                                }
                                else -> {
                                    engine.seekTo(scrubTo)
                                    scrubbing = false
                                    flash(formatTime(scrubTo))
                                }
                            }
                        },
                        onVertical = { left, phase, dy, height ->
                            val level = if (left) onBrightness(phase, dy, height) else onVolume(phase, dy, height)
                            if (phase == 1) {
                                val pct = (level * 100).roundToInt()
                                osd = if (left) "Light  $pct" else "Volume  $pct"
                            }
                            if (phase == 2) flash(osd ?: "")
                        },
                    ),
            )
        }

        if (state.opening || (state.buffering in 1f..97f && state.playing)) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(36.dp),
                color = Tungsten,
                strokeWidth = 2.dp,
            )
        }

        state.error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(InkRaised)
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Couldn’t play this", color = Cream, fontFamily = Outfit, fontWeight = FontWeight(620), fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(message, color = CreamDim, fontFamily = Outfit, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Row {
                    Pill("Try again", filled = true) { engine.retry() }
                    Spacer(Modifier.width(8.dp))
                    Pill("Close", filled = false, onClick = onBack)
                }
            }
        }

        if (!inPip && (osd != null || state.boosting)) {
            Text(
                text = if (state.boosting) "2×" else osd.orEmpty(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC07080A))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                color = Cream,
                fontFamily = Outfit,
                fontWeight = FontWeight(620),
                fontSize = 18.sp,
            )
        }

        if (!inPip && locked && lockVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 18.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC07080A))
                    .clickable { locked = false }
                    .padding(14.dp),
            ) {
                Icon(Icons.Rounded.Lock, "Unlock", tint = Cream)
            }
        }

        if (!inPip && chrome && !locked && state.error == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xC407080A), Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE607080A)))),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(Icons.Rounded.ArrowBack, "Back", onBack)
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(
                            state.title.ifBlank { "Velo" },
                            color = Cream,
                            fontFamily = Outfit,
                            fontWeight = FontWeight(620),
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = buildString {
                            if (state.queue.size > 1) append("${state.queueIndex + 1} of ${state.queue.size}")
                            if (state.videoInfo.isNotBlank()) {
                                if (isNotEmpty()) append("  ·  ")
                                append(state.videoInfo)
                            }
                        }
                        if (meta.isNotBlank()) {
                            Text(meta, color = CreamDim, fontFamily = Outfit, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    IconButton(Icons.Rounded.LockOpen, "Lock") { locked = true; chrome = false }
                    IconButton(Icons.Rounded.PictureInPictureAlt, "Picture in picture", onPip)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.queue.size > 1) {
                        IconButton(Icons.Rounded.SkipPrevious, "Previous") { engine.previous() }
                    }
                    IconButton(Icons.Rounded.Replay10, "Back 10 seconds") {
                        engine.seekBy(-10_000)
                        flash("−10 seconds")
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Cream)
                            .clickable { engine.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.playing && !state.ended) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (state.playing) "Pause" else "Play",
                            tint = Ink,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(Icons.Rounded.Forward10, "Forward 10 seconds") {
                        engine.seekBy(10_000)
                        flash("+10 seconds")
                    }
                    if (state.queue.size > 1) {
                        IconButton(Icons.Rounded.SkipNext, "Next") { engine.next() }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (chapter != null && state.chapters.size > 1) {
                        Text(chapter.name, color = Tungsten, fontFamily = Outfit, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                    }
                    Slider(
                        value = if (duration > 0) shownPosition.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                        onValueChange = {
                            scrubbing = true
                            scrubTo = it.toLong()
                        },
                        onValueChangeFinished = {
                            engine.seekTo(scrubTo)
                            scrubbing = false
                        },
                        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Tungsten,
                            activeTrackColor = Tungsten,
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                        ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(shownPosition), color = Cream, fontFamily = Outfit, fontSize = 12.sp)
                        Text(
                            if (duration > 0) "−${formatTime(duration - shownPosition)}" else "",
                            color = CreamDim,
                            fontFamily = Outfit,
                            fontSize = 12.sp,
                        )
                    }
                    if (state.chapters.size > 1 && duration > 0) {
                        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                            state.chapters.forEach { mark ->
                                val x = size.width * (mark.timeMs.toFloat() / duration).coerceIn(0f, 1f)
                                drawCircle(Cream.copy(alpha = 0.45f), radius = 2.2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, size.height / 2))
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton("${trimRate(state.rate)}×") { page = 3; sheet = true }
                    TextButton(subLabel(state)) { page = 2; sheet = true }
                    TextButton("Sound") { page = 1; sheet = true }
                    IconButton(Icons.Rounded.Tune, "Adjust") { page = 0; sheet = true }
                }
                if (state.resumeHintMs > 0) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Pill("Start from beginning", filled = false) {
                            engine.seekTo(0)
                            engine.clearResumeHint()
                        }
                    }
                    LaunchedEffect(state.uri) {
                        delay(5000)
                        engine.clearResumeHint()
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (!inPip && hint && !locked && state.error == null) {
            Text(
                "Double-tap to skip   ·   Hold for 2×   ·   Swipe for light and volume",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 28.dp, start = 24.dp, end = 24.dp),
                color = Cream.copy(alpha = 0.86f),
                fontFamily = Outfit,
                fontSize = 13.sp,
            )
        }
    }

    if (sheet && !inPip) {
        ModalBottomSheet(
            onDismissRequest = { sheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF14161C),
            contentColor = Cream,
        ) {
            AdjustSheet(state, page, { page = it }, engine, onPickSub)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Cream, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = Cream,
        fontFamily = Outfit,
        fontWeight = FontWeight(520),
        fontSize = 14.sp,
    )
}

@Composable
private fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(if (filled) Tungsten else Color.White.copy(0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (filled) Ink else Cream, fontFamily = Outfit, fontWeight = FontWeight(620), fontSize = 14.sp)
    }
}

private fun trimRate(rate: Float): String {
    val text = if (rate % 1f == 0f) rate.toInt().toString() else "%.2f".format(rate).trimEnd('0').trimEnd('.')
    return text
}

private fun subLabel(state: PlayerUiState): String {
    val current = state.spuTracks.firstOrNull { it.id == state.spuTrackId }
    val name = current?.name?.lowercase().orEmpty()
    return if (current == null || name.contains("disable") || name == "off") "Subtitles" else "Subtitles on"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdjustSheet(
    state: PlayerUiState,
    page: Int,
    onPage: (Int) -> Unit,
    engine: PlayerEngine,
    onPickSub: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Picture", "Sound", "Subtitles", "Play").forEachIndexed { index, label ->
                val on = page == index
                Text(
                    label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(if (on) Tungsten else Color.White.copy(0.06f))
                        .clickable { onPage(index) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (on) Ink else Cream,
                    fontFamily = Outfit,
                    fontWeight = FontWeight(620),
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> PicturePage(state, engine)
                1 -> SoundPage(state, engine)
                2 -> SubtitlePage(state, engine, onPickSub)
                else -> PlayPage(state, engine)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PicturePage(state: PlayerUiState, engine: PlayerEngine) {
    Text("Fit", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "fit" to "Fit",
            "crop" to "Crop",
            "stretch" to "Stretch",
            "16:9" to "16:9",
            "4:3" to "4:3",
            "2.39" to "2.39",
            "original" to "Pixel",
        ).forEach { (key, label) ->
            ChoiceChip(label, state.scaleName == key) { engine.setScale(key) }
        }
    }
    Spacer(Modifier.height(16.dp))
    CommitSlider("Zoom", state.zoom, 1f..3.5f, { "%.1f×".format(it) }) { engine.setZoom(it) }
    PictureSliders(state, engine)
    Spacer(Modifier.height(8.dp))
    Text("Rotate", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 90, 180, 270).forEach { deg ->
            ChoiceChip(if (deg == 0) "None" else "$deg°", state.rotation == deg) { engine.setRotation(deg) }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Deinterlace", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("off" to "Off", "auto" to "Auto", "yadif" to "Yadif", "blend" to "Blend").forEach { (key, label) ->
            ChoiceChip(label, state.deinterlace == key) { engine.setDeinterlace(key) }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Reset picture",
        modifier = Modifier.clickable { engine.resetPicture() }.padding(vertical = 8.dp),
        color = Tungsten,
        fontFamily = Outfit,
        fontWeight = FontWeight(620),
    )
    Text(
        "Color changes apply when you let go of the slider.",
        color = CreamDim,
        fontFamily = Outfit,
        fontSize = 12.sp,
    )
}

@Composable
private fun PictureSliders(state: PlayerUiState, engine: PlayerEngine) {
    var light by remember(state.brightness) { mutableFloatStateOf(state.brightness) }
    var contrast by remember(state.contrast) { mutableFloatStateOf(state.contrast) }
    var color by remember(state.saturation) { mutableFloatStateOf(state.saturation) }
    var gamma by remember(state.gamma) { mutableFloatStateOf(state.gamma) }
    fun commit() = engine.commitPicture(light, contrast, color, gamma)
    LiveSlider("Light", light, 0.2f..2f, { "%.2f".format(it) }, { light = it }, ::commit)
    LiveSlider("Contrast", contrast, 0.2f..2f, { "%.2f".format(it) }, { contrast = it }, ::commit)
    LiveSlider("Color", color, 0f..3f, { "%.2f".format(it) }, { color = it }, ::commit)
    LiveSlider("Gamma", gamma, 0.2f..2.2f, { "%.2f".format(it) }, { gamma = it }, ::commit)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundPage(state: PlayerUiState, engine: PlayerEngine) {
    Text("Track", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    if (state.audioTracks.isEmpty()) {
        Text("No extra audio tracks", color = Cream, fontFamily = Outfit)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.audioTracks.forEach { track ->
                ChoiceChip(prettyTrack(track.name), track.id == state.audioTrackId) { engine.selectAudio(track.id) }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    DelayRow("Audio delay", state.audioDelayMs) { engine.setAudioDelay(it) }
    Spacer(Modifier.height(16.dp))
    Text("Speed", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { rate ->
            ChoiceChip(trimRate(rate) + "×", absClose(state.rate, rate)) { engine.setRate(rate) }
        }
    }
    CommitSlider("Fine speed", state.rate, 0.25f..3f, { trimRate(it) + "×" }) { engine.setRate(it) }
    Spacer(Modifier.height(8.dp))
    Text("Equalizer", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("Off", !state.eqEnabled) { engine.setEqOff() }
        state.eqNames.forEachIndexed { index, name ->
            ChoiceChip(name, state.eqEnabled && state.eqPreset == index) { engine.setEqPreset(index) }
        }
    }
    if (state.eqFreqs.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        var pre by remember(state.eqPreamp, state.eqPreset) { mutableFloatStateOf(state.eqPreamp) }
        LiveSlider("Preamp", pre, -12f..12f, { "%.0f dB".format(it) }, { pre = it }) {
            engine.setCustomEq(state.eqAmps, pre)
        }
        state.eqFreqs.forEachIndexed { index, freq ->
            val amp = state.eqAmps.getOrElse(index) { 0f }
            var local by remember(amp, state.eqPreset, index) { mutableFloatStateOf(amp) }
            LiveSlider(freqLabel(freq), local, -20f..20f, { "%.0f".format(it) }, { local = it }) {
                val next = state.eqAmps.toMutableList()
                while (next.size <= index) next += 0f
                next[index] = local
                engine.setCustomEq(next, state.eqPreamp)
            }
        }
    }
    Text("Pitch stays natural when you change speed.", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitlePage(state: PlayerUiState, engine: PlayerEngine, onPickSub: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("Off", state.spuTrackId < 0) { engine.selectSpu(-1) }
        state.spuTracks.forEach { track ->
            val lower = track.name.lowercase()
            if (!lower.contains("disable")) {
                ChoiceChip(prettyTrack(track.name), track.id == state.spuTrackId) { engine.selectSpu(track.id) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Pill("Load a subtitle file", filled = true, onClick = onPickSub)
    Spacer(Modifier.height(16.dp))
    DelayRow("Subtitle delay", state.spuDelayMs) { engine.setSpuDelay(it) }
    Spacer(Modifier.height(12.dp))
    var size by remember(state.subSize) { mutableFloatStateOf(state.subSize.toFloat()) }
    LiveSlider("Size", size, 12f..48f, { it.roundToInt().toString() }, { size = it }) {
        engine.setSubtitleStyle(size.roundToInt(), state.subColor)
    }
    Spacer(Modifier.height(8.dp))
    Text("Color", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0xFFFFFF to Color.White, 0xFFE14A to Color(0xFFFFE14A), 0x7DFFB2 to Color(0xFF7DFFB2), 0x8FD4FF to Color(0xFF8FD4FF)).forEach { (rgb, tint) ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(tint)
                    .clickable { engine.setSubtitleStyle(state.subSize, rgb) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Size and color apply when you release the control.", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayPage(state: PlayerUiState, engine: PlayerEngine) {
    Text("Repeat", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("Off", state.repeatMode == app.lumen.player.playback.RepeatMode.Off) {
            engine.setRepeat(app.lumen.player.playback.RepeatMode.Off)
        }
        ChoiceChip("All", state.repeatMode == app.lumen.player.playback.RepeatMode.All) {
            engine.setRepeat(app.lumen.player.playback.RepeatMode.All)
        }
        ChoiceChip("One", state.repeatMode == app.lumen.player.playback.RepeatMode.One) {
            engine.setRepeat(app.lumen.player.playback.RepeatMode.One)
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("A–B loop", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip(state.abA?.let { "A  ${formatTime(it)}" } ?: "Set A", state.abA != null) { engine.markA() }
        ChoiceChip(state.abB?.let { "B  ${formatTime(it)}" } ?: "Set B", state.abB != null) { engine.markB() }
        ChoiceChip("Clear", false) { engine.clearAb() }
    }
    Spacer(Modifier.height(16.dp))
    Text("Sleep", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("Off", state.sleepUntil == null && !state.stopAtEnd) {
            engine.setSleepMinutes(0)
            engine.setStopAtEnd(false)
        }
        ChoiceChip("15m", false) { engine.setSleepMinutes(15) }
        ChoiceChip("30m", false) { engine.setSleepMinutes(30) }
        ChoiceChip("60m", false) { engine.setSleepMinutes(60) }
        ChoiceChip("End of video", state.stopAtEnd) { engine.setStopAtEnd(true) }
    }
    state.sleepUntil?.let { until ->
        val left = ((until - android.os.SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0)
        Spacer(Modifier.height(8.dp))
        Text("Sleeps in ${formatTime(left * 1000)}", color = Cream, fontFamily = Outfit)
    }
    Spacer(Modifier.height(16.dp))
    ChoiceChip(if (state.hw) "Hardware decode on" else "Hardware decode off", state.hw) {
        engine.setHw(!state.hw)
    }
    Spacer(Modifier.height(8.dp))
    ChoiceChip(if (state.pipOnLeave) "Picture-in-picture when you leave" else "Stay in the app when you leave", state.pipOnLeave) {
        engine.setPipOnLeave(!state.pipOnLeave)
    }
    if (state.chapters.size > 1) {
        Spacer(Modifier.height(16.dp))
        Text("Chapters", color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        state.chapters.forEach { chapter ->
            Text(
                "${formatTime(chapter.timeMs)}   ${chapter.name}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { engine.seekTo(chapter.timeMs) }
                    .padding(vertical = 8.dp),
                color = Cream,
                fontFamily = Outfit,
                fontSize = 14.sp,
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(
        "Playback by libVLC. Files stay on this phone.",
        color = CreamDim,
        fontFamily = Outfit,
        fontSize = 12.sp,
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(if (selected) Tungsten else Color.White.copy(0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) Ink else Cream,
        fontFamily = Outfit,
        fontWeight = FontWeight(520),
        fontSize = 13.sp,
        maxLines = 1,
    )
}

@Composable
private fun CommitSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onDone: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    LiveSlider(label, local, range, format, { local = it }) { onDone(local) }
}

@Composable
private fun LiveSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Cream, fontFamily = Outfit, fontSize = 14.sp, modifier = Modifier.width(88.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Tungsten,
                activeTrackColor = Tungsten,
                inactiveTrackColor = Color.White.copy(0.16f),
            ),
        )
        Text(format(value), color = CreamDim, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.width(64.dp))
    }
}

@Composable
private fun DelayRow(label: String, ms: Long, onChange: (Long) -> Unit) {
    Text(label, color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("−500", false) { onChange(ms - 500) }
        ChoiceChip("−50", false) { onChange(ms - 50) }
        Text(
            "${if (ms > 0) "+" else ""}${ms} ms",
            color = Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight(620),
            modifier = Modifier.width(92.dp),
        )
        ChoiceChip("+50", false) { onChange(ms + 50) }
        ChoiceChip("+500", false) { onChange(ms + 500) }
    }
    if (ms != 0L) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Reset",
            modifier = Modifier.clickable { onChange(0) }.padding(vertical = 4.dp),
            color = Tungsten,
            fontFamily = Outfit,
            fontWeight = FontWeight(620),
        )
    }
}

private fun prettyTrack(name: String): String = name.replace('_', ' ').ifBlank { "Track" }

private fun freqLabel(freq: Int): String = if (freq >= 1000) "${freq / 1000}k" else "$freq"

private fun absClose(a: Float, b: Float) = kotlin.math.abs(a - b) < 0.02f
