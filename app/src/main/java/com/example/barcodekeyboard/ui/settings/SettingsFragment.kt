package com.example.barcodekeyboard.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences

/**
 * Fragment that displays and manages barcode keyboard preferences,
 * including dynamic Dark Mode / Light Mode switching.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val themePref = findPreference<ListPreference>(KeyboardPreferences.KEY_THEME)
        themePref?.setOnPreferenceChangeListener { _, newValue ->
            val themeMode = newValue as? String ?: KeyboardPreferences.THEME_SYSTEM
            applyNightMode(themeMode)
            true
        }
    }

    private fun applyNightMode(themeMode: String) {
        val nightMode = when (themeMode) {
            KeyboardPreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            KeyboardPreferences.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
