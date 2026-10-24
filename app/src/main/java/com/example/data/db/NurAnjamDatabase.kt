package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedAudioTrackEntity::class,
        TrackPlayRecordEntity::class,
        ListeningEventEntity::class,
        DailyListeningAggregateEntity::class,
        com.example.data.ambient.DownloadedAmbientEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class NurAnjamDatabase : RoomDatabase() {

    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun trackPlayDao(): TrackPlayDao
    abstract fun intelligenceDao(): IntelligenceDao
    abstract fun downloadedAmbientDao(): com.example.data.ambient.DownloadedAmbientDao

    companion object {
        @Volatile
        private var INSTANCE: NurAnjamDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_play_records ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tracks` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `reciter` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `dataUri` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `folderName` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `bitrateKbps` INTEGER NOT NULL,
                        `sampleRateHz` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `dateAdded` INTEGER NOT NULL,
                        `playCount` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN customSortTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tracks SET customSortTimestamp = dateAdded WHERE customSortTimestamp = 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN lastPlayedTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): NurAnjamDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NurAnjamDatabase::class.java,
                    "nur_anjam_audio.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build().also {
                        INSTANCE = it
                    }
            }
        }
    }
}
