package com.iptv.firetv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iptv.firetv.data.SourceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters, private val sources: SourceRepository
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result { sources.refreshDue(); return Result.success() } // failures are recorded per source
}

@Singleton
class RefreshScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    fun schedule() {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("source-refresh", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
