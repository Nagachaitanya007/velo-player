package app.lumen.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.PhoneVideo
import app.lumen.player.playback.RecentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PosterWash = Color(0xFF101218)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    recents: List<RecentItem>,
    videos: List<PhoneVideo>,
    scanning: Boolean,
    canSeeVideos: Boolean,
    unfinishedOnly: Boolean,
    sortByName: Boolean,
    onOpen: () -> Unit,
    onAllow: () -> Unit,
    onRefresh: () -> Unit,
    onVideo: (PhoneVideo) -> Unit,
    onVideoFromStart: (PhoneVideo) -> Unit,
    onRecent: (RecentItem) -> Unit,
    onFromStart: (RecentItem) -> Unit,
    onResumeLast: (RecentItem) -> Unit,
    onForget: (RecentItem) -> Unit,
    onClear: () -> Unit,
    onToggleUnfinished: () -> Unit,
    onToggleSort: () -> Unit,
    onResetLook: () -> Unit,
) {
    var armClear by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var librarySort by remember { mutableIntStateOf(0) }
    var asGrid by remember { mutableStateOf(false) }
    var stack by remember { mutableStateOf(listOf<String>()) }
    val resume = recents.firstOrNull { unfinished(it) }
    val shown = recents
        .let { list -> if (unfinishedOnly) list.filter { unfinished(it) } else list }
        .let { list -> if (sortByName) list.sortedBy { it.title.lowercase() } else list }
    val needle = query.trim()
    val searching = needle.isNotEmpty()
    LaunchedEffect(videos, stack) {
        val prefix = stack.joinToString("/")
        val alive = prefix.isEmpty() || videos.any { inFolder(it.path, prefix) }
        if (!alive) stack = emptyList()
    }
    val opened = if (searching) emptyList() else stack
    val shelves = if (searching) emptyList() else foldersHere(videos, opened)
    val loose = sortVideos(
        if (searching) {
            videos.filter {
                it.title.contains(needle, true) || it.folder.contains(needle, true) || it.path.contains(needle, true)
            }
        } else {
            videosHere(videos, opened)
        },
        librarySort,
    )
    val recentByUri = remember(recents) { recents.associateBy { it.uri } }
    val libraryLabel = when {
        scanning && videos.isEmpty() -> "Looking through this phone…"
        searching && loose.isEmpty() -> "Nothing matches."
        videos.isEmpty() -> "No videos on this phone yet."
        searching -> countLine(loose.size, 0)
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
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Tungsten),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "VELO",
                    color = CreamDim,
                    fontFamily = Outfit,
                    fontWeight = FontWeight(620),
                    fontSize = 13.sp,
                    letterSpacing = 2.4.sp,
                )
            }
            Spacer(Modifier.height(36.dp))
            Text(
                "Anything this phone\ncan decode.",
                style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (canSeeVideos) {
                    "Videos already on this phone are listed here. Picture, sound, and subtitles stay out of the way until you ask."
                } else {
                    "Velo can list the videos on this phone, so you don't have to hunt through Files."
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            )
            if (resume != null) {
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Tungsten)
                        .clickable { onResumeLast(resume) }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "Resume  ·  ${resume.title}",
                        color = Ink,
                        fontFamily = Outfit,
                        fontWeight = FontWeight(620),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (recents.isNotEmpty()) {
                    QuietChip("Unfinished", unfinishedOnly, onToggleUnfinished)
                    QuietChip(if (sortByName) "A–Z" else "Newest", sortByName, onToggleSort)
                    QuietChip(if (armClear) "Erase the list" else "Clear history", armClear) {
                        if (armClear) {
                            armClear = false
                            onClear()
                        } else {
                            armClear = true
                        }
                    }
                }
                QuietChip("Reset look", false, onResetLook)
                QuietChip("Browse files", false, onOpen)
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!searching && opened.isNotEmpty()) {
                    Text(
                        "Back",
                        color = Tungsten,
                        fontFamily = Outfit,
                        fontWeight = FontWeight(620),
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { stack = stack.dropLast(1) },
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        opened.last(),
                        color = Cream,
                        fontFamily = Outfit,
                        fontWeight = FontWeight(620),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        if (searching) "SEARCH" else "ON THIS PHONE",
                        color = CreamDim,
                        fontFamily = Outfit,
                        fontWeight = FontWeight(620),
                        fontSize = 12.sp,
                        letterSpacing = 1.6.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!canSeeVideos) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Tungsten)
                        .clickable(onClick = onAllow),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Show videos on this phone", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android asks once. Velo only reads video files.",
                    color = CreamDim,
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                )
            } else {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Cream, fontFamily = Outfit, fontSize = 15.sp),
                    cursorBrush = SolidColor(Tungsten),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(InkRaised)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            if (query.isEmpty()) {
                                Text("Search videos", color = CreamDim, fontFamily = Outfit, fontSize = 15.sp)
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuietChip("Newest", librarySort == 0) { librarySort = 0 }
                    QuietChip("A–Z", librarySort == 1) { librarySort = 1 }
                    QuietChip(if (asGrid) "Grid" else "Rows", asGrid) { asGrid = !asGrid }
                    QuietChip("Refresh", false, onRefresh)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    libraryLabel,
                    color = CreamDim,
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            }
        }
        if (canSeeVideos && !searching) {
            gridItems(shelves, key = { "dir:${opened.joinToString("/")}/${it.name}" }, span = { GridItemSpan(maxLineSpan) }) { shelf ->
                FolderRow(shelf.name, shelf.count) { stack = opened + shelf.name }
            }
        }
        if (canSeeVideos) {
            if (asGrid) {
                gridItems(loose, key = { it.uri }) { video ->
                    VideoCell(video, recentByUri[video.uri], onVideo)
                }
            } else {
                gridItems(loose, key = { it.uri }, span = { GridItemSpan(maxLineSpan) }) { video ->
                    VideoRow(video, recentByUri[video.uri], searching, onVideo, onVideoFromStart)
                }
            }
        }
        if (recents.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "CONTINUE",
                        color = CreamDim,
                        fontFamily = Outfit,
                        fontWeight = FontWeight(620),
                        fontSize = 12.sp,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (shown.isEmpty()) {
                        Text(
                            "Nothing unfinished.",
                            color = CreamDim,
                            fontFamily = Outfit,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            gridItems(shown, key = { "recent-${it.uri}" }, span = { GridItemSpan(maxLineSpan) }) { item ->
                RecentRow(item, onRecent, onFromStart, onForget)
            }
        }
        }
    }
}

@Composable
private fun QuietChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) Tungsten else InkRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (on) Ink else Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight(520),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun VideoRow(
    video: PhoneVideo,
    recent: RecentItem?,
    showFolder: Boolean,
    onVideo: (PhoneVideo) -> Unit,
    onFromStart: (PhoneVideo) -> Unit,
) {
    val progress = if (recent != null && recent.durationMs > 0) {
        (recent.positionMs.toFloat() / recent.durationMs).coerceIn(0f, 1f)
    } else 0f
    val detail = videoDetail(video, showFolder)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkRaised)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(
                video.uri,
                Modifier
                    .size(112.dp, 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onVideo(video) },
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).clickable { onVideo(video) }) {
                Text(
                    video.title,
                    color = Cream,
                    fontFamily = Outfit,
                    fontWeight = FontWeight(520),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        detail,
                        color = CreamDim,
                        fontFamily = Outfit,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress > 0.02f && progress < 0.97f) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Hairline),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .background(Tungsten),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "From start",
            color = Tungsten,
            fontFamily = Outfit,
            fontWeight = FontWeight(520),
            fontSize = 13.sp,
            modifier = Modifier.clickable { onFromStart(video) },
        )
    }
}

@Composable
private fun VideoCell(
    video: PhoneVideo,
    recent: RecentItem?,
    onVideo: (PhoneVideo) -> Unit,
) {
    val progress = if (recent != null && recent.durationMs > 0) {
        (recent.positionMs.toFloat() / recent.durationMs).coerceIn(0f, 1f)
    } else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkRaised)
            .clickable { onVideo(video) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(PosterWash),
        ) {
            Thumb(video.uri, Modifier.fillMaxSize())
            if (progress > 0.02f && progress < 0.97f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Hairline),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(Tungsten),
                    )
                }
            }
        }
        Text(
            video.title,
            color = Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight(520),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FolderRow(name: String, count: Int, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkRaised)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            name,
            color = Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight(520),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (count == 1) "1 video" else "$count videos",
            color = CreamDim,
            fontFamily = Outfit,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun RecentRow(
    item: RecentItem,
    onRecent: (RecentItem) -> Unit,
    onFromStart: (RecentItem) -> Unit,
    onForget: (RecentItem) -> Unit,
) {
    val progress = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
    val remain = (item.durationMs - item.positionMs).coerceAtLeast(0)
    val detail = when {
        item.durationMs <= 0 -> "Open"
        progress < 0.02f -> formatTime(item.durationMs)
        progress > 0.97f -> "Finished"
        else -> "${formatTime(remain)} left"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkRaised)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(
                item.uri,
                Modifier
                    .size(112.dp, 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRecent(item) },
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).clickable { onRecent(item) }) {
                Text(
                    item.title,
                    color = Cream,
                    fontFamily = Outfit,
                    fontWeight = FontWeight(520),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Hairline),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .background(Tungsten),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(detail, color = CreamDim, fontFamily = Outfit, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Text(
                "From start",
                color = Tungsten,
                fontFamily = Outfit,
                fontWeight = FontWeight(520),
                fontSize = 13.sp,
                modifier = Modifier.clickable { onFromStart(item) },
            )
            Spacer(Modifier.width(18.dp))
            Text(
                "Remove",
                color = CreamDim,
                fontFamily = Outfit,
                fontWeight = FontWeight(520),
                fontSize = 13.sp,
                modifier = Modifier.clickable { onForget(item) },
            )
        }
    }
}

@Composable
private fun Thumb(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<ImageBitmap?>(VideoThumbs.peek(uri)) }
    LaunchedEffect(uri) {
        if (image != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching { VideoThumbs.load(context, android.net.Uri.parse(uri)) }.getOrNull()
        }
        if (loaded != null) image = loaded
    }
    Box(modifier.background(PosterWash)) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun unfinished(item: RecentItem): Boolean {
    return item.positionMs > 3_000 && (item.durationMs == 0L || item.positionMs < item.durationMs - 4_000)
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes / 1_048_576.0
    if (mb >= 1.0) return "%.0f MB".format(mb)
    return "${(bytes / 1024).coerceAtLeast(1)} KB"
}

private data class Shelf(val name: String, val count: Int)

private fun inFolder(path: String, prefix: String): Boolean {
    val dir = path.trim('/')
    return dir == prefix || dir.startsWith("$prefix/")
}

private fun foldersHere(videos: List<PhoneVideo>, stack: List<String>): List<Shelf> {
    val prefix = stack.joinToString("/")
    val counts = LinkedHashMap<String, Int>()
    for (video in videos) {
        val rest = remainder(video.path, prefix) ?: continue
        if (rest.isEmpty()) continue
        val name = rest.substringBefore('/')
        counts[name] = (counts[name] ?: 0) + 1
    }
    return counts.map { Shelf(it.key, it.value) }.sortedBy { it.name.lowercase() }
}

private fun videosHere(videos: List<PhoneVideo>, stack: List<String>): List<PhoneVideo> {
    val prefix = stack.joinToString("/")
    return videos.filter { remainder(it.path, prefix) == "" }
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

private fun sortVideos(videos: List<PhoneVideo>, mode: Int): List<PhoneVideo> {
    return if (mode == 1) videos.sortedBy { it.title.lowercase() } else videos.sortedByDescending { it.addedMs }
}

private fun countLine(videos: Int, folders: Int): String {
    val parts = ArrayList<String>(2)
    if (folders > 0) parts += if (folders == 1) "1 folder" else "$folders folders"
    if (videos > 0) parts += if (videos == 1) "1 video" else "$videos videos"
    return parts.joinToString("  ·  ").ifEmpty { "Nothing here." }
}

private fun videoDetail(video: PhoneVideo, showFolder: Boolean): String {
    return buildString {
        if (showFolder && video.folder.isNotBlank()) append(video.folder)
        if (video.durationMs > 0) {
            if (isNotEmpty()) append("  ·  ")
            append(formatTime(video.durationMs))
        }
        if (video.sizeBytes > 0) {
            if (isNotEmpty()) append("  ·  ")
            append(formatSize(video.sizeBytes))
        }
    }
}
