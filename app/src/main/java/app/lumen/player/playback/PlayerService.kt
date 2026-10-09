package app.lumen.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import app.lumen.player.MainActivity
import app.lumen.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlayerService : Service() {
    lateinit var engine: PlayerEngine
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSessionCompat
    private var lastNotifKey = ""
    private var inForeground = false
    private var noisyRegistered = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                engine.pause()
            }
        }
    }

    inner class LocalBinder : Binder() {
        val service: PlayerService get() = this@PlayerService
    }

    override fun onCreate() {
        super.onCreate()
        engine = PlayerEngine(this)
        createChannel()
        session = MediaSessionCompat(this, "Velo").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = engine.play()
                override fun onPause() = engine.pause()
                override fun onSeekTo(pos: Long) = engine.seekTo(pos)
                override fun onSkipToNext() = engine.next()
                override fun onSkipToPrevious() = engine.previous()
                override fun onStop() = engine.closeToLibrary()
            })
            isActive = true
        }
        scope.launch {
            engine.state.collect { state ->
                publishSession(state)
                val key = "${state.phase}|${state.playing}|${state.title}|${state.queueIndex}"
                if (key != lastNotifKey) {
                    lastNotifKey = key
                    if (state.phase == Phase.Player) {
                        runCatching { goForeground(state) }
                        inForeground = true
                    } else if (inForeground) {
                        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                        inForeground = false
                    }
                }
                syncNoisy(state.playing)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> engine.toggle()
            ACTION_NEXT -> engine.next()
            ACTION_PREV -> engine.previous()
            ACTION_STOP -> {
                engine.closeToLibrary()
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                inForeground = false
                return START_NOT_STICKY
            }
        }
        val state = engine.state.value
        val shown = if (state.title.isBlank()) state.copy(title = "Velo") else state
        runCatching { goForeground(shown) }
        inForeground = true
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onDestroy() {
        if (noisyRegistered) {
            runCatching { unregisterReceiver(noisy) }
            noisyRegistered = false
        }
        session.isActive = false
        session.release()
        engine.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun syncNoisy(playing: Boolean) {
        if (playing && !noisyRegistered) {
            val filter = IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(noisy, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(noisy, filter)
            }
            noisyRegistered = true
        }
    }

    private fun goForeground(state: PlayerUiState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(state: PlayerUiState): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = pending(2, ACTION_TOGGLE)
        val prev = pending(3, ACTION_PREV)
        val next = pending(4, ACTION_NEXT)
        val stop = pending(5, ACTION_STOP)
        val playIcon = if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playLabel = if (state.playing) "Pause" else "Play"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(state.title.ifBlank { "Velo" })
            .setContentText(if (state.playing) "Playing" else "Paused")
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prev)
            .addAction(playIcon, playLabel, toggle)
            .addAction(android.R.drawable.ic_media_next, "Next", next)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stop)
            .setOngoing(state.playing)
            .build()
    }

    private fun pending(code: Int, action: String): PendingIntent {
        val intent = Intent(this, PlayerService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun publishSession(state: PlayerUiState) {
        val playback = if (state.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .setState(playback, state.positionMs, if (state.boosting) 2f else state.rate)
                .build(),
        )
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
                .build(),
        )
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Shows what Velo is playing"
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL = "playback"
        const val NOTIF_ID = 41
        const val ACTION_TOGGLE = "app.lumen.player.TOGGLE"
        const val ACTION_NEXT = "app.lumen.player.NEXT"
        const val ACTION_PREV = "app.lumen.player.PREV"
        const val ACTION_STOP = "app.lumen.player.STOP"
    }
}
