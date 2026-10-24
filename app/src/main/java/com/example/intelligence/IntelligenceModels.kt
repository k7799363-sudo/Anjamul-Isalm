package com.example.intelligence

import com.example.model.AudioTrack

data class ListeningStatsSummary(
    val todayListeningMs: Long = 0L,
    val weekListeningMs: Long = 0L,
    val monthListeningMs: Long = 0L,
    val todaySongsCount: Int = 0,
    val weekSongsCount: Int = 0,
    val monthSongsCount: Int = 0,
    val todayUniqueCount: Int = 0,
    val topTrackToday: AudioTrack? = null,
    val mostActiveHourRange: String = "8 PM – 11 PM",
    val topArtistToday: String = "Various Reciters",
    val completionRateToday: Float = 0f
)

data class TopTrackInsight(
    val rank: Int,
    val track: AudioTrack,
    val playCount: Int,
    val listeningTimeMs: Long,
    val completionRate: Float,
    val weeklyPlayCount: Int,
    val monthlyPlayCount: Int,
    val dailyAveragePlays: Float
)

data class PersonalMusicProfile(
    val frequentlyListenedTags: List<String> = emptyList(),
    val preferredArtists: List<String> = emptyList(),
    val listeningPatternPeriod: String = "Mostly Evening",
    val discoveryTendency: String = "Balanced Listener",
    val mostLikedTrack: AudioTrack? = null,
    val mostLikedSummary: String = ""
)

enum class RecommendationType {
    BECAUSE_YOU_LISTEN,
    YOUR_FAVORITES,
    REDISCOVER,
    USUAL_TIME_OF_DAY,
    CONTINUE_EXPLORING,
    HIDDEN_GEMS
}

data class RecommendationGroup(
    val type: RecommendationType,
    val title: String,
    val subtitle: String,
    val tracks: List<AudioTrack>
)

data class DailyReportData(
    val dateLabel: String,
    val listeningTimeFormatted: String,
    val songsPlayed: Int,
    val uniqueSongs: Int,
    val topSongTitle: String,
    val topSongPlays: Int,
    val mostActivePeriod: String,
    val topArtist: String,
    val completionRateFormatted: String
)

data class WeeklyReportData(
    val totalListeningFormatted: String,
    val songsPlayed: Int,
    val uniqueSongs: Int,
    val mostPlayedSong: String,
    val mostPlayedArtist: String,
    val topGenre: String,
    val averageDailyListeningFormatted: String,
    val mostActiveDayName: String,
    val dailyListeningList: List<Pair<String, Long>> = emptyList()
)

data class MonthlyReportData(
    val totalListeningFormatted: String,
    val songsPlayed: Int,
    val uniqueSongs: Int,
    val topSong: String,
    val topArtist: String,
    val topAlbum: String,
    val topGenre: String,
    val averageDailyFormatted: String,
    val previousMonthListeningFormatted: String,
    val diffFormatted: String
)

enum class TimeRangeFilter {
    TODAY,
    DAYS_7,
    DAYS_30,
    ALL_TIME
}
