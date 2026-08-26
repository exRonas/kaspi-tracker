package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Backup
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.SectionHeader
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.service.AutoBackup
import com.kleinapps.kaspitracker.util.formatDayHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    /** Writes the backup into cacheDir and hands it to the system share sheet. */
    fun export(context: Context) {
        viewModelScope.launch {
            _status.value = "Готовлю бэкап…"
            runCatching {
                val json = repo.exportBackup()
                val file = withContext(Dispatchers.IO) { writeBackupFile(context, json) }
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "Сохранить бэкап"
                    )
                )
                file.name
            }.onSuccess { _status.value = "Бэкап готов: $it" }
                .onFailure { _status.value = "Не удалось сделать бэкап: ${it.message}" }
        }
    }

    fun import(context: Context, uri: Uri) {
        viewModelScope.launch {
            _status.value = "Читаю файл…"
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: error("файл пустой")
                repo.importBackup(json)
            }.onSuccess { report ->
                _status.value = "Добавлено операций: ${report.added}, " +
                    "уже было: ${report.skipped}. Правил: ${report.rules}, бюджетов: ${report.budgets}."
            }.onFailure { _status.value = "Не удалось восстановить: ${it.message}" }
        }
    }

    private fun writeBackupFile(context: Context, json: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(System.currentTimeMillis())
        return File(dir, "${Backup.FILE_PREFIX}_$stamp.json").apply { writeText(json) }
    }
}

/** Text under the explainer: when the unattended weekly copy last ran. */
private fun autoBackupStatus(context: Context): String {
    val last = AutoBackup.lastRun(context)
        ?: return "Раз в неделю копия сохраняется в Загрузки сама. Первая — в течение часа."
    return "Автобэкап раз в неделю в Загрузки. Последний: " +
        "${formatDayHeader(last.first)}, ${last.second}. Хранятся 4 последних."
}

/**
 * Full copy of the data as a JSON file: share it anywhere (Telegram to self,
 * cloud, cable) and restore on another phone without a computer.
 */
@Composable
fun BackupScreen(viewModel: BackupViewModel = viewModel()) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        // Backups are shared as application/json, but some apps hand the same
        // file back as octet-stream — accept anything and fail on parsing.
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.import(context, it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Что попадает в бэкап")
                Text(
                    text = "Все операции, месячные бюджеты и выученные правила категорий — " +
                        "одним JSON-файлом.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = autoBackupStatus(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = { viewModel.export(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить бэкап")
        }

        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Восстановить из файла")
        }

        Text(
            text = "Восстановление добавляет недостающие операции и не трогает те, что уже есть — " +
                "один и тот же файл можно применять сколько угодно раз.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
