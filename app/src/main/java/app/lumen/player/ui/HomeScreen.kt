package app.lumen.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.playback.RecentItem

@Composable
fun HomeScreen(
    recents: List<RecentItem>,
    onOpen: () -> Unit,
    onRecent: (RecentItem) -> Unit,
) {
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
                "LUMEN",
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
        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(40.dp))
            Text(
                "CONTINUE",
                color = CreamDim,
                fontFamily = Outfit,
                fontWeight = FontWeight(620),
                fontSize = 12.sp,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(12.dp))
            recents.forEach { item ->
                RecentRow(item, onRecent)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RecentRow(item: RecentItem, onRecent: (RecentItem) -> Unit) {
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
            .clickable { onRecent(item) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            item.title,
            color = Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight(520),
            fontSize = 16.sp,
            maxLines = 1,
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
