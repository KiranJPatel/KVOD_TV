package com.iptv.firetv.data

import com.iptv.firetv.core.common.ChannelNaming
import com.iptv.firetv.core.common.DiagnosticsLog
import com.iptv.firetv.core.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * After the v3 migration existing channels have no duplicate key. This fills them in, in small batches (each its own short
 * transaction, so the UI never waits on it). Safe to run at every start: when nothing is missing it is a single cheap query.
 */
@Singleton
class ChannelKeyBackfill @Inject constructor(private val db: AppDatabase) {
    suspend fun run(): Int = withContext(Dispatchers.IO) {
        var total = 0
        while (true) {
            val rows = db.channels().needingKeys(500)
            if (rows.isEmpty()) break
            db.channels().upsertAll(rows.map { it.copy(dupKey = ChannelNaming.dupKey(it.name, it.id), qualityRank = ChannelNaming.qualityRank(it.name)) })
            total += rows.size
        }
        if (total > 0) DiagnosticsLog.add("db", "Duplicate keys computed for $total existing channels")
        total
    }
}
