package app.lumen.player.playback

enum class Phase { Library, Player }

enum class RepeatMode { Off, All, One }

data class TrackChoice(val id: Int, val name: String)

data class ChapterMark(val name: String, val timeMs: Long)

data class QueueItem(val uri: String, val title: String)

data class RecentItem(
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val openedAt: Long,
)

data class PlayerUiState(
    val phase: Phase = Phase.Library,
    val title: String = "",
    val uri: String? = null,
    val playing: Boolean = false,
    val opening: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val buffering: Float = 100f,
    val error: String? = null,
    val audioTracks: List<TrackChoice> = emptyList(),
    val spuTracks: List<TrackChoice> = emptyList(),
    val audioTrackId: Int = -1,
    val spuTrackId: Int = -1,
    val rate: Float = 1f,
    val boosting: Boolean = false,
    val audioDelayMs: Long = 0,
    val spuDelayMs: Long = 0,
    val scaleName: String = "fit",
    val zoom: Float = 1f,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val gamma: Float = 1f,
    val rotation: Int = 0,
    val deinterlace: String = "off",
    val subSize: Int = 20,
    val subColor: Int = 0xFFFFFF,
    val hw: Boolean = true,
    val videoInfo: String = "",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val hasVideo: Boolean = true,
    val chapters: List<ChapterMark> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
    val queueIndex: Int = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val abA: Long? = null,
    val abB: Long? = null,
    val sleepUntil: Long? = null,
    val stopAtEnd: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPreset: Int = -1,
    val eqNames: List<String> = emptyList(),
    val eqFreqs: List<Int> = emptyList(),
    val eqAmps: List<Float> = emptyList(),
    val eqPreamp: Float = 0f,
    val recents: List<RecentItem> = emptyList(),
    val pipOnLeave: Boolean = true,
    val resumeHintMs: Long = 0,
)
