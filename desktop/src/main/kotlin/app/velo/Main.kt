@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.velo

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.Job
import java.awt.FileDialog
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import java.io.File
import kotlin.math.max
import kotlin.math.min

private val Ink = Color(0xFF07080A)
private val InkRaised = Color(0xFF14161C)
private val Cream = Color(0xFFF3EFE6)
private val CreamDim = Color(0xFFA39E93)
private val Tungsten = Color(0xFFE4A15A)
private val Hairline = Color(0x22F3EFE6)

fun main() = application {
    val engine = remember { Engine().also { it.start() } }
    DisposableEffect(engine) { onDispose { engine.release() } }
    var clips by remember { mutableStateOf<List<DiskVideo>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var scanRequest by remember { mutableStateOf(0) }
    val scanGen = remember { AtomicInteger(0) }
    LaunchedEffect(scanRequest) {
        val mine = scanGen.incrementAndGet()
        scanning = true
        startScan({ scanGen.get() != mine }) { list, running ->
            SwingUtilities.invokeLater {
                if (scanGen.get() != mine) return@invokeLater
                clips = list
                scanning = running
            }
        }
    }
    val windowState = rememberWindowState(width = 1180.dp, height = 760.dp)
    var fullscreen by remember { mutableStateOf(false) }
    var adjust by remember { mutableStateOf(false) }
    var spaceDown by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    windowState.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Velo",
    ) {
        val outfit = remember { loadOutfit() }
        val watching = engine.tick
        LaunchedEffect(engine.phase, watching) { focus.requestFocus() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    if (engine.phase != "player") return@onKeyEvent false
                    if (event.key == Key.Spacebar) {
                        if (event.type == KeyEventType.KeyDown && !spaceDown) {
                            spaceDown = true
                            engine.toggle()
                        }
                        if (event.type == KeyEventType.KeyUp) spaceDown = false
                        return@onKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> engine.seekBy(-10_000)
                        Key.DirectionRight -> engine.seekBy(10_000)
                        Key.DirectionUp -> engine.setVolume(engine.volume + 5)
                        Key.DirectionDown -> engine.setVolume(engine.volume - 5)
                        Key.F -> fullscreen = !fullscreen
                        Key.Escape -> if (fullscreen) fullscreen = false else if (adjust) adjust = false else engine.back()
                        else -> return@onKeyEvent false
                    }
                    true
                },
        ) {
            if (engine.phase == "player") {
                Stage(engine, outfit, adjust, { adjust = !adjust }, { engine.back() }, { fullscreen = !fullscreen })
            } else {
                Home(engine, outfit, window, clips, scanning) { scanRequest++ }
            }
        }
    }
}

private fun loadOutfit(): FontFamily {
    val stream = object {}::class.java.getResourceAsStream("/font/outfit.ttf") ?: return FontFamily.SansSerif
    val file = File.createTempFile("velo-outfit", ".ttf")
    file.deleteOnExit()
    stream.use { file.writeBytes(it.readBytes()) }
    return FontFamily(
        androidx.compose.ui.text.platform.Font(file = file, weight = FontWeight.Normal),
        androidx.compose.ui.text.platform.Font(file = file, weight = FontWeight.Medium),
        androidx.compose.ui.text.platform.Font(file = file, weight = FontWeight.SemiBold),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Home(
    engine: Engine,
    outfit: FontFamily,
    window: java.awt.Frame,
    clips: List<DiskVideo>,
    scanning: Boolean,
    onRefresh: () -> Unit,
) {
    var armClear by remember { mutableStateOf(false) }
    var unfinishedOnly by remember { mutableStateOf(false) }
    var sortByName by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var librarySort by remember { mutableStateOf(0) }
    var asGrid by remember { mutableStateOf(false) }
    var stack by remember { mutableStateOf(listOf<String>()) }
    val recents = engine.recents
    val resume = recents.firstOrNull { unfinished(it) }
    val shown = recents
        .let { if (unfinishedOnly) it.filter { item -> unfinished(item) } else it }
        .let { if (sortByName) it.sortedBy { item -> item.file.name.lowercase() } else it }
    val needle = query.trim()
    val searching = needle.isNotEmpty()
    LaunchedEffect(clips, stack) {
        val prefix = stack.joinToString("/")
        val alive = prefix.isEmpty() || clips.any { inFolder(libraryDir(it.file), prefix) }
        if (!alive) stack = emptyList()
    }
    val opened = if (searching) emptyList() else stack
    val shelves = if (searching) emptyList() else foldersHere(clips, opened)
    val loose = sortClips(
        if (searching) {
            clips.filter {
                it.file.name.contains(needle, true) || libraryDir(it.file).contains(needle, true)
            }
        } else {
            clipsHere(clips, opened)
        },
        librarySort,
    )
    fun play(file: File, start: Long) = engine.open(file, start)
    fun playKnown(file: File, fromStart: Boolean) {
        val recent = engine.recents.firstOrNull { it.file.absolutePath == file.absolutePath }
        val start = if (!fromStart && recent != null && unfinished(recent)) recent.positionMs else 0L
        play(file, start)
    }
    val libraryLabel = when {
        scanning && clips.isEmpty() -> "Looking through your folders…"
        searching && loose.isEmpty() -> "Nothing matches."
        clips.isEmpty() -> "No videos in the usual folders yet."
        searching -> countLine(loose.size, 0)
        scanning -> countLine(loose.size, shelves.size) + ", still looking…"
        else -> countLine(loose.size, shelves.size)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (!asGrid) 1 else when {
            maxWidth >= 1100.dp -> 4
            maxWidth >= 760.dp -> 3
            else -> 2
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Tungsten))
                Spacer(Modifier.width(10.dp))
                Text("VELO", color = CreamDim, fontFamily = outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 2.4.sp)
            }
            Spacer(Modifier.height(36.dp))
            Text("Anything this computer\ncan decode.", color = Cream, fontFamily = outfit, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 46.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                engine.failure ?: "Videos already on this computer are listed here. Picture, sound, and subtitles stay out of the way until you ask.",
                color = CreamDim,
                fontFamily = outfit,
                fontSize = 16.sp,
            )
            if (resume != null) {
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Tungsten)
                        .clickable { engine.open(resume.file, resume.positionMs) }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Resume  ·  ${resume.file.name}", color = Ink, fontFamily = outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recents.isNotEmpty()) {
                    QuietChip("Unfinished", unfinishedOnly, outfit) { unfinishedOnly = !unfinishedOnly }
                    QuietChip(if (sortByName) "A–Z" else "Newest", sortByName, outfit) { sortByName = !sortByName }
                    QuietChip(if (armClear) "Erase the list" else "Clear history", armClear, outfit) {
                        if (armClear) {
                            armClear = false
                            engine.clearHistory()
                        } else armClear = true
                    }
                }
                QuietChip("Reset look", false, outfit) { engine.resetLook() }
                QuietChip("Browse files", false, outfit) {
                    pick(window, true)?.let { files -> playKnown(files.first(), false) }
                }
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!searching && opened.isNotEmpty()) {
                    Text("Back", color = Tungsten, fontFamily = outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable { stack = stack.dropLast(1) })
                    Spacer(Modifier.width(14.dp))
                    Text(opened.last(), color = Cream, fontFamily = outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(if (searching) "SEARCH" else "ON THIS COMPUTER", color = CreamDim, fontFamily = outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.6.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Cream, fontFamily = outfit, fontSize = 15.sp),
                cursorBrush = SolidColor(Tungsten),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InkRaised).padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        if (query.isEmpty()) Text("Search videos", color = CreamDim, fontFamily = outfit, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietChip("Newest", librarySort == 0, outfit) { librarySort = 0 }
                QuietChip("A–Z", librarySort == 1, outfit) { librarySort = 1 }
                QuietChip(if (asGrid) "Grid" else "Rows", asGrid, outfit) { asGrid = !asGrid }
                QuietChip("Refresh", false, outfit, onRefresh)
            }
            Spacer(Modifier.height(12.dp))
            Text(libraryLabel, color = CreamDim, fontFamily = outfit, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
        }
        if (!searching) {
            gridItems(shelves, key = { "dir:${opened.joinToString("/")}/${it.name}" }, span = { GridItemSpan(maxLineSpan) }) { shelf ->
                FolderRow(shelf.name, shelf.count, outfit) { stack = opened + shelf.name }
            }
        }
        if (asGrid) {
            gridItems(loose, key = { it.file.absolutePath }) { clip ->
                DiskCell(clip, outfit) { playKnown(clip.file, false) }
            }
        } else {
            gridItems(loose, key = { it.file.absolutePath }, span = { GridItemSpan(maxLineSpan) }) { clip ->
                val recent = recents.firstOrNull { it.file.absolutePath == clip.file.absolutePath }
                DiskRow(clip, recent, searching, outfit, { playKnown(clip.file, false) }, { playKnown(clip.file, true) })
            }
        }
        if (recents.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(28.dp))
                Text("CONTINUE", color = CreamDim, fontFamily = outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.6.sp)
                Spacer(Modifier.height(12.dp))
                if (shown.isEmpty()) Text("Nothing unfinished.", color = CreamDim, fontFamily = outfit, fontSize = 14.sp)
            }
            gridItems(shown, key = { "recent-${it.file.absolutePath}" }, span = { GridItemSpan(maxLineSpan) }) { item ->
                RecentRow(item, outfit, { engine.open(item.file, if (unfinished(item)) item.positionMs else 0L) }, { engine.open(item.file, 0) }, { engine.forget(item.file.absolutePath) })
            }
        }
        }
    }
}

@Composable
private fun DiskRow(clip: DiskVideo, recent: Desk?, showFolder: Boolean, outfit: FontFamily, onOpen: () -> Unit, onStart: () -> Unit) {
    val progress = if (recent != null && recent.durationMs > 0) (recent.positionMs.toFloat() / recent.durationMs).coerceIn(0f, 1f) else 0f
    val detail = clipDetail(clip, showFolder)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InkRaised).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Poster(clip.file, Modifier.size(112.dp, 64.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(clip.file.name, color = Cream, fontFamily = outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(detail, color = CreamDim, fontFamily = outfit, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (progress > 0.02f && progress < 0.97f) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Hairline)) {
                        Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Tungsten))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("From start", color = Tungsten, fontFamily = outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onStart))
    }
}

@Composable
private fun DiskCell(clip: DiskVideo, outfit: FontFamily, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InkRaised).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF101218))) {
            Poster(clip.file, Modifier.fillMaxSize())
        }
        Text(
            clip.file.name,
            color = Cream,
            fontFamily = outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FolderRow(name: String, count: Int, outfit: FontFamily, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InkRaised).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(name, color = Cream, fontFamily = outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(if (count == 1) "1 video" else "$count videos", color = CreamDim, fontFamily = outfit, fontSize = 12.sp)
    }
}

@Composable
private fun RecentRow(item: Desk, outfit: FontFamily, onOpen: () -> Unit, onStart: () -> Unit, onForget: () -> Unit) {
    val progress = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
    val detail = when {
        item.durationMs <= 0 -> "Open"
        progress < 0.02f -> formatTime(item.durationMs)
        progress > 0.97f -> "Finished"
        else -> "${formatTime((item.durationMs - item.positionMs).coerceAtLeast(0))} left"
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InkRaised).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Poster(item.file, Modifier.size(112.dp, 64.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(item.file.name, color = Cream, fontFamily = outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Hairline)) {
                        Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Tungsten))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(detail, color = CreamDim, fontFamily = outfit, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Text("From start", color = Tungsten, fontFamily = outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onStart))
            Spacer(Modifier.width(18.dp))
            Text("Remove", color = CreamDim, fontFamily = outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onForget))
        }
    }
}

@Composable
private fun Poster(file: File, modifier: Modifier) {
    var image by remember(file.absolutePath, file.length(), file.lastModified()) { mutableStateOf(Posters.peek(file)) }
    LaunchedEffect(file.absolutePath, file.length(), file.lastModified()) {
        if (image != null) return@LaunchedEffect
        val job = coroutineContext[Job]
        Posters.want(file) { bmp ->
            SwingUtilities.invokeLater {
                if (job?.isActive != false) image = bmp
            }
        }
    }
    Box(modifier.background(Color(0xFF101218))) {
        image?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Stage(
    engine: Engine,
    outfit: FontFamily,
    adjust: Boolean,
    onAdjust: () -> Unit,
    onBack: () -> Unit,
    onFull: () -> Unit,
) {
    var chrome by remember { mutableStateOf(true) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .onPointerEvent(PointerEventType.Move) { chrome = true }
            .onPointerEvent(PointerEventType.Scroll) {
                val delta = it.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                engine.setVolume(engine.volume - (delta * 6).toInt())
            }
            .pointerInput(size) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (offset.x < size.width * 0.34f) engine.seekBy(-10_000)
                        else if (offset.x > size.width * 0.66f) engine.seekBy(10_000)
                        else engine.toggle()
                    },
                    onTap = { chrome = !chrome },
                )
            },
    ) {
        val frame = engine.frame
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            if (frame == null) return@Canvas
            val srcW = frame.width.toFloat()
            val srcH = frame.height.toFloat()
            val scale = when (engine.fit) {
                "fill" -> max(size.width / srcW, size.height / srcH)
                "stretch" -> -1f
                else -> min(size.width / srcW, size.height / srcH)
            }
            if (engine.fit == "stretch") {
                drawImage(frame, dstSize = IntSize(size.width, size.height))
            } else {
                val dw = srcW * scale
                val dh = srcH * scale
                drawImage(
                    frame,
                    dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - dw) / 2f).toInt(), ((size.height - dh) / 2f).toInt()),
                    dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
                )
            }
        }
        if (engine.opening && frame == null) {
            Text("Opening", color = CreamDim, fontFamily = outfit, modifier = Modifier.align(Alignment.Center))
        }
        engine.playError?.let { message ->
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(18.dp)).background(InkRaised).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Cream, fontFamily = outfit, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Text("Try again", color = Ink, fontFamily = outfit, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Tungsten).clickable { engine.retry() }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        if (chrome) {
            Row(Modifier.fillMaxWidth().background(Color(0x9907080A)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Back", color = Cream, fontFamily = outfit, modifier = Modifier.clickable(onClick = onBack))
                Spacer(Modifier.width(18.dp))
                Text(engine.title, color = Cream, fontFamily = outfit, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(if (engine.playing) "Pause" else "Play", color = Cream, fontFamily = outfit, modifier = Modifier.clickable { engine.toggle() })
                Spacer(Modifier.width(18.dp))
                Text("Adjust", color = Tungsten, fontFamily = outfit, modifier = Modifier.clickable(onClick = onAdjust))
                Spacer(Modifier.width(18.dp))
                Text("Full", color = Cream, fontFamily = outfit, modifier = Modifier.clickable(onClick = onFull))
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC07080A)).padding(16.dp)) {
                Slider(
                    value = if (engine.durationMs > 0) engine.positionMs / engine.durationMs.toFloat() else 0f,
                    onValueChange = { engine.seekTo((it * engine.durationMs).toLong()) },
                    onValueChangeFinished = {},
                )
                Text("${formatTime(engine.positionMs)}   ${formatTime(engine.durationMs)}", color = CreamDim, fontFamily = outfit, fontSize = 12.sp)
            }
        }
        if (adjust) {
            Column(
                Modifier.align(Alignment.CenterEnd).width(320.dp).fillMaxSize().background(Color(0xF014161C)).verticalScroll(rememberScrollState()).padding(18.dp),
            ) {
                Text("ADJUST", color = CreamDim, fontFamily = outfit, fontSize = 12.sp, letterSpacing = 1.6.sp)
                Spacer(Modifier.height(12.dp))
                LabeledSlider("Speed", engine.rate, 0.5f, 2f, outfit) { engine.setRate(it) }
                LabeledSlider("Volume", engine.volume / 150f, 0f, 1f, outfit) { engine.setVolume((it * 150).toInt()) }
                LabeledSlider("Brightness", engine.brightness, 0.4f, 1.8f, outfit) { engine.setPicture(it, engine.contrast, engine.saturation, engine.gamma) }
                LabeledSlider("Contrast", engine.contrast, 0.4f, 1.8f, outfit) { engine.setPicture(engine.brightness, it, engine.saturation, engine.gamma) }
                LabeledSlider("Saturation", engine.saturation, 0.2f, 2f, outfit) { engine.setPicture(engine.brightness, engine.contrast, it, engine.gamma) }
                LabeledSlider("Gamma", engine.gamma, 0.4f, 1.8f, outfit) { engine.setPicture(engine.brightness, engine.contrast, engine.saturation, it) }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietChip("Fit", engine.fit == "fit", outfit) { engine.setFit("fit") }
                    QuietChip("Fill", engine.fit == "fill", outfit) { engine.setFit("fill") }
                    QuietChip("Stretch", engine.fit == "stretch", outfit) { engine.setFit("stretch") }
                    QuietChip(if (engine.hw) "Hardware" else "Software", engine.hw, outfit) { engine.setHw(!engine.hw) }
                }
                Spacer(Modifier.height(14.dp))
                Text("Sound", color = Cream, fontFamily = outfit)
                engine.audioTracks.forEach { track ->
                    Text(track.description(), color = if (track.id() == engine.audioId) Tungsten else CreamDim, fontFamily = outfit, fontSize = 13.sp, modifier = Modifier.clickable { engine.setAudio(track.id()) }.padding(vertical = 4.dp))
                }
                Text("Subtitles", color = Cream, fontFamily = outfit, modifier = Modifier.padding(top = 8.dp))
                engine.spuTracks.forEach { track ->
                    Text(track.description(), color = if (track.id() == engine.spuId) Tungsten else CreamDim, fontFamily = outfit, fontSize = 13.sp, modifier = Modifier.clickable { engine.setSpu(track.id()) }.padding(vertical = 4.dp))
                }
                Text("Open a subtitle file", color = Tungsten, fontFamily = outfit, fontSize = 13.sp, modifier = Modifier.clickable {
                    // The window reference is not here. Subtitle picking is handled by a file dialog without a parent.
                    pick(null, false)?.firstOrNull()?.let(engine::addSubtitle)
                }.padding(vertical = 6.dp))
                if (engine.eqNames.isNotEmpty()) {
                    Text("Equalizer", color = Cream, fontFamily = outfit, modifier = Modifier.padding(top = 8.dp))
                    Text("Off", color = if (engine.eqName == null) Tungsten else CreamDim, fontFamily = outfit, fontSize = 13.sp, modifier = Modifier.clickable { engine.setEqualizer(null) }.padding(vertical = 3.dp))
                    engine.eqNames.take(12).forEach { name ->
                        Text(name, color = if (engine.eqName == name) Tungsten else CreamDim, fontFamily = outfit, fontSize = 13.sp, modifier = Modifier.clickable { engine.setEqualizer(name) }.padding(vertical = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, min: Float, max: Float, outfit: FontFamily, onChange: (Float) -> Unit) {
    Text(label, color = CreamDim, fontFamily = outfit, fontSize = 12.sp)
    Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
}

@Composable
private fun QuietChip(label: String, on: Boolean, outfit: FontFamily, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) Tungsten else InkRaised).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = if (on) Ink else Cream, fontFamily = outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

private fun unfinished(item: Desk): Boolean {
    return item.positionMs > 3_000 && (item.durationMs == 0L || item.positionMs < item.durationMs - 4_000)
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes / 1_048_576.0
    if (mb >= 1.0) return "%.0f MB".format(mb)
    return "${(bytes / 1024).coerceAtLeast(1)} KB"
}

private fun formatTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0) / 1000
    val s = safe % 60
    val m = (safe / 60) % 60
    val h = safe / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private data class Shelf(val name: String, val count: Int)

private fun libraryDir(file: File): String {
    val dir = file.parentFile?.absoluteFile ?: return ""
    val home = File(System.getProperty("user.home")).absoluteFile
    val dirPath = dir.toPath()
    val homePath = home.toPath()
    if (dirPath.startsWith(homePath)) return homePath.relativize(dirPath).toString().replace('\\', '/').trim('/')
    val abs = dir.absolutePath.replace('\\', '/')
    if (abs.startsWith("/Volumes/")) return abs.removePrefix("/Volumes/").trim('/')
    val root = dirPath.root ?: return dir.name
    val rest = root.relativize(dirPath).toString().replace('\\', '/').trim('/')
    val drive = root.toString().trim('/', '\\').ifEmpty { "Computer" }
    return if (rest.isEmpty()) drive else "$drive/$rest"
}

private fun inFolder(path: String, prefix: String): Boolean {
    val dir = path.trim('/')
    return dir == prefix || dir.startsWith("$prefix/")
}

private fun foldersHere(clips: List<DiskVideo>, stack: List<String>): List<Shelf> {
    val prefix = stack.joinToString("/")
    val counts = LinkedHashMap<String, Int>()
    for (clip in clips) {
        val rest = remainder(libraryDir(clip.file), prefix) ?: continue
        if (rest.isEmpty()) continue
        val name = rest.substringBefore('/')
        counts[name] = (counts[name] ?: 0) + 1
    }
    return counts.map { Shelf(it.key, it.value) }.sortedBy { it.name.lowercase() }
}

private fun clipsHere(clips: List<DiskVideo>, stack: List<String>): List<DiskVideo> {
    val prefix = stack.joinToString("/")
    return clips.filter { remainder(libraryDir(it.file), prefix) == "" }
}

private fun remainder(path: String, prefix: String): String? {
    val dir = path.trim('/')
    return when {
        prefix.isEmpty() -> dir
        dir == prefix -> ""
        dir.startsWith("$prefix/") -> dir.removePrefix("$prefix/")
        else -> null
    }
}

private fun sortClips(clips: List<DiskVideo>, mode: Int): List<DiskVideo> {
    return if (mode == 1) clips.sortedBy { it.file.name.lowercase() } else clips.sortedByDescending { it.modified }
}

private fun countLine(videos: Int, folders: Int): String {
    val parts = ArrayList<String>(2)
    if (folders > 0) parts += if (folders == 1) "1 folder" else "$folders folders"
    if (videos > 0) parts += if (videos == 1) "1 video" else "$videos videos"
    return parts.joinToString("  ·  ").ifEmpty { "Nothing here." }
}

private fun clipDetail(clip: DiskVideo, showFolder: Boolean): String {
    return buildString {
        if (showFolder) {
            val folder = clip.file.parentFile?.name
            if (!folder.isNullOrBlank()) append(folder)
        }
        if (isNotEmpty()) append("  ·  ")
        append(formatSize(clip.size))
    }
}

private fun pick(parent: java.awt.Frame?, multiple: Boolean): List<File>? {
    val dialog = FileDialog(parent, "Open", FileDialog.LOAD)
    dialog.isMultipleMode = multiple
    dialog.isVisible = true
    val files = if (multiple) dialog.files?.toList().orEmpty() else listOfNotNull(dialog.file?.let { File(dialog.directory, it) })
    return files.filter { it.exists() }.ifEmpty { null }
}
