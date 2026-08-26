package com.kleinapps.kaspitracker.service

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kleinapps.kaspitracker.data.Backup
import com.kleinapps.kaspitracker.data.TransactionRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Weekly unattended backup into the phone's Downloads folder.
 *
 * A backup you have to remember to make is a backup you don't have — the manual
 * button on the backup screen stays for "right now, before I do something
 * risky", this is the safety net for everything else. Only [KEEP_COPIES] files
 * are kept; older ones are deleted so Downloads doesn't fill up with JSON.
 */
object AutoBackup {

    private const val TAG = "AutoBackup"
    private const val WORK_NAME = "weekly_backup"
    private const val PREFS = "auto_backup"
    private const val KEY_LAST_RUN = "last_run"
    private const val KEY_LAST_FILE = "last_file"
    private const val KEEP_COPIES = 4

    /** Registers the weekly job. Existing schedules are left alone. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** When the last automatic backup ran, and what it was called. */
    fun lastRun(context: Context): Pair<Long, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong(KEY_LAST_RUN, 0L)
        val name = prefs.getString(KEY_LAST_FILE, null)
        return if (at > 0 && name != null) at to name else null
    }

    /**
     * Writes the backup where the user can actually reach it: MediaStore's
     * Downloads on Android 10+, the app's own external Documents folder below
     * that (where the legacy path is still writable without a permission).
     */
    internal fun write(context: Context, json: String): String {
        val name = "${Backup.FILE_PREFIX}_${timestamp()}.json"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Downloads недоступны")
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: error("не удалось открыть файл на запись")
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: error("нет внешнего хранилища")
            File(dir, name).writeText(json)
        }

        rotate(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_RUN, System.currentTimeMillis())
            .putString(KEY_LAST_FILE, name)
            .apply()
        return name
    }

    /** Keeps only the newest [KEEP_COPIES] automatic backups. */
    private fun rotate(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("${Backup.FILE_PREFIX}%"),
                "${MediaStore.Downloads.DISPLAY_NAME} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                var seen = 0
                while (cursor.moveToNext()) {
                    seen++
                    if (seen <= KEEP_COPIES) continue
                    val id = cursor.getLong(idColumn)
                    // Only files this app inserted are deletable without a prompt;
                    // anything else simply throws and is skipped.
                    runCatching {
                        resolver.delete(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            "${MediaStore.Downloads._ID} = ?",
                            arrayOf(id.toString())
                        )
                    }
                }
            }
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
            dir.listFiles { file -> file.name.startsWith(Backup.FILE_PREFIX) }
                ?.sortedByDescending { it.name }
                ?.drop(KEEP_COPIES)
                ?.forEach { it.delete() }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(System.currentTimeMillis())
}

/** Does the weekly export. Retries once if the database is busy. */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val json = TransactionRepository.get(applicationContext).exportBackup()
        AutoBackup.write(applicationContext, json)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = {
            Log.e("BackupWorker", "Weekly backup failed", it)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    )
}
