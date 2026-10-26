package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.receipt.BatchImportResult
import com.kleinapps.kaspitracker.receipt.ReceiptImporter
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.PrimaryButton
import com.kleinapps.kaspitracker.ui.components.RowDivider
import com.kleinapps.kaspitracker.ui.components.SectionHeader
import com.kleinapps.kaspitracker.ui.components.ShareBar
import com.kleinapps.kaspitracker.ui.theme.Kaspi
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppCard {
            SectionHeader(title = "Как это работает")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Выбери сразу все PDF-чеки из Загрузок — приложение прочитает их одно за другим. " +
                    "Уже импортированные и совпавшие с уведомлением Kaspi пропускаются.",
                style = Kaspi.type.bodyM,
                color = Kaspi.colors.ink
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = Kaspi.colors.ink3, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Распознавание идёт на устройстве: несколько секунд на файл, экран лучше не закрывать.",
                    style = Kaspi.type.bodyS,
                    color = Kaspi.colors.ink2
                )
            }
        }

        PrimaryButton(
            text = if (state.running) "Импортирую… ${state.done} из ${state.total}" else "Выбрать чеки",
            icon = Icons.Outlined.FolderOpen,
            // Kaspi hands out application/pdf, some file managers re-tag the same
            // bytes as octet-stream — accept everything and fail on parsing.
            onClick = { picker.launch(arrayOf("application/pdf", "*/*")) },
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.running) {
            ShareBar(fraction = state.progress, color = Kaspi.colors.accent)
        }

        state.result?.takeIf { !state.running }?.let { result -> ImportSummary(result) }

        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(text = "Один чек из Kaspi", style = Kaspi.type.titleM, color = Kaspi.colors.ink)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "В Kaspi нажми «Поделиться» на чеке и выбери Kaspi Tracker — результат появится поверх экрана.",
                style = Kaspi.type.bodyS,
                color = Kaspi.colors.ink2
            )
        }
    }
}

@Composable
private fun ImportSummary(result: BatchImportResult) {
    AppCard(contentPadding = 0.dp) {
        SectionHeader(
            title = "Обработано: ${result.total}",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        SummaryRow("Добавлено", result.added, Kaspi.colors.pos)
        RowDivider()
        SummaryRow("Дополнило уведомление", result.merged, Kaspi.colors.ink)
        RowDivider()
        SummaryRow("Уже были", result.duplicates, Kaspi.colors.ink2)
        RowDivider()
        SummaryRow("Не прочитались", result.failed, if (result.failed > 0) Kaspi.colors.neg else Kaspi.colors.ink2)
        if (result.failures.isNotEmpty()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                result.failures.forEach { reason ->
                    Text(text = "• $reason", style = Kaspi.type.bodyS, color = Kaspi.colors.ink2)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, count: Int, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = label, style = Kaspi.type.bodyM, color = Kaspi.colors.ink, modifier = Modifier.weight(1f))
        Text(text = count.toString(), style = Kaspi.type.money, color = color)
    }
}
