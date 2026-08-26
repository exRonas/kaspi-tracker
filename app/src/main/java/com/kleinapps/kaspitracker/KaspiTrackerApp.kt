package com.kleinapps.kaspitracker

import android.app.Application
import android.util.Log
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.service.AutoBackup
import com.kleinapps.kaspitracker.ui.theme.ThemeController
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KaspiTrackerApp : Application() {
    // Eagerly warm up the repository/database singletons.
    val repository: TransactionRepository by lazy { TransactionRepository.get(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Required once before any PDDocument.load() call (receipt import).
        PDFBoxResourceLoader.init(applicationContext)
        ThemeController.init(this)
        // Weekly safety-net copy of everything into Downloads.
        AutoBackup.schedule(this)
        appScope.launch {
            runCatching {
                // Turn categories the user already assigned by hand into rules,
                // so known merchants aren't asked about all over again...
                repository.seedRulesFromHistory()
                // ...read merchants out of pushes the old parser skipped...
                repository.backfillNotificationMerchants()
                // ...then fill in rows that predate auto-categorization or that
                // those rules now cover.
                repository.backfillCategories()
            }.onFailure { Log.e("KaspiTrackerApp", "Category backfill failed", it) }
        }
    }
}
