package com.example.barcodekeyboard.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences

/**
 * Redesigned Settings Screen with 3 core Bento sections:
 * 1. Tema (Dark, Light, System)
 * 2. Pengaturan Scanner (Beep Sound, Vibration, Auto-Enter, Prefix/Suffix)
 * 3. Info Aplikasi (One Keyboard, Version, ML Kit Engine, Permission)
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CAMERA = 1001
    }

    private lateinit var preferences: KeyboardPreferences

    // Views - Navigation
    private lateinit var btnSettingsBack: ImageButton

    // Views - Section 1: Tema
    private lateinit var cardThemeDark: View
    private lateinit var cardThemeLight: View
    private lateinit var cardThemeSystem: View
    private lateinit var ivCheckDark: ImageView
    private lateinit var ivCheckLight: ImageView
    private lateinit var ivCheckSystem: ImageView

    // Views - Section: Ukuran Keyboard
    private lateinit var rowKeyboardScale: View
    private lateinit var tvKeyboardScaleSummary: TextView
    private lateinit var btnQuickResetScale: Button

    // Views - Section: Kenyamanan Mengetik (Gboard Style)
    private lateinit var switchAutoCapitalization: SwitchCompat
    private lateinit var switchDoubleSpacePeriod: SwitchCompat
    private lateinit var switchSpacebarCursor: SwitchCompat
    private lateinit var switchSwipeDelete: SwitchCompat
    private lateinit var switchLongPressSymbols: SwitchCompat

    // Views - Section 2: Scanner
    private lateinit var switchScannerSound: SwitchCompat
    private lateinit var switchScannerVibrate: SwitchCompat
    private lateinit var switchScannerAutoEnter: SwitchCompat
    private lateinit var rowPrefixSuffix: View
    private lateinit var tvPrefixSuffixSummary: TextView
    private lateinit var rowBottomPadding: View
    private lateinit var tvBottomPaddingSummary: TextView

    // Views - Section 3: Info
    private lateinit var btnCheckCameraPermission: TextView
    private lateinit var btnOpenSetupGuide: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = KeyboardPreferences(this)
        applySavedNightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        applyStatusBarTheme()

        initViews()
        setupNavigation()
        setupThemeSection()
        setupKeyboardScaleSection()
        setupTypingComfortSection()
        setupScannerSection()
        setupInfoSection()
    }

    private fun applySavedNightMode() {
        val nightMode = when (preferences.themeMode) {
            KeyboardPreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            KeyboardPreferences.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun applyStatusBarTheme() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.bento_bg)
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val decor = window.decorView
            decor.systemUiVisibility = if (isNight) {
                decor.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                decor.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    private fun initViews() {
        btnSettingsBack = findViewById(R.id.btnSettingsBack)

        cardThemeDark = findViewById(R.id.cardThemeDark)
        cardThemeLight = findViewById(R.id.cardThemeLight)
        cardThemeSystem = findViewById(R.id.cardThemeSystem)
        ivCheckDark = findViewById(R.id.ivCheckDark)
        ivCheckLight = findViewById(R.id.ivCheckLight)
        ivCheckSystem = findViewById(R.id.ivCheckSystem)

        rowKeyboardScale = findViewById(R.id.rowKeyboardScale)
        tvKeyboardScaleSummary = findViewById(R.id.tvKeyboardScaleSummary)
        btnQuickResetScale = findViewById(R.id.btnQuickResetScale)

        switchAutoCapitalization = findViewById(R.id.switchAutoCapitalization)
        switchDoubleSpacePeriod = findViewById(R.id.switchDoubleSpacePeriod)
        switchSpacebarCursor = findViewById(R.id.switchSpacebarCursor)
        switchSwipeDelete = findViewById(R.id.switchSwipeDelete)
        switchLongPressSymbols = findViewById(R.id.switchLongPressSymbols)

        switchScannerSound = findViewById(R.id.switchScannerSound)
        switchScannerVibrate = findViewById(R.id.switchScannerVibrate)
        switchScannerAutoEnter = findViewById(R.id.switchScannerAutoEnter)
        rowPrefixSuffix = findViewById(R.id.rowPrefixSuffix)
        tvPrefixSuffixSummary = findViewById(R.id.tvPrefixSuffixSummary)
        rowBottomPadding = findViewById(R.id.rowBottomPadding)
        tvBottomPaddingSummary = findViewById(R.id.tvBottomPaddingSummary)

        btnCheckCameraPermission = findViewById(R.id.btnCheckCameraPermission)
        btnOpenSetupGuide = findViewById(R.id.btnOpenSetupGuide)
    }

    private fun setupNavigation() {
        btnSettingsBack.setOnClickListener {
            finish()
        }
    }

    private fun setupThemeSection() {
        updateThemeSelectionUI()

        cardThemeDark.setOnClickListener {
            setThemeMode(KeyboardPreferences.THEME_DARK)
        }

        cardThemeLight.setOnClickListener {
            setThemeMode(KeyboardPreferences.THEME_LIGHT)
        }

        cardThemeSystem.setOnClickListener {
            setThemeMode(KeyboardPreferences.THEME_SYSTEM)
        }
    }

    private fun setThemeMode(mode: String) {
        preferences.sharedPreferences.edit()
            .putString(KeyboardPreferences.KEY_THEME, mode)
            .apply()

        val nightMode = when (mode) {
            KeyboardPreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            KeyboardPreferences.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        updateThemeSelectionUI()
        applyStatusBarTheme()
        Toast.makeText(this, "Tema berhasil diubah", Toast.LENGTH_SHORT).show()
    }

    private fun updateThemeSelectionUI() {
        val currentTheme = preferences.themeMode
        val isDark = currentTheme == KeyboardPreferences.THEME_DARK
        val isLight = currentTheme == KeyboardPreferences.THEME_LIGHT
        val isSystem = currentTheme == KeyboardPreferences.THEME_SYSTEM

        cardThemeDark.setBackgroundResource(
            if (isDark) R.drawable.bg_bento_theme_card_selected else R.drawable.bg_bento_theme_card_unselected
        )
        cardThemeLight.setBackgroundResource(
            if (isLight) R.drawable.bg_bento_theme_card_selected else R.drawable.bg_bento_theme_card_unselected
        )
        cardThemeSystem.setBackgroundResource(
            if (isSystem) R.drawable.bg_bento_theme_card_selected else R.drawable.bg_bento_theme_card_unselected
        )

        ivCheckDark.visibility = if (isDark) View.VISIBLE else View.GONE
        ivCheckLight.visibility = if (isLight) View.VISIBLE else View.GONE
        ivCheckSystem.visibility = if (isSystem) View.VISIBLE else View.GONE
    }

    private fun setupKeyboardScaleSection() {
        updateKeyboardScaleSummary()

        rowKeyboardScale.setOnClickListener {
            showKeyboardScaleDialog()
        }

        btnQuickResetScale.setOnClickListener {
            resetKeyboardScaleWithFeedback()
        }
    }

    private fun updateKeyboardScaleSummary() {
        val scale = preferences.keyboardScalePercent
        val label = when {
            scale < 90 -> "Sangat Pendek"
            scale < 98 -> "Pendek"
            scale in 98..102 -> "Standar (Default)"
            scale in 103..115 -> "Tinggi"
            else -> "Ekstra Tinggi"
        }
        tvKeyboardScaleSummary.text = "$scale% ($label)"
        btnQuickResetScale.visibility = if (scale == KeyboardPreferences.DEFAULT_KEYBOARD_SCALE) View.GONE else View.VISIBLE
    }

    private fun resetKeyboardScaleWithFeedback() {
        preferences.resetKeyboardScale()
        updateKeyboardScaleSummary()
        Toast.makeText(this, "Ukuran keyboard dikembalikan ke Standar (100%)", Toast.LENGTH_SHORT).show()
    }

    private fun showKeyboardScaleDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_keyboard_size, null)
        val tvScalePercent = dialogView.findViewById<TextView>(R.id.tvDialogScalePercent)
        val tvScaleLabel = dialogView.findViewById<TextView>(R.id.tvDialogScaleLabel)
        val sbScale = dialogView.findViewById<SeekBar>(R.id.sbKeyboardScale)
        val btnShort = dialogView.findViewById<Button>(R.id.btnPresetShort)
        val btnNormal = dialogView.findViewById<Button>(R.id.btnPresetNormal)
        val btnTall = dialogView.findViewById<Button>(R.id.btnPresetTall)
        val btnReset = dialogView.findViewById<Button>(R.id.btnResetScale)

        val initialScale = preferences.keyboardScalePercent
        var currentScale = initialScale

        fun updateDialogDisplay(scale: Int) {
            currentScale = scale
            tvScalePercent.text = "$scale%"
            tvScaleLabel.text = when {
                scale < 90 -> "Sangat Pendek"
                scale < 98 -> "Pendek"
                scale in 98..102 -> "Ukuran Standar (Default)"
                scale in 103..115 -> "Tinggi"
                else -> "Ekstra Tinggi"
            }
        }

        // SeekBar max is 50, range 80 to 130
        sbScale.progress = (initialScale - 80).coerceIn(0, 50)
        updateDialogDisplay(initialScale)

        sbScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = progress + 80
                updateDialogDisplay(scale)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Quick Presets
        btnShort.setOnClickListener {
            sbScale.progress = 85 - 80
        }
        btnNormal.setOnClickListener {
            sbScale.progress = 100 - 80
        }
        btnTall.setOnClickListener {
            sbScale.progress = 115 - 80
        }

        // Reset Button inside Dialog
        btnReset.setOnClickListener {
            sbScale.progress = 100 - 80
            Toast.makeText(this, "Di-reset ke 100%", Toast.LENGTH_SHORT).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Tinggi & Ukuran Keyboard")
            .setView(dialogView)
            .setPositiveButton("Terapkan") { _, _ ->
                val chosen = sbScale.progress + 80
                preferences.setKeyboardScalePercent(chosen)
                updateKeyboardScaleSummary()
                Toast.makeText(this, "Ukuran keyboard diatur ke $chosen%", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Reset ke 100%") { _, _ ->
                resetKeyboardScaleWithFeedback()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupTypingComfortSection() {
        switchAutoCapitalization.isChecked = preferences.isAutoCapitalizationEnabled
        switchAutoCapitalization.setOnCheckedChangeListener { _, isChecked ->
            preferences.sharedPreferences.edit()
                .putBoolean(KeyboardPreferences.KEY_AUTO_CAPITALIZATION, isChecked)
                .apply()
        }

        switchDoubleSpacePeriod.isChecked = preferences.isDoubleSpacePeriodEnabled
        switchDoubleSpacePeriod.setOnCheckedChangeListener { _, isChecked ->
            preferences.sharedPreferences.edit()
                .putBoolean(KeyboardPreferences.KEY_DOUBLE_SPACE_PERIOD, isChecked)
                .apply()
        }

        switchSpacebarCursor.isChecked = preferences.isSpacebarCursorEnabled
        switchSpacebarCursor.setOnCheckedChangeListener { _, isChecked ->
            preferences.sharedPreferences.edit()
                .putBoolean(KeyboardPreferences.KEY_SPACEBAR_CURSOR, isChecked)
                .apply()
        }

        switchSwipeDelete.isChecked = preferences.isSwipeDeleteEnabled
        switchSwipeDelete.setOnCheckedChangeListener { _, isChecked ->
            preferences.sharedPreferences.edit()
                .putBoolean(KeyboardPreferences.KEY_SWIPE_DELETE, isChecked)
                .apply()
        }

        switchLongPressSymbols.isChecked = preferences.isLongPressSymbolsEnabled
        switchLongPressSymbols.setOnCheckedChangeListener { _, isChecked ->
            preferences.sharedPreferences.edit()
                .putBoolean(KeyboardPreferences.KEY_LONG_PRESS_SYMBOLS, isChecked)
                .apply()
        }
    }

    private fun setupScannerSection() {
        switchScannerSound.isChecked = preferences.isSoundEnabled
        switchScannerSound.setOnCheckedChangeListener { _, isChecked ->
            preferences.sharedPreferences.edit()
                .putBoolean(KeyboardPreferences.KEY_SOUND, isChecked)
                .apply()
        }

        switchScannerVibrate.isChecked = preferences.isVibrationEnabled
        switchScannerVibrate.setOnCheckedChangeListener { _, isChecked ->
            preferences.sharedPreferences.edit()
                .putBoolean(KeyboardPreferences.KEY_VIBRATE, isChecked)
                .apply()
        }

        switchScannerAutoEnter.isChecked = preferences.isAutoEnterEnabled
        switchScannerAutoEnter.setOnCheckedChangeListener { _, isChecked ->
            preferences.sharedPreferences.edit()
                .putBoolean(KeyboardPreferences.KEY_AUTO_ENTER, isChecked)
                .apply()
        }

        updatePrefixSuffixSummary()

        rowPrefixSuffix.setOnClickListener {
            showPrefixSuffixDialog()
        }

        updateBottomPaddingSummary()

        rowBottomPadding.setOnClickListener {
            showBottomPaddingDialog()
        }
    }

    private fun updateBottomPaddingSummary() {
        val summary = when (preferences.bottomPaddingSetting) {
            KeyboardPreferences.BOTTOM_PADDING_HIGH -> "Tinggi (+48dp - Paling Aman untuk 3 Tombol)"
            KeyboardPreferences.BOTTOM_PADDING_MEDIUM -> "Sedang (+28dp)"
            KeyboardPreferences.BOTTOM_PADDING_LOW -> "Rendah (+16dp)"
            KeyboardPreferences.BOTTOM_PADDING_NONE -> "Tanpa Jarak (0dp - Khusus Gestur Penuh)"
            else -> "Otomatis (Sesuai Navigasi Sistem)"
        }
        tvBottomPaddingSummary.text = summary
    }

    private fun showBottomPaddingDialog() {
        val options = arrayOf(
            "Otomatis (Rekomendasi - Deteksi Navigasi Sistem)",
            "Tinggi (+48dp - Paling Pas untuk Navigasi 3 Tombol)",
            "Sedang (+28dp)",
            "Rendah (+16dp)",
            "Tanpa Jarak (0dp - Khusus Navigasi Gestur)"
        )
        val values = arrayOf(
            KeyboardPreferences.BOTTOM_PADDING_AUTO,
            KeyboardPreferences.BOTTOM_PADDING_HIGH,
            KeyboardPreferences.BOTTOM_PADDING_MEDIUM,
            KeyboardPreferences.BOTTOM_PADDING_LOW,
            KeyboardPreferences.BOTTOM_PADDING_NONE
        )

        val currentVal = preferences.bottomPaddingSetting
        val selectedIndex = values.indexOf(currentVal).let { if (it >= 0) it else 0 }

        AlertDialog.Builder(this)
            .setTitle("Jarak Bawah Keyboard")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val chosen = values[which]
                preferences.setBottomPaddingSetting(chosen)
                updateBottomPaddingSummary()
                Toast.makeText(this, "Jarak bawah keyboard diperbarui", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updatePrefixSuffixSummary() {
        val prefix = preferences.prefix
        val suffix = preferences.suffix
        if (prefix.isEmpty() && suffix.isEmpty()) {
            tvPrefixSuffixSummary.text = "Tidak ada awalan / akhiran"
        } else {
            val p = if (prefix.isEmpty()) "-" else "\"$prefix\""
            val s = if (suffix.isEmpty()) "-" else "\"$suffix\""
            tvPrefixSuffixSummary.text = "Awalan: $p • Akhiran: $s"
        }
    }

    private fun showPrefixSuffixDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_prefix_suffix, null)
        val etPrefix = dialogView.findViewById<EditText>(R.id.etDialogPrefix)
        val etSuffix = dialogView.findViewById<EditText>(R.id.etDialogSuffix)

        etPrefix.setText(preferences.prefix)
        etSuffix.setText(preferences.suffix)

        AlertDialog.Builder(this)
            .setTitle("Atur Awalan & Akhiran")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val newPrefix = etPrefix.text.toString()
                val newSuffix = etSuffix.text.toString()
                preferences.sharedPreferences.edit()
                    .putString(KeyboardPreferences.KEY_PREFIX, newPrefix)
                    .putString(KeyboardPreferences.KEY_SUFFIX, newSuffix)
                    .apply()
                updatePrefixSuffixSummary()
                Toast.makeText(this, "Format berhasil disimpan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupInfoSection() {
        updateCameraPermissionButton()

        btnCheckCameraPermission.setOnClickListener {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                Toast.makeText(this, "✓ Izin kamera sudah aktif!", Toast.LENGTH_SHORT).show()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    PERMISSION_REQUEST_CAMERA
                )
            }
        }

        btnOpenSetupGuide.setOnClickListener {
            val intent = Intent(this, com.example.barcodekeyboard.ui.setup.SetupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateCameraPermissionButton() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        btnCheckCameraPermission.setBackgroundResource(R.drawable.bg_bento_pill_blue)
        btnCheckCameraPermission.setTextColor(android.graphics.Color.WHITE)
        if (hasPermission) {
            btnCheckCameraPermission.text = "✓ Izin Kamera Aktif"
        } else {
            btnCheckCameraPermission.text = "Izinkan Akses Kamera"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            updateCameraPermissionButton()
        }
    }
}
