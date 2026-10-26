package com.ydh.salvio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedRepo::class,
        CachedCommit::class,
        CachedPr::class,
        CachedBranch::class,
        CachedPrReview::class,
        CachedCommitActivity::class,
        CachedIssue::class,
        CachedRelease::class,
        CachedCheckRuns::class,
        CachedPrFiles::class,
        CachedTrafficViews::class,
        CachedTrafficClones::class,
        CachedContributorStats::class,
        CachedContributors::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "salvio_cache.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
