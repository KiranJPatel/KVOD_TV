package com.iptv.firetv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SourceEntity::class, ChannelEntity::class, VodItemEntity::class, SeriesEntity::class, EpisodeEntity::class,
        EpgChannelEntity::class, ProgramEntity::class, EpgMappingEntity::class, ProfileEntity::class,
        FavoriteEntity::class, WatchProgressEntity::class, PlaybackHistoryEntity::class, ImportJobEntity::class,
        RecentSearchEntity::class, GroupOverrideEntity::class, PlaylistEntity::class, PlaylistItemEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun channels(): ChannelDao
    abstract fun vod(): VodDao
    abstract fun epg(): EpgDao
    abstract fun user(): UserDao
    abstract fun imports(): ImportDao
    abstract fun playlists(): PlaylistDao

    companion object {
        const val NAME = "iptv.db"
        /** v1 -> v2: user playlists. Existing data is untouched; the SQL mirrors what Room generates for the two new entities. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlists_profileId` ON `playlists` (`profileId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_items` (`playlistId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `itemId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `kind`, `itemId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId` ON `playlist_items` (`playlistId`)")
            }
        }

        /**
         * v2 -> v3: duplicate-channel identity. Existing rows get dupKey = '' and are filled in once at app start
         * (ChannelKeyBackfill); until then they simply are not treated as duplicates of anything.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `dupKey` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `qualityRank` INTEGER NOT NULL DEFAULT 3")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_dupKey` ON `channels` (`dupKey`)")
            }
        }

        /** Add a Migration here (and a MigrationTestHelper test) with every further version bump. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
