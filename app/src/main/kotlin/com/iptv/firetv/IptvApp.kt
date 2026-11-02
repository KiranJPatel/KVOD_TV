package com.iptv.firetv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.iptv.firetv.work.RefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltAndroidApp
class IptvApp : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: RefreshScheduler
    @Inject lateinit var channelKeys: com.iptv.firetv.data.ChannelKeyBackfill
    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Bounded artwork caches keep memory and storage predictable on Fire TV Stick class hardware. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(64L * 1024 * 1024).build() }
        .crossfade(180).respectCacheHeaders(false).build()

    override fun onCreate() {
        super.onCreate()
        com.iptv.firetv.core.common.DiagnosticsLog.attach(java.io.File(filesDir, "diagnostics.log"))
        scheduler.schedule()
        appScope.launch { runCatching { channelKeys.run() } }
    }
}
