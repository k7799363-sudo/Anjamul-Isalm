package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CachedAudioTrackEntity
import com.example.data.db.CachedTrackDao
import com.example.data.db.NurAnjamDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentAndScanFunctionalityTest {

    private lateinit var db: NurAnjamDatabase
    private lateinit var dao: CachedTrackDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NurAnjamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cachedTrackDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testLastPlayedTimestampUpdateAndRecentQuery() = runBlocking {
        // Insert sample tracks with lastPlayedTimestamp = 0
        val track1 = CachedAudioTrackEntity(
            id = "track_1",
            title = "Surah Al-Fatihah",
            reciter = "Mishary Rashid Alafasy",
            durationMs = 60000L,
            dataUri = "content://audio/1",
            album = "Holy Quran",
            folderName = "Quran",
            sizeBytes = 1000000L,
            bitrateKbps = 320,
            sampleRateHz = 44100,
            isFavorite = false,
            dateAdded = 1000L,
            customSortTimestamp = 1000L,
            playCount = 0,
            lastPlayedTimestamp = 0L
        )

        val track2 = CachedAudioTrackEntity(
            id = "track_2",
            title = "Surah Al-Baqarah",
            reciter = "Abdul Basit",
            durationMs = 1200000L,
            dataUri = "content://audio/2",
            album = "Holy Quran",
            folderName = "Quran",
            sizeBytes = 2000000L,
            bitrateKbps = 320,
            sampleRateHz = 44100,
            isFavorite = true,
            dateAdded = 2000L,
            customSortTimestamp = 2000L,
            playCount = 0,
            lastPlayedTimestamp = 0L
        )

        val track3 = CachedAudioTrackEntity(
            id = "track_3",
            title = "Surah Yasin",
            reciter = "Saad Al-Ghamdi",
            durationMs = 900000L,
            dataUri = "content://audio/3",
            album = "Holy Quran",
            folderName = "Quran",
            sizeBytes = 1500000L,
            bitrateKbps = 320,
            sampleRateHz = 44100,
            isFavorite = false,
            dateAdded = 3000L,
            customSortTimestamp = 3000L,
            playCount = 0,
            lastPlayedTimestamp = 0L
        )

        dao.insertAll(listOf(track1, track2, track3))

        // Before playing any track, recent query must return empty list
        val initialRecent = dao.observeRecentTracks().first()
        assertTrue("Initially recent tracks must be empty", initialRecent.isEmpty())

        // Simulate ExoPlayer starting playback on track 1 at t = 5000L
        dao.updateLastPlayedTimestamp("track_1", 5000L)

        val afterTrack1 = dao.observeRecentTracks().first()
        assertEquals(1, afterTrack1.size)
        assertEquals("track_1", afterTrack1[0].id)
        assertEquals(5000L, afterTrack1[0].lastPlayedTimestamp)

        // Simulate ExoPlayer starting playback on track 3 at t = 8000L
        dao.updateLastPlayedTimestamp("track_3", 8000L)

        // Query recent list: track 3 should be first (8000L) followed by track 1 (5000L)
        val afterTrack3 = dao.observeRecentTracks().first()
        assertEquals(2, afterTrack3.size)
        assertEquals("track_3", afterTrack3[0].id)
        assertEquals(8000L, afterTrack3[0].lastPlayedTimestamp)
        assertEquals("track_1", afterTrack3[1].id)
        assertEquals(5000L, afterTrack3[1].lastPlayedTimestamp)

        // Now play track 1 again at t = 10000L
        dao.updateLastPlayedTimestamp("track_1", 10000L)

        val afterReplayTrack1 = dao.observeRecentTracks().first()
        assertEquals(2, afterReplayTrack1.size)
        assertEquals("track_1", afterReplayTrack1[0].id)
        assertEquals(10000L, afterReplayTrack1[0].lastPlayedTimestamp)
        assertEquals("track_3", afterReplayTrack1[1].id)
        assertEquals(8000L, afterReplayTrack1[1].lastPlayedTimestamp)
    }
}
