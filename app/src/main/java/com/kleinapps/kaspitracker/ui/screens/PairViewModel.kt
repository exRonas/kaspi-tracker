package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kleinapps.kaspitracker.data.SharedHints
import com.kleinapps.kaspitracker.sync.CloudConfig
import com.kleinapps.kaspitracker.sync.CloudError
import com.kleinapps.kaspitracker.sync.CloudException
import com.kleinapps.kaspitracker.sync.CoupleManager
import com.kleinapps.kaspitracker.sync.CoupleState
import com.kleinapps.kaspitracker.sync.CoupleStore
import com.kleinapps.kaspitracker.ui.theme.PersonTone
import com.kleinapps.kaspitracker.ui.theme.ThemeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the pair screen is busy with, and what went wrong last. */
data class PairUiStatus(
    val busy: Boolean = false,
    val error: CloudError? = null
)

class PairViewModel(app: Application) : AndroidViewModel(app) {

    private val manager by lazy { CoupleManager.get(app) }

    val couple: StateFlow<CoupleState> = CoupleStore.state
    val configured: Boolean = CloudConfig.isConfigured

    private val _status = MutableStateFlow(PairUiStatus())
    val status: StateFlow<PairUiStatus> = _status

    private val _hints = MutableStateFlow(SharedHints.enabled(app))
    val hints: StateFlow<Boolean> = _hints

    fun saveProfile(name: String, tone: PersonTone) {
        ThemeController.setTone(tone)
        run { manager.saveProfile(name, PersonTone.choices.indexOf(tone)) }
    }

    fun showCode() = run { manager.createCode() }

    fun redeem(code: String) = run { manager.redeem(code) }

    fun syncNow() = run { manager.syncNow() }

    fun unpair() = run { manager.unpair() }

    /** Called every few seconds while the code is on screen. */
    fun checkPending() {
        viewModelScope.launch { runCatching { manager.checkPending() } }
    }

    fun setHints(enabled: Boolean) {
        SharedHints.setEnabled(getApplication(), enabled)
        _hints.value = enabled
    }

    fun clearError() {
        _status.value = _status.value.copy(error = null)
    }

    private fun run(action: suspend () -> Unit) {
        if (_status.value.busy) return
        viewModelScope.launch {
            _status.value = PairUiStatus(busy = true)
            val error = try {
                action()
                null
            } catch (e: CloudException) {
                e.error
            } catch (e: Exception) {
                // An answer the client could not read: show it, never crash.
                CloudError.UNKNOWN
            }
            _status.value = PairUiStatus(busy = false, error = error ?: couple.value.lastError)
        }
    }
}
