package com.kleinapps.kaspitracker.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * App-wide theme preference, persisted in SharedPreferences and exposed as a
 * StateFlow so composition reacts instantly to the in-app toggle.
 */
object ThemeController {

    private const val PREFS = "settings"
    private const val KEY = "theme_mode"

    private lateinit var prefs: SharedPreferences

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> get() = _mode

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    /** Light → Dark → System → Light … */
    fun cycle() {
        setMode(
            when (_mode.value) {
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
            }
        )
    }
}
