package com.kleinapps.kaspitracker.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * App-wide look preferences — light/dark mode and the user's own colour —
 * persisted in SharedPreferences and exposed as StateFlows so composition
 * reacts instantly to a change.
 */
object ThemeController {

    private const val PREFS = "settings"
    private const val KEY = "theme_mode"
    private const val KEY_TONE = "person_tone"

    private lateinit var prefs: SharedPreferences

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> get() = _mode

    private val _tone = MutableStateFlow(PersonTone.CLAY)
    /** The user's colour: the accent of their own view. */
    val tone: StateFlow<PersonTone> get() = _tone

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        _tone.value = PersonTone.of(prefs.getInt(KEY_TONE, 0))
    }

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    fun setTone(tone: PersonTone) {
        _tone.value = tone
        prefs.edit().putInt(KEY_TONE, PersonTone.choices.indexOf(tone).coerceAtLeast(0)).apply()
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
