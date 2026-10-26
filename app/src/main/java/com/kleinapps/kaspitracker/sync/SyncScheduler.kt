package com.kleinapps.kaspitracker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * When syncing happens: every 15 minutes while online (the shortest period
 * Android allows), and a few seconds after this phone records or changes
 * something. Nothing runs unless the phone is paired or waiting for its code
 * to be entered.
 */
object SyncScheduler {

    private const val PERIODIC = "sync_periodic"
    private const val SOON = "sync_now"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Debounced: a burst of changes (a pushed receipt batch) ends in one sync. */
    fun requestSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!CloudConfig.isConfigured) return Result.success()
        CoupleStore.init(applicationContext)
        val manager = CoupleManager.get(applicationContext)
        val state = CoupleStore.state.value
        when {
            state.pendingCode != null -> runCatching { manager.checkPending() }
            state.paired -> manager.syncNow()
        }
        return Result.success()
    }
}
