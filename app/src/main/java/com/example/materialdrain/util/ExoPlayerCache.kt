package com.example.materialdrain.util

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi // SimpleCache and related APIs are still under UnstableApi
object ExoPlayerCache {

    private var simpleCache: SimpleCache? = null
    private const val MAX_CACHE_SIZE_BYTES: Long = 100 * 1024 * 1024 // 100MB

    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDirectory = File(context.cacheDir, "media_cache")
            if (!cacheDirectory.exists()) {
                cacheDirectory.mkdirs()
            }
            // Using StandaloneDatabaseProvider for the cache index
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(
                cacheDirectory,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES),
                databaseProvider
            )
        }
        return simpleCache!!
    }
}