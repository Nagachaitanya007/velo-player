package app.lumen.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.playback.RecentItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    recents: List<RecentItem>,
    unfinishedOnly: Boolean,
    sortByName: Boolean,
    onOpen: () -> Unit,
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
    val resume = recents.firstOrNull { unfinished(it) }
    val shown = recents
        .let { list -> if (unfinishedOnly) list.filter { unfinished(it) } else list }
        .let { list -> if (sortByName) list.sortedBy { it.title.lowercase() } else list }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
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
            "Open a video. Picture, sound, and subtitles stay out of the way until you ask.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Tungsten)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Text("Open a video", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        }
        if (resume != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(InkRaised)
                    .clickable { onResumeLast(resume) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "Resume  ·  ${resume.title}",
                    color = Cream,
                    fontFamily = Outfit,
                    fontWeight = FontWeight(520),
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
        }
        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(36.dp))
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
            shown.forEach { item ->
                RecentRow(item, onRecent, onFromStart, onForget)
                Spacer(Modifier.height(8.dp))
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.clickable { onRecent(item) }) {
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

private fun unfinished(item: RecentItem): Boolean {
    return item.positionMs > 3_000 && (item.durationMs == 0L || item.positionMs < item.durationMs - 4_000)
}
