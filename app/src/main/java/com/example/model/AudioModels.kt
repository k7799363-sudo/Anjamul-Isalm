package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AmbientNoorGold
import com.example.ui.theme.AmbientRainBlue
import com.example.ui.theme.AmbientStreamTeal
import com.example.ui.theme.AmbientThunderPurple
import com.example.ui.theme.AmbientWindAmber

data class LyricLine(
    val timestampMs: Long,
    val arabic: String,
    val transliteration: String,
    val translation: String
)

data class AudioTrack(
    val id: String,
    val title: String,
    val reciter: String,
    val durationMs: Long,
    val dataUri: String,
    val album: String = "Spiritual Recitations",
    val folderName: String = "Religious",
    val sizeBytes: Long = 0L,
    val bitrateKbps: Int = 320,
    val sampleRateHz: Int = 44100,
    val isFavorite: Boolean = false,
    val surahNumber: Int? = null,
    val ayahCount: Int? = null,
    val arabicTitle: String? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val dateAdded: Long = System.currentTimeMillis(),
    val dateAddedMs: Long = dateAdded,
    var customSortTimestamp: Long = dateAdded,
    val playCount: Int = 0,
    var lastPlayedTimestamp: Long = 0L
)

enum class AudioSortOrder(
    val title: String,
    val subtitle: String
) {
    TITLE_AZ(
        title = "Alphabetical (A → Z)",
        subtitle = "Sort alphabetically by track title"
    ),
    TITLE_ZA(
        title = "Alphabetical (Z → A)",
        subtitle = "Sort reverse alphabetically by title"
    ),
    DATE_NEWEST(
        title = "Chronological (Newest First)",
        subtitle = "Recently added or imported audio files"
    ),
    DATE_OLDEST(
        title = "Chronological (Oldest First)",
        subtitle = "Earliest added audio tracks"
    ),
    MOST_PLAYED(
        title = "Listening Frequency (Most Played)",
        subtitle = "Tracks with highest listening count"
    )
}

enum class AmbientType(
    val displayName: String,
    val description: String,
    val accentColor: Color
) {
    RAIN(
        displayName = "Gentle Rain",
        description = "Calming raindrops pattering softly on leaves",
        accentColor = AmbientRainBlue
    ),
    THUNDER(
        displayName = "Distant Thunder",
        description = "Deep soothing atmospheric rolling rumble",
        accentColor = AmbientThunderPurple
    ),
    DESERT_WIND(
        displayName = "Nocturnal Breeze",
        description = "Serene warm wind sweeping through the dunes",
        accentColor = AmbientWindAmber
    ),
    MOUNTAIN_STREAM(
        displayName = "Sacred Stream",
        description = "Crystal spring water flowing over pebbles",
        accentColor = AmbientStreamTeal
    ),
    SACRED_NOOR(
        displayName = "Deep Noor",
        description = "Warm 432Hz harmonic acoustic resonance",
        accentColor = AmbientNoorGold
    )
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class PlaybackMode(val title: String, val description: String) {
    LOOP_TRACK("Loop Track", "Replays the current recitation continuously"),
    LOOP_FOLDER("Loop Folder", "Plays current folder sequentially respecting active sort order, then loops"),
    SHUFFLE_FOLDER("Shuffle Folder", "Randomizes queue strictly within the active folder"),
    FREQUENCY_PLAY("Most Played First", "Orders active folder tracks by historical play count")
}

enum class AudioPreset(val title: String, val subtitle: String) {
    NORMAL("Natural Audio", "Balanced studio frequency response"),
    SPIRITUAL_REVERB("Sacred Sanctuary", "Spacious acoustic reverbs for deep presence"),
    VOCAL_CLARITY("Crystal Tajweed", "Clear articulation of sacred letters & tajweed"),
    DEEP_BASS("Subtle Resonance", "Warm grounded lower frequencies")
}

enum class AudioOutputType(val defaultLabel: String) {
    PHONE_SPEAKER("Phone Speaker"),
    BLUETOOTH_EARBUDS("Wireless Earbuds"),
    BLUETOOTH_HEADPHONES("Bluetooth Headphones"),
    BLUETOOTH_SPEAKER("Bluetooth Speaker"),
    WIRED_HEADPHONES("Wired Headphones"),
    USB_AUDIO("USB Audio Device"),
    OTHER("External Audio")
}

data class AudioOutputDevice(
    val type: AudioOutputType = AudioOutputType.PHONE_SPEAKER,
    val name: String = "Phone Speaker",
    val isWireless: Boolean = false,
    val description: String = "Internal phone speakers"
)

enum class AmbientPlayType {
    NONE,
    PRESET_ATMOSPHERE,
    ONLINE_PREVIEW,
    DOWNLOADED_PREVIEW
}

data class PlaybackState(
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val playbackMode: PlaybackMode = PlaybackMode.LOOP_FOLDER,
    val recitationVolume: Float = 0.85f,
    val ambientVolume: Float = 0.0f,
    val selectedAmbient: AmbientType = AmbientType.RAIN,
    val isAmbientPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val audioPreset: AudioPreset = AudioPreset.SPIRITUAL_REVERB,
    val sleepTimerRemainingSec: Int? = null,
    val isSleepTimerActive: Boolean = false,
    val isFadingOut: Boolean = false,
    val audioOutputDevice: AudioOutputDevice = AudioOutputDevice(),
    val strictVolumeControl: Boolean = true,
    val activeAmbientType: AmbientPlayType = AmbientPlayType.NONE,
    val activePreviewSoundId: String? = null,
    val activeAmbientTitle: String? = null,
    val activeAmbientFilePath: String? = null,
    val playQueue: List<AudioTrack> = emptyList(),
    val currentMediaItemIndex: Int = 0
)

enum class LibraryTab(val title: String) {
    TRACKS("Tracks"),
    FOLDERS("Folders"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artists")
}

enum class PlayerViewMode {
    COVER,
    LYRIC,
    AMBIENT_MIXER
}
