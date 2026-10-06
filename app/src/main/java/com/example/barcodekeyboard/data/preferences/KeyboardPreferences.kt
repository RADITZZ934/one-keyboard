package com.example.barcodekeyboard.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.preference.PreferenceManager

/**
 * Manages user settings and preferences for Barcode Keyboard.
 */
class KeyboardPreferences(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        const val KEY_THEME = "pref_theme"
        const val THEME_SYSTEM = "system"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"

        const val KEY_AUTO_ENTER = "pref_auto_enter"
        const val KEY_VIBRATE = "pref_vibrate"
        const val KEY_SOUND = "pref_sound"
        const val KEY_PREFIX = "pref_prefix"
        const val KEY_SUFFIX = "pref_suffix"
    }

    val themeMode: String
        get() = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM

    /**
     * Resolves whether Dark Mode is currently active based on user preference
     * or Android system dark mode configuration.
     */
    fun isDarkTheme(context: Context): Boolean {
        return when (themeMode) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> {
                val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    val isAutoEnterEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENTER, false)

    val isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)

    val isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)

    val sharedPreferences: SharedPreferences
        get() = prefs

    val prefixText: String
        get() = prefs.getString(KEY_PREFIX, "") ?: ""

    val suffixText: String
        get() = prefs.getString(KEY_SUFFIX, "") ?: ""

    val prefix: String
        get() = prefixText

    val suffix: String
        get() = suffixText

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
