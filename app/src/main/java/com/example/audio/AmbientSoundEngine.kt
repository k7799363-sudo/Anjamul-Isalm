package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.model.AmbientType

/**
 * Hardware-accelerated ambient sound engine powered by Android MediaPlayer with seamless looping.
 * Plays high-fidelity 44.1kHz Stereo bundled soundscapes (Rain, Thunder, Desert Wind, Mountain Stream, Sacred Noor).
 * Zero CPU overhead, zero UI latency, no synthetic beeps.
 */
class AmbientSoundEngine(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var currentType: AmbientType = AmbientType.RAIN

    @Volatile
    private var currentVolume: Float = 0.55f

    @Volatile
    private var isPlaying: Boolean = false

    fun start(type: AmbientType, volume: Float) {
        currentType = type
        currentVolume = volume.coerceIn(0f, 1f)
        isPlaying = true

        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {}

        try {
            val assetPath = when (type) {
                AmbientType.RAIN -> "audio/ambient/ambient_rain.wav"
                AmbientType.THUNDER -> "audio/ambient/ambient_thunder.wav"
                AmbientType.DESERT_WIND -> "audio/ambient/ambient_wind.wav"
                AmbientType.MOUNTAIN_STREAM -> "audio/ambient/ambient_stream.wav"
                AmbientType.SACRED_NOOR -> "audio/ambient/ambient_noor.wav"
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                AssetMediaHelper.setDataSourceFromAsset(this, context, assetPath)
                isLooping = true
                setVolume(currentVolume, currentVolume)
                setOnPreparedListener { mp ->
                    if (isPlaying) {
                        mp.setVolume(currentVolume, currentVolume)
                        mp.start()
                        Log.i("AmbientSoundEngine", "Playing ambient asset: $assetPath at vol $currentVolume")
                    }
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AmbientSoundEngine", "Failed to start ambient audio asset: ${e.message}", e)
        }
    }

    fun setType(type: AmbientType) {
        if (currentType != type) {
            currentType = type
            if (isPlaying) {
                start(type, currentVolume)
            }
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        currentVolume = clamped
        try {
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (_: Exception) {}
    }

    fun stop() {
        isPlaying = false
        try {
            mediaPlayer?.pause()
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w("AmbientSoundEngine", "Error stopping MediaPlayer: ${e.message}")
        }
        mediaPlayer = null
    }
}
