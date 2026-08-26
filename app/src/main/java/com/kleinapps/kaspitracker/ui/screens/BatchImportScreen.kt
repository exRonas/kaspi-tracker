package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import android.content.Context
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.receipt.BatchImportResult
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.SectionHeader
import com.kleinapps.kaspitracker.receipt.ReceiptImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BatchImportUiState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val result: BatchImportResult? = null
) {
    val progress: Float
        get() = if (total == 0) 0f else done.toFloat() / total
}

class BatchImportViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(BatchImportUiState())
    val state: StateFlow<BatchImportUiState> = _state

    fun import(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.running) return
        viewModelScope.launch {
            _state.value = BatchImportUiState(running = true, total = uris.size)
            val summary = ReceiptImporter.importAll(context, uris) { done, total, running ->
                _state.value = BatchImportUiState(
                    running = true, done = done, total = total, result = running
                )
            }
            _state.value = BatchImportUiState(
                running = false, done = uris.size, total = uris.size, result = summary
            )
        }
    }
}

/**
 * Imports a whole folder of Kaspi receipts in one go, instead of sharing them
 * into the app one at a time.
 */
@Composable
fun BatchImportScreen(viewModel: BatchImportViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.import(context, uris) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Как это работает")
                Text(
                    text = "Выбери сразу все PDF-чеки из Загрузок — приложение прочитает их одно " +
                        "за другим. Уже импортированные и совпавшие с уведомлением Kaspi " +
                        "пропускаются, так что лишних записей не появится.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Чеки без текстового слоя распознаются на устройстве — это несколько " +
                        "секунд на файл. Экран лучше не закрывать.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            // Kaspi hands out application/pdf, some file managers re-tag the same
            // bytes as octet-stream — accept everything and fail on parsing.
            onClick = { picker.launch(arrayOf("application/pdf", "*/*")) },
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.running) "Импортирую…" else "Выбрать чеки")
        }

        if (state.running) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${state.done} из ${state.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.result?.takeIf { !state.running }?.let { result -> ImportSummary(result) }
    }
}

@Composable
private fun ImportSummary(result: BatchImportResult) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(title = "Обработано ${result.total} чеков")
            SummaryRow("Добавлено", result.added)
            SummaryRow("Дополнило уведомление", result.merged)
            SummaryRow("Уже были", result.duplicates)
            SummaryRow("Не прочитались", result.failed)

            result.failures.forEach { reason ->
                Text(
                    text = "• $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, count: Int) {
    Text(
        text = "$label: $count",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
