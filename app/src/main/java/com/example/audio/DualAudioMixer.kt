package com.example.audio

import android.content.Context
import com.example.model.AmbientType
import com.example.model.AudioOutputDevice
import com.example.model.AudioPreset
import com.example.model.AudioTrack
import com.example.model.PlaybackState
import kotlinx.coroutines.flow.StateFlow

/**
 * DualAudioMixer delegates to the unified Media3 DualAudioMixerBridge,
 * maintaining clean backward compatibility with ViewModel while utilizing
 * the synchronized dual ExoPlayer background engine.
 */
class DualAudioMixer(context: Context) {

    private val bridge = DualAudioMixerBridge.getInstance(context)

    val state: StateFlow<PlaybackState> = bridge.state
    val outputDetector: AudioOutputDetector = bridge.outputDetector

    fun setPlaylist(tracks: List<AudioTrack>, startIndex: Int = 0, autoPlay: Boolean = false) {
        bridge.setPlaylist(tracks, startIndex, autoPlay)
    }

    fun playTrack(track: AudioTrack) {
        bridge.playTrack(track)
    }

    fun togglePlayPause() {
        bridge.onTogglePlayPause()
    }

    fun pauseAll() {
        bridge.onPause()
    }

    fun seekTo(positionMs: Long) {
        bridge.onSeekTo(positionMs)
    }

    fun playNext() {
        bridge.onSkipToNext()
    }

    fun playPrevious() {
        bridge.onSkipToPrevious()
    }

    fun toggleShuffle() {
        bridge.toggleShuffle()
    }

    fun cycleRepeatMode() {
        bridge.cycleRepeatMode()
    }

    fun cyclePlaybackMode(): com.example.model.PlaybackMode {
        return bridge.cyclePlaybackMode()
    }

    fun setPlaybackMode(mode: com.example.model.PlaybackMode) {
        bridge.setPlaybackMode(mode)
    }

    fun setPlaybackSpeed(speed: Float) {
        bridge.setPlaybackSpeed(speed)
    }

    fun setRecitationVolume(volume: Float) {
        bridge.setRecitationVolume(volume)
    }

    fun setAmbientVolume(volume: Float) {
        bridge.setAmbientVolume(volume)
    }

    fun setAmbientType(type: AmbientType) {
        bridge.setAmbientType(type)
    }

    fun selectAmbient(type: AmbientType) {
        bridge.selectAmbient(type)
    }

    fun toggleAmbientPlayback() {
        bridge.toggleAmbientPlayback()
    }

    fun setAtmosphereEnabled(enabled: Boolean) {
        bridge.setAtmosphereEnabled(enabled)
    }

    fun playAmbientPreview(uriOrPath: String, soundId: String, isOnline: Boolean, title: String? = null) {
        bridge.playAmbientPreview(uriOrPath, soundId, isOnline, title)
    }

    fun stopAmbientPreview() {
        bridge.stopAmbientPreview()
    }

    fun applyPresetMix(recitationVol: Float, ambientVol: Float, ambient: AmbientType) {
        bridge.applyPresetMix(recitationVol, ambientVol, ambient)
    }

    fun setAudioPreset(preset: AudioPreset) {
        bridge.setAudioPreset(preset)
    }

    fun setSleepTimer(minutes: Int) {
        bridge.setSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        bridge.cancelSleepTimer()
    }

    fun removeTrackFromQueue(trackId: String) {
        bridge.removeTrackFromQueue(trackId)
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        bridge.reorderQueue(fromIndex, toIndex)
    }

    fun playTrackNext(track: AudioTrack) {
        bridge.playTrackNext(track)
    }

    fun addToQueueEnd(track: AudioTrack) {
        bridge.addToQueueEnd(track)
    }

    fun refreshAudioOutput() {
        outputDetector.refresh()
    }

    fun simulateAudioDevice(device: AudioOutputDevice?) {
        outputDetector.simulateDevice(device)
    }

    fun setProgressTrackingActive(active: Boolean) {
        bridge.setProgressTrackingActive(active)
    }

    fun updateTrackFavoriteState(trackId: String, isFavorite: Boolean) {
        bridge.updateTrackFavoriteState(trackId, isFavorite)
    }

    fun updateTrackDate(trackId: String, newTimestamp: Long) {
        bridge.updateTrackDate(trackId, newTimestamp)
    }

    fun release() {
        bridge.release()
    }
}
