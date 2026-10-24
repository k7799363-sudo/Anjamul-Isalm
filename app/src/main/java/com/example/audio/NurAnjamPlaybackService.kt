package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.R
import com.example.data.AudioArtworkExtractor
import com.example.model.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Playback Service with native MediaSessionCompat integration.
 * Connects directly to DualAudioMixerBridge to control Quran and ambient soundscapes.
 * Provides instant compatibility with the Realme Capsule, AOD, Lock Screen, and system notifications.
 */
class NurAnjamPlaybackService : Service() {

    interface MediaActionListener {
        fun onPlay()
        fun onPause()
        fun onTogglePlayPause()
        fun onSkipToNext()
        fun onSkipToPrevious()
        fun onSeekTo(positionMs: Long)
        fun onStop()
    }

    companion object {
        private const val TAG = "NurAnjamService"
        const val CHANNEL_ID = "nur_anjam_media_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_OR_UPDATE = "com.example.action.START_OR_UPDATE"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_PLAY_PAUSE = "com.example.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_PREVIOUS = "com.example.action.PREVIOUS"

        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_RECITER = "extra_reciter"
        const val EXTRA_ALBUM = "extra_album"
        const val EXTRA_DATA_URI = "extra_data_uri"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        var mediaActionListener: MediaActionListener? = null

        fun startOrUpdate(
            context: Context,
            track: AudioTrack,
            isPlaying: Boolean,
            positionMs: Long
        ) {
            try {
                val intent = Intent(context, NurAnjamPlaybackService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_TRACK_ID, track.id)
                    putExtra(EXTRA_TITLE, track.title)
                    putExtra(EXTRA_RECITER, track.reciter)
                    putExtra(EXTRA_ALBUM, track.album)
                    putExtra(EXTRA_DATA_URI, track.dataUri)
                    putExtra(EXTRA_DURATION_MS, track.durationMs)
                    putExtra(EXTRA_POSITION_MS, positionMs)
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start foreground service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, NurAnjamPlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot stop service: ${e.message}")
            }
        }
    }

    private var mediaSession: MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentArtworkBitmap: Bitmap? = null
    private var currentTrackId: String? = null
    private var currentTitle: String = "Nur Anjam Recitation"
    private var currentReciter: String = "Spiritual Sanctuary"
    private var currentAlbum: String = "Nur Anjam"
    private var currentDurationMs: Long = 0L
    private var isPlayingState: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()

        // Immediate startForeground call with default placeholder to guarantee
        // ForegroundServiceDidNotStartInTimeException is never thrown by Android OS.
        try {
            val initialNotification = buildMediaNotification(
                title = currentTitle,
                reciter = currentReciter,
                isPlaying = false,
                artBitmap = null
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initial startForeground: ${e.message}", e)
        }
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSessionCompat(this, "NurAnjamMediaSession").apply {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )

                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        dispatchPlay()
                    }

                    override fun onPause() {
                        dispatchPause()
                    }

                    override fun onSkipToNext() {
                        dispatchNext()
                    }

                    override fun onSkipToPrevious() {
                        dispatchPrevious()
                    }

                    override fun onSeekTo(pos: Long) {
                        dispatchSeekTo(pos)
                    }

                    override fun onStop() {
                        dispatchStop()
                    }
                })

                isActive = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaSession: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Guard: Ensure foreground notification remains active
            postForegroundNotification(currentTitle, currentReciter, isPlayingState, currentArtworkBitmap)
            return START_STICKY
        }

        when (intent.action) {
            ACTION_PLAY -> dispatchPlay()
            ACTION_PAUSE -> dispatchPause()
            ACTION_PLAY_PAUSE -> dispatchTogglePlayPause()
            ACTION_NEXT -> dispatchNext()
            ACTION_PREVIOUS -> dispatchPrevious()
            ACTION_STOP -> {
                dispatchStop()
                updatePlaybackState(false, 0L)
                mediaSession?.isActive = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_OR_UPDATE -> {
                handleStartOrUpdate(intent)
            }
        }

        return START_STICKY
    }

    private fun dispatchPlay() {
        mediaActionListener?.onPlay()
        DualAudioMixerBridge.onPlay()
    }

    private fun dispatchPause() {
        mediaActionListener?.onPause()
        DualAudioMixerBridge.onPause()
    }

    private fun dispatchTogglePlayPause() {
        mediaActionListener?.onTogglePlayPause()
        DualAudioMixerBridge.onTogglePlayPause()
    }

    private fun dispatchNext() {
        mediaActionListener?.onSkipToNext()
        DualAudioMixerBridge.onSkipToNext()
    }

    private fun dispatchPrevious() {
        mediaActionListener?.onSkipToPrevious()
        DualAudioMixerBridge.onSkipToPrevious()
    }

    private fun dispatchSeekTo(pos: Long) {
        mediaActionListener?.onSeekTo(pos)
        DualAudioMixerBridge.onSeekTo(pos)
    }

    private fun dispatchStop() {
        mediaActionListener?.onStop()
        DualAudioMixerBridge.onStop()
    }

    private fun handleStartOrUpdate(intent: Intent) {
        val trackId = intent.getStringExtra(EXTRA_TRACK_ID) ?: ""
        currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Quran Recitation"
        currentReciter = intent.getStringExtra(EXTRA_RECITER) ?: "Spiritual Reflection"
        currentAlbum = intent.getStringExtra(EXTRA_ALBUM) ?: "Nur Anjam"
        val dataUri = intent.getStringExtra(EXTRA_DATA_URI) ?: ""
        currentDurationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        val positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
        isPlayingState = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)

        mediaSession?.isActive = true

        // Update Playback State immediately for AOD / lockscreen
        updatePlaybackState(isPlayingState, positionMs)

        // Asynchronously extract and cache ID3 artwork for lockscreen & Realme Capsule
        if (trackId != currentTrackId) {
            currentTrackId = trackId
            currentArtworkBitmap = null

            serviceScope.launch {
                val dummyTrack = AudioTrack(
                    id = trackId,
                    title = currentTitle,
                    reciter = currentReciter,
                    durationMs = currentDurationMs,
                    dataUri = dataUri,
                    album = currentAlbum
                )
                val bitmap = AudioArtworkExtractor.getEmbeddedArtwork(applicationContext, dummyTrack)
                currentArtworkBitmap = bitmap ?: createDefaultArtwork(currentTitle)

                updateMediaMetadata(currentTitle, currentReciter, currentAlbum, currentDurationMs, currentArtworkBitmap)
                postForegroundNotification(currentTitle, currentReciter, isPlayingState, currentArtworkBitmap)
            }
        } else {
            updateMediaMetadata(currentTitle, currentReciter, currentAlbum, currentDurationMs, currentArtworkBitmap)
            postForegroundNotification(currentTitle, currentReciter, isPlayingState, currentArtworkBitmap)
        }
    }

    private fun updatePlaybackState(isPlaying: Boolean, positionMs: Long) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                state,
                positionMs,
                if (isPlaying) 1.0f else 0.0f,
                SystemClock.elapsedRealtime()
            )
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateMediaMetadata(
        title: String,
        reciter: String,
        album: String,
        durationMs: Long,
        artBitmap: Bitmap?
    ) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, reciter)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)

        if (artBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artBitmap)
        }

        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun postForegroundNotification(
        title: String,
        reciter: String,
        isPlaying: Boolean,
        artBitmap: Bitmap?
    ) {
        try {
            val notification = buildMediaNotification(title, reciter, isPlaying, artBitmap)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground notification: ${e.message}", e)
        }
    }

    private fun buildMediaNotification(
        title: String,
        reciter: String,
        isPlaying: Boolean,
        artBitmap: Bitmap?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NurAnjamPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPausePendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, NurAnjamPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, NurAnjamPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, NurAnjamPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionToken = mediaSession?.sessionToken

        val mediaStyle = MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPendingIntent)

        if (sessionToken != null) {
            mediaStyle.setMediaSession(sessionToken)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(reciter)
            .setSubText("NurAnjam Sanctuary")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (artBitmap != null) {
            builder.setLargeIcon(artBitmap)
        }

        // Action 0: Previous
        builder.addAction(
            R.drawable.ic_notification,
            "Previous",
            prevPendingIntent
        )

        // Action 1: Play / Pause
        builder.addAction(
            R.drawable.ic_notification,
            if (isPlaying) "Pause" else "Play",
            playPausePendingIntent
        )

        // Action 2: Next
        builder.addAction(
            R.drawable.ic_notification,
            "Next",
            nextPendingIntent
        )

        return builder.build()
    }

    private fun createDefaultArtwork(title: String): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = android.graphics.Color.rgb(10, 30, 24)
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val goldPaint = Paint().apply {
            color = android.graphics.Color.rgb(212, 175, 55)
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, goldPaint)

        return bitmap
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NurAnjam Active Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Enables Lock Screen Media Controls and Realme Dynamic Capsule audio status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved: App swiped away from Recents screen. isPlayingState=$isPlayingState")
        if (!isPlayingState) {
            Log.i(TAG, "Playback is paused/stopped. Stopping foreground service cleanly.")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (_: Exception) {}
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        mediaActionListener = null
    }
}
